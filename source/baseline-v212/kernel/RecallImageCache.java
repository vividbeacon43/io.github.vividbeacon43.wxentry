package com.zafiro.wxentry.kernel;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Local-only, per-message image copies. This class never deletes WeChat files. */
public final class RecallImageCache {
    public interface Clock { long now(); }
    public static final long WINDOW_MS = 180000L;
    private static final long MAX_FILE = 50L * 1024 * 1024;
    private static final long MAX_BYTES = 256L * 1024 * 1024;
    private static final int MAX_PENDING = 128;
    private final File root, pending, kept, staging;
    private final Clock clock;
    private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
    private long bytes;

    public static final class Entry {
        public final String key, account, talker, imageName;
        public final long localId, serverId, receivedAt, expiresAt;
        private File directory;
        private volatile boolean recalled;
        private long storedBytes;
        private int imageCount;
        private Entry(String k, String a, String t, long l, long s, String n, long at,
                      File d, boolean r) {
            key=k; account=a; talker=t; localId=l; serverId=s; imageName=n;
            receivedAt=at; expiresAt=at+WINDOW_MS; directory=d; recalled=r;
        }
        public boolean isRecalled() { return recalled; }
    }
    public static final class Image {
        public final File copy;
        public final String originalPath;
        public final boolean thumbnail;
        private Image(File f, String p, boolean thumb) {
            copy=f; originalPath=p;
            thumbnail=thumb;
        }
    }

    public RecallImageCache(File directory, Clock time) throws IOException {
        root=directory.getCanonicalFile(); clock=time;
        pending=new File(root,"pending"); kept=new File(root,"kept");
        staging=new File(root,"staging");
        mkdir(pending); mkdir(kept); mkdir(staging);
        load(pending,false); load(kept,true);
        recomputeBytes();
        // Incomplete copies have never been published as a cache entry.
        File[] loose=staging.listFiles();
        if(loose!=null) for(File f:loose) removeOwn(f);
        sweep();
    }

    public synchronized Entry register(String account, String talker, long localId,
                                       long serverId, String imageReference) throws IOException {
        return registerAt(account,talker,localId,serverId,imageReference,clock.now());
    }
    public synchronized Entry registerAt(String account, String talker, long localId,
                                       long serverId, String imageReference, long arrival) throws IOException {
        if(account==null || !account.matches("[a-fA-F0-9]{32}") || talker==null
                || talker.length()==0 || talker.length()>256 || localId<=0 || serverId<=0
                || arrival<0 || arrival>clock.now() || clock.now()-arrival>=WINDOW_MS)
            return null;
        String name=imageName(imageReference);
        if(name==null) return null;
        String key=hash(account+"\n"+talker+"\n"+serverId);
        Entry old=entries.get(key);
        if(old!=null) return old; // Duplicate inserts must not extend the three-minute window.
        sweep();
        int count=0;
        for(Entry e:entries.values()) if(!e.recalled) count++;
        if(count>=MAX_PENDING || entries.size()>=4096) return null;
        File dir=new File(pending,key);
        File draft=new File(staging,"register-"+UUID.randomUUID());
        mkdir(draft);
        long now=arrival;
        Properties p=new Properties();
        p.setProperty("account",account); p.setProperty("talker",talker);
        p.setProperty("localId",Long.toString(localId));
        p.setProperty("serverId",Long.toString(serverId));
        p.setProperty("imageName",name); p.setProperty("receivedAt",Long.toString(now));
        try {
            FileOutputStream out=new FileOutputStream(new File(draft,"message.properties"));
            try { p.store(out,"WXEntry local image cache"); out.getFD().sync(); }
            finally { out.close(); }
            if(!draft.renameTo(dir)) throw new IOException("Cannot publish image cache entry");
        } finally { if(draft.exists()) removeOwn(draft); }
        Entry e=new Entry(key,account,talker,localId,serverId,name,now,dir,false);
        entries.put(key,e);
        return e;
    }

    /** Call only after the official image writer has closed or renamed its completed file. */
    public void observe(File source) throws IOException {
        String path=source.getCanonicalPath();
        String account=accountOf(path), name=imageName(source.getName());
        if(account==null || name==null || !source.isFile() || !imageHeader(source)) return;
        long length=source.length(), modified=source.lastModified();
        if(length<24 || length>MAX_FILE) return;
        List<Entry> targets=new ArrayList<Entry>();
        synchronized(this) {
            for(Entry e:entries.values())
                if(e.account.equals(account) && e.imageName.equals(name) && alive(e)) targets.add(e);
        }
        for(Entry e:targets) {
            synchronized(this) {
                boolean copied=false;
                for(Image i:images(e))
                    if(path.equals(i.originalPath) && i.copy.length()>=length) { copied=true; break; }
                if(copied) continue;
            }
            File draft=new File(staging,UUID.randomUUID()+".image");
            try {
                copy(source,draft);
                if(source.length()!=length || source.lastModified()!=modified || draft.length()!=length)
                    continue; // A concurrent writer still owns the source.
                synchronized(this) {
                    if(entries.get(e.key)!=e || !alive(e) || bytes+length>MAX_BYTES) continue;
                    String base=hash(path)+"-"+UUID.randomUUID();
                    File content=new File(e.directory,base+".image");
                    File pointer=new File(e.directory,base+".path");
                    if(!draft.renameTo(content)) throw new IOException("Cannot publish cached image");
                    try { writeUtf8(pointer,path); }
                    catch(IOException failure) { content.delete(); throw failure; }
                    bytes+=length;
                    e.storedBytes+=length; e.imageCount++;
                    // Keep the last complete variant of each exact official path.
                    for(Image i:images(e)) if(path.equals(i.originalPath) && !content.equals(i.copy)) {
                        long n=i.copy.length();
                        if(i.copy.delete()) { bytes-=n; e.storedBytes-=n; e.imageCount--; }
                        new File(i.copy.getParentFile(),stripImage(i.copy.getName())+".path").delete();
                        new File(i.copy.getParentFile(),stripImage(i.copy.getName())+".native").delete();
                    }
                }
            } finally { draft.delete(); }
        }
    }

    /** Only the native adapter may call this after ImgInfo reports a completed download.
     * Native media can use opaque formats and names unrelated to the thumbnail token. */
    public void observeCompleted(Entry e, File source, boolean thumbnail) throws IOException {
        String path=source.getCanonicalPath();
        if(!e.account.equals(accountOf(path)) || !source.isFile()) return;
        long length=source.length(), modified=source.lastModified();
        if(length<24 || length>MAX_FILE) return;
        synchronized(this) {
            if(entries.get(e.key)!=e || !alive(e)) return;
            for(Image i:images(e)) if(path.equals(i.originalPath) && i.copy.length()==length
                    && fileHash(i.copy).equals(fileHash(source))) return;
        }
        File draft=new File(staging,UUID.randomUUID()+".image");
        try {
            copy(source,draft);
            if(source.length()!=length || source.lastModified()!=modified || draft.length()!=length) return;
            String digest=fileHash(draft);
            synchronized(this) {
                if(entries.get(e.key)!=e || !alive(e) || bytes+length>MAX_BYTES) return;
                List<Image> old=images(e);
                String base=hash(path)+"-"+UUID.randomUUID();
                File content=new File(e.directory,base+".image");
                File pointer=new File(e.directory,base+".path");
                File proof=new File(e.directory,base+".native");
                // Publish .path last: incomplete metadata is never treated as a valid copy.
                if(!draft.renameTo(content)) throw new IOException("Cannot publish native image");
                try {
                    writeUtf8(proof,(thumbnail?"thumb":"full")+"\n"+length+"\n"+digest);
                    writeUtf8(pointer,path);
                } catch(IOException failure) { content.delete(); proof.delete(); pointer.delete(); throw failure; }
                bytes+=length; e.storedBytes+=length; e.imageCount++;
                for(Image i:old) if(path.equals(i.originalPath)) {
                    long n=i.copy.length();
                    if(i.copy.delete()) {
                        bytes-=n; e.storedBytes-=n; e.imageCount--;
                        new File(e.directory,stripImage(i.copy.getName())+".path").delete();
                        new File(e.directory,stripImage(i.copy.getName())+".native").delete();
                    }
                }
            }
        } finally { draft.delete(); }
    }

    /** Rename is the commit point shared by recall and expiry. No image data is copied here. */
    public synchronized Entry recall(String account, String talker, long localId, long serverId)
            throws IOException {
        for(Entry e:entries.values()) {
            if(!e.account.equals(account) || !e.talker.equals(talker) || e.localId!=localId
                    || e.serverId!=serverId) continue;
            if(e.recalled) return e;
            if(!alive(e)) return null;
            File target=new File(kept,e.key);
            if(!e.directory.renameTo(target)) throw new IOException("Cannot retain recalled image");
            e.directory=target; e.recalled=true;
            return e;
        }
        return null;
    }

    public synchronized List<Entry> entries() { return new ArrayList<Entry>(entries.values()); }
    public synchronized List<Image> images(Entry e) throws IOException {
        List<Image> result=new ArrayList<Image>();
        if(entries.get(e.key)!=e) return result;
        File[] files=e.directory.listFiles();
        if(files==null) return result;
        for(File p:files) if(p.getName().endsWith(".path") && p.isFile()) {
            String base=p.getName().substring(0,p.getName().length()-5);
            File content=new File(e.directory,base+".image");
            if(!base.matches("[a-f0-9]{64}-[a-f0-9-]{36}") || !content.isFile()) continue;
            String path=readUtf8(p,4096);
            if(!e.account.equals(accountOf(path)) || !new File(path).getCanonicalPath().equals(path)
                    || content.length()<24 || content.length()>MAX_FILE) continue;
            File proof=new File(e.directory,base+".native");
            if(proof.isFile()) {
                String[] v=readUtf8(proof,256).split("\n");
                if(v.length==3 && (v[0].equals("full") || v[0].equals("thumb"))
                        && v[1].equals(Long.toString(content.length())) && v[2].equals(fileHash(content)))
                    result.add(new Image(content,path,v[0].equals("thumb")));
            } else if(e.imageName.equals(imageName(new File(path).getName())) && imageHeader(content))
                result.add(new Image(content,path,new File(path).getName().startsWith("th_")));
        }
        return result;
    }

    public synchronized boolean protects(String path) throws IOException {
        String canonical=new File(path).getCanonicalPath();
        for(Entry e:entries.values()) if(e.recalled)
            for(Image i:images(e)) if(canonical.equals(i.originalPath)) return true;
        return false;
    }

    /** Restore missing official paths so the original WeChat bubble/viewer can read them. */
    public int restore(Entry e) throws IOException {
        List<Image> copies;
        synchronized(this) {
            if(entries.get(e.key)!=e || !e.recalled) return 0;
            copies=images(e);
        }
        int restored=0;
        for(Image i:copies) {
            File destination=new File(i.originalPath).getCanonicalFile();
            if(!e.account.equals(accountOf(destination.getPath())) || destination.exists()) continue;
            File parent=destination.getParentFile(); mkdir(parent);
            File draft=new File(parent,".wxentry-restore-"+UUID.randomUUID());
            try {
                copy(i.copy,draft);
                synchronized(this) {
                    if(entries.get(e.key)!=e || !e.recalled || destination.exists()) continue;
                    if(draft.renameTo(destination)) restored++;
                }
            } finally { draft.delete(); }
        }
        return restored;
    }

    public synchronized int sweep() throws IOException {
        int removed=0;
        Iterator<Entry> it=entries.values().iterator();
        while(it.hasNext()) {
            Entry e=it.next();
            if((!e.recalled && !alive(e)) || (e.recalled && clock.now()>e.expiresAt+30000L
                    && e.imageCount==0)) {
                removeOwn(e.directory); it.remove(); bytes-=e.storedBytes; removed++;
            }
        }
        return removed;
    }
    public synchronized void clear() throws IOException {
        for(Entry e:entries.values()) removeOwn(e.directory);
        entries.clear(); bytes=0;
    }
    /** Revoking the premium grant only removes temporary module copies, never retained media. */
    public synchronized int discardPending() throws IOException {
        int removed=0;
        Iterator<Entry> it=entries.values().iterator();
        while(it.hasNext()) {
            Entry e=it.next();
            if(e.recalled) continue;
            removeOwn(e.directory); it.remove(); bytes-=e.storedBytes; removed++;
        }
        return removed;
    }
    private boolean alive(Entry e) {
        long now=clock.now();
        return e.recalled || (now>=e.receivedAt && now<e.expiresAt);
    }
    private void load(File parent,boolean recalled) throws IOException {
        File[] dirs=parent.listFiles();
        if(dirs==null) return;
        for(File d:dirs) {
            if(!d.isDirectory() || !d.getName().matches("[a-f0-9]{64}")) continue;
            if(!d.getCanonicalFile().getParentFile().equals(parent.getCanonicalFile())) continue;
            try {
                Properties p=new Properties();
                File metadata=new File(d,"message.properties");
                if(metadata.length()>8192) throw new IOException("Oversized entry metadata");
                FileInputStream in=new FileInputStream(metadata);
                try { p.load(in); } finally { in.close(); }
                String a=p.getProperty("account"), t=p.getProperty("talker"), n=p.getProperty("imageName");
                long l=Long.parseLong(p.getProperty("localId")), s=Long.parseLong(p.getProperty("serverId"));
                long at=Long.parseLong(p.getProperty("receivedAt"));
                if(a==null || !a.matches("[a-fA-F0-9]{32}") || t==null || t.length()>256 || l<=0 || s<=0
                        || at<0 || at>Long.MAX_VALUE-WINDOW_MS || n==null || !n.equals(imageName(n))
                        || !d.getName().equals(hash(a+"\n"+t+"\n"+s))) throw new IOException("Invalid entry");
                Entry e=new Entry(d.getName(),a,t,l,s,n,at,d,recalled);
                Entry old=entries.get(e.key);
                if(old==null || recalled) entries.put(e.key,e);
            } catch(Exception invalid) { removeOwn(d); }
        }
    }
    private void recomputeBytes() throws IOException {
        bytes=0;
        for(Entry e:entries.values()) {
            e.storedBytes=0; e.imageCount=0;
            for(Image i:images(e)) { e.storedBytes+=i.copy.length(); e.imageCount++; }
            bytes+=e.storedBytes;
        }
    }
    private void removeOwn(File f) throws IOException {
        File canonical=f.getCanonicalFile();
        if(!canonical.getPath().startsWith(root.getPath()+File.separator))
            throw new IOException("Cache cleanup escaped its root");
        if(f.isDirectory()) {
            File[] children=f.listFiles();
            if(children!=null) for(File child:children) removeOwn(child);
        }
        if(f.exists() && !f.delete()) throw new IOException("Cannot clean cache entry");
    }
    public static String accountOf(String path) {
        if(path==null) return null;
        String normalized=path.replace('\\','/');
        int pos=normalized.indexOf("/MicroMsg/");
        if(pos<0) return null;
        String tail=normalized.substring(pos+10);
        int slash=tail.indexOf('/');
        if(slash!=32) return null;
        String account=tail.substring(0,slash), rest=tail.substring(slash+1);
        if(!account.matches("[a-fA-F0-9]{32}")) return null;
        return rest.startsWith("image2/") || rest.startsWith("image/") ? account : null;
    }
    public static String imageName(String reference) {
        if(reference==null) return null;
        String n=reference.replace('\\','/');
        n=n.substring(n.lastIndexOf('/')+1);
        if(n.startsWith("th_")) n=n.substring(3);
        if(n.endsWith("_tmp.jpg") || n.contains("writable")) return null;
        String lower=n.toLowerCase(Locale.ROOT);
        for(String ext:new String[]{".jpeg",".jpg",".png",".webp",".gif",".heic"})
            if(lower.endsWith(ext)) { n=n.substring(0,n.length()-ext.length()); break; }
        return n.matches("[A-Za-z0-9_-]{6,160}") ? n : null;
    }
    private static boolean imageHeader(File f) throws IOException {
        FileInputStream in=new FileInputStream(f);
        byte[] b=new byte[24]; int n;
        try { n=in.read(b); } finally { in.close(); }
        if(n<24) return false;
        RandomAccessFile tail=new RandomAccessFile(f,"r");
        try {
            long length=tail.length();
            if((b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255) {
                tail.seek(length-2);
                return tail.readUnsignedByte()==255 && tail.readUnsignedByte()==217;
            }
            if((b[0]&255)==137 && b[1]=='P' && b[2]=='N' && b[3]=='G') {
                tail.seek(length-12);
                return tail.readInt()==0 && tail.readInt()==0x49454e44 && tail.readInt()==0xae426082;
            }
            if(b[0]=='G' && b[1]=='I' && b[2]=='F') {
                tail.seek(length-1); return tail.readUnsignedByte()==0x3b;
            }
            if(b[0]=='R' && b[1]=='I' && b[2]=='F' && b[3]=='F'
                    && b[8]=='W' && b[9]=='E' && b[10]=='B' && b[11]=='P') {
                long declared=(b[4]&255L)|((b[5]&255L)<<8)|((b[6]&255L)<<16)|((b[7]&255L)<<24);
                return declared+8==length;
            }
            // Unknown/WeChat-private image formats need a validated native adapter.
            return false;
        } finally { tail.close(); }
    }
    private static void copy(File source,File destination) throws IOException {
        FileInputStream in=new FileInputStream(source);
        try {
            FileOutputStream out=new FileOutputStream(destination);
            try {
                byte[] buffer=new byte[32768]; int n;
                while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
                out.getFD().sync();
            } finally { out.close(); }
        } finally { in.close(); }
    }
    private static String stripImage(String n) { return n.substring(0,n.length()-6); }
    private static void mkdir(File f) throws IOException {
        if(!f.isDirectory() && !f.mkdirs()) throw new IOException("Cannot create cache directory");
    }
    private static String hash(String s) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
            StringBuilder out=new StringBuilder();
            for(byte b:digest) out.append(String.format(Locale.ROOT,"%02x",b&255));
            return out.toString();
        } catch(Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static String fileHash(File file) throws IOException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            FileInputStream in=new FileInputStream(file);
            try { byte[] b=new byte[32768]; int n; while((n=in.read(b))!=-1) digest.update(b,0,n); }
            finally { in.close(); }
            StringBuilder out=new StringBuilder();
            for(byte b:digest.digest()) out.append(String.format(Locale.ROOT,"%02x",b&255));
            return out.toString();
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
    }
    private static void writeUtf8(File f,String s) throws IOException {
        FileOutputStream out=new FileOutputStream(f);
        try { out.write(s.getBytes("UTF-8")); out.getFD().sync(); } finally { out.close(); }
    }
    private static String readUtf8(File f,int max) throws IOException {
        if(f.length()>max) throw new IOException("Oversized cache metadata");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        FileInputStream in=new FileInputStream(f);
        try {
            byte[] b=new byte[1024]; int n;
            while((n=in.read(b))!=-1) {
                if(out.size()+n>max) throw new IOException("Oversized cache metadata");
                out.write(b,0,n);
            }
        }
        finally { in.close(); }
        return new String(out.toByteArray(),"UTF-8");
    }
}
