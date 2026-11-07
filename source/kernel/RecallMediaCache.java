package com.zafiro.wxentry.kernel;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Only module-owned copies expire. Restores require the native adapter's fresh approved paths. */
public final class RecallMediaCache {
    public interface Clock {long now();}
    public static final long WINDOW_MS=180000L,MAX_FILE=256L*1024*1024,MAX_BYTES=512L*1024*1024;
    private final File root,pending,kept,staging;
    private final Clock clock;
    private final Map<String,Entry> entries=new LinkedHashMap<String,Entry>();
    private long bytes;
    public static final class Entry {
        public final String key,account,talker;public final long local,server,received,expires;public final int type;
        File directory;boolean recalled;
        Entry(String k,String a,String t,long l,long s,int y,long at,File d,boolean r){key=k;account=a;talker=t;local=l;server=s;type=y;received=at;expires=at+WINDOW_MS;directory=d;recalled=r;}
        public boolean isRecalled(){return recalled;}
    }
    public static final class Copy {
        public final File file;public final String original,hash;public final long length;
        Copy(File f,String p,String h,long n){file=f;original=p;hash=h;length=n;}
    }
    public RecallMediaCache(File directory,Clock time)throws IOException{
        root=directory.getCanonicalFile();clock=time;pending=new File(root,"pending");kept=new File(root,"kept");staging=new File(root,"staging");
        mkdir(pending);mkdir(kept);mkdir(staging);load(pending,false);load(kept,true);
        for(Entry e:entries.values())for(Copy c:copies(e))bytes+=c.length;
        File[] drafts=staging.listFiles();if(drafts!=null)for(File f:drafts)removeOwn(f);sweep();
    }
    public synchronized Entry register(String account,String talker,long local,long server,int type,long received)throws IOException{
        if(account==null||!account.matches("[a-fA-F0-9]{32}")||talker==null||talker.length()==0||talker.length()>256||local<=0||server<=0||received>Long.MAX_VALUE-WINDOW_MS||!MediaMessagePolicy.live(received,clock.now())||!MediaMessagePolicy.media(type,6,0))return null;
        String key=hash((account+"\n"+talker+"\n"+local+"\n"+server).getBytes("UTF-8"));Entry old=entries.get(key);if(old!=null)return old;
        sweep();int waiting=0;for(Entry e:entries.values())if(!e.recalled)waiting++;if(waiting>=128||entries.size()>=4096)return null;
        File draft=new File(staging,"entry-"+UUID.randomUUID());mkdir(draft);
        Properties p=new Properties();p.setProperty("account",account);p.setProperty("talker",talker);p.setProperty("local",Long.toString(local));p.setProperty("server",Long.toString(server));p.setProperty("type",Integer.toString(type));p.setProperty("received",Long.toString(received));
        File target=new File(pending,key);
        try{write(new File(draft,"entry.properties"),p);if(!draft.renameTo(target))throw new IOException("Cannot publish media entry");}finally{if(draft.exists())removeOwn(draft);}
        Entry e=new Entry(key,account,talker,local,server,type,received,target,false);entries.put(key,e);return e;
    }
    private boolean alive(Entry e){return e.recalled||MediaMessagePolicy.live(e.received,clock.now());}
    private boolean official(String account,File file)throws IOException{
        String path=file.getCanonicalPath().replace('\\','/');if(path.startsWith(root.getPath().replace('\\','/')+"/"))return false;
        if(path.indexOf("/MicroMsg/")<0&&path.indexOf("/com.tencent.mm/")<0)return false;
        if(path.contains("/WechatXposed/")||path.indexOf('\n')>=0||path.indexOf('\r')>=0)return false;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("/MicroMsg/([a-fA-F0-9]{32})/").matcher(path);
        return !m.find()||account.equals(m.group(1));
    }
    public boolean snapshot(Entry e,File source,long nativeLength,boolean nativeComplete)throws IOException{
        if(!nativeComplete||source==null||!source.isFile()||!official(e.account,source))return false;
        source=source.getCanonicalFile();long length=source.length(),modified=source.lastModified();String path=source.getPath();
        if(length<=0||length>MAX_FILE||nativeLength<=0||length!=nativeLength)return false;
        synchronized(this){if(entries.get(e.key)!=e||!alive(e))return false;for(Copy c:copies(e))if(c.original.equals(path)&&c.length==length&&c.hash.equals(fileHash(source)))return true;}
        File draft=new File(staging,UUID.randomUUID()+".part");
        try{
            String digest=copy(source,draft,e);
            if(digest==null||source.length()!=length||source.lastModified()!=modified||draft.length()!=length||!digest.equals(fileHash(source)))return false;
            synchronized(this){
                if(entries.get(e.key)!=e||!alive(e)||bytes+length>MAX_BYTES)return false;
                String base=hash(path.getBytes("UTF-8"));File content=new File(e.directory,base+".media"),meta=new File(e.directory,base+".properties");
                List<Copy> old=copies(e);File target=new File(e.directory,base+"-"+UUID.randomUUID()+".media");
                if(!draft.renameTo(target))throw new IOException("Cannot publish media bytes");
                Properties p=new Properties();p.setProperty("file",target.getName());p.setProperty("path",path);p.setProperty("sha256",digest);p.setProperty("length",Long.toString(length));
                File proof=new File(staging,UUID.randomUUID()+".properties");
                try{write(proof,p);File previous=new File(staging,UUID.randomUUID()+".old");if(meta.exists()&&!meta.renameTo(previous))throw new IOException("Cannot replace media metadata");
                    if(!proof.renameTo(meta)){if(previous.exists())previous.renameTo(meta);throw new IOException("Cannot commit media metadata");}previous.delete();
                }catch(IOException failure){target.delete();throw failure;}finally{proof.delete();}
                bytes+=length;for(Copy c:old)if(c.original.equals(path)&&!c.file.equals(target)&&c.file.delete())bytes-=c.length;
                return true;
            }
        }finally{draft.delete();}
    }
    public synchronized Entry recall(Entry e)throws IOException{
        if(e==null||entries.get(e.key)!=e)return null;if(e.recalled)return e;if(!alive(e))return null;
        File target=new File(kept,e.key);if(!e.directory.renameTo(target))throw new IOException("Cannot retain recalled media");e.directory=target;e.recalled=true;return e;
    }
    public synchronized Entry find(String account,String talker,long local,long server){for(Entry e:entries.values())if(e.account.equals(account)&&e.talker.equals(talker)&&e.local==local&&e.server==server)return e;return null;}
    public synchronized List<Entry> entries(){return new ArrayList<Entry>(entries.values());}
    public synchronized boolean hasCapacity(long length){return length>0&&length<=MAX_FILE&&bytes<=MAX_BYTES-length;}
    public synchronized List<Copy> copies(Entry e)throws IOException{
        List<Copy> out=new ArrayList<Copy>();if(entries.get(e.key)!=e)return out;File[] files=e.directory.listFiles();if(files==null)return out;
        for(File m:files)if(m.getName().matches("[a-f0-9]{64}\\.properties"))try{
            Properties p=read(m,16384);String name=p.getProperty("file"),path=p.getProperty("path"),digest=p.getProperty("sha256");long n=Long.parseLong(p.getProperty("length"));
            if(name==null||!name.matches("[a-f0-9]{64}-[a-f0-9-]{36}\\.media")||path==null||!new File(path).getCanonicalPath().equals(path)||!official(e.account,new File(path))||n<=0||n>MAX_FILE||digest==null||!digest.matches("[a-f0-9]{64}"))continue;
            File f=new File(e.directory,name);if(!f.getCanonicalFile().getParentFile().equals(e.directory.getCanonicalFile())||!f.isFile()||f.length()!=n)continue;
            out.add(new Copy(f,path,digest,n));
        }catch(Exception invalid){/* An incomplete proof never becomes a source file. */}
        return out;
    }
    public int restore(Entry e,String currentAccount,Collection<File> nativeApprovedPaths)throws IOException{
        if(!e.account.equals(currentAccount)||nativeApprovedPaths==null)return 0;Set<String> approved=new HashSet<String>();for(File f:nativeApprovedPaths)if(f!=null&&official(currentAccount,f))approved.add(f.getCanonicalPath());
        int count=0;for(Copy c:copies(e)){
            synchronized(this){if(entries.get(e.key)!=e||!e.recalled)return count;}
            File dest=new File(c.original);if(!approved.contains(c.original)||dest.exists()||!fileHash(c.file).equals(c.hash))continue;
            File parent=dest.getParentFile();mkdir(parent);File draft=new File(parent,".wxentry-media-"+UUID.randomUUID());
            try{copy(c.file,draft,null);synchronized(this){if(entries.get(e.key)==e&&e.recalled&&!dest.exists()&&approved.contains(dest.getCanonicalPath())&&draft.renameTo(dest))count++;}}finally{draft.delete();}
        }return count;
    }
    public synchronized int sweep()throws IOException{return discard(false);}
    public synchronized int discardPending()throws IOException{return discard(true);}
    private int discard(boolean all)throws IOException{
        int removed=0;Iterator<Entry> it=entries.values().iterator();while(it.hasNext()){
            Entry e=it.next();if(e.recalled||(!all&&alive(e)))continue;long n=0;for(Copy c:copies(e))n+=c.length;removeOwn(e.directory);it.remove();bytes=Math.max(0,bytes-n);removed++;
        }return removed;
    }
    private void load(File parent,boolean recalled)throws IOException{
        File[] dirs=parent.listFiles();if(dirs==null)return;for(File d:dirs){if(!d.getName().matches("[a-f0-9]{64}")||!d.isDirectory()||!d.getCanonicalFile().getParentFile().equals(parent.getCanonicalFile()))continue;
            try{Properties p=read(new File(d,"entry.properties"),8192);String a=p.getProperty("account"),t=p.getProperty("talker");long l=Long.parseLong(p.getProperty("local")),s=Long.parseLong(p.getProperty("server")),at=Long.parseLong(p.getProperty("received"));int type=Integer.parseInt(p.getProperty("type"));
                if(a==null||!a.matches("[a-fA-F0-9]{32}")||t==null||t.length()==0||t.length()>256||l<=0||s<=0||at<0||at>Long.MAX_VALUE-WINDOW_MS||!MediaMessagePolicy.media(type,6,0)||!d.getName().equals(hash((a+"\n"+t+"\n"+l+"\n"+s).getBytes("UTF-8"))))throw new IOException("Invalid entry");
                Entry old=entries.get(d.getName());if(old==null||recalled)entries.put(d.getName(),new Entry(d.getName(),a,t,l,s,type,at,d,recalled));
            }catch(Exception invalid){removeOwn(d);}
        }
    }
    private String copy(File from,File to,Entry pendingEntry)throws IOException{
        try{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(from);FileOutputStream out=new FileOutputStream(to)){
            byte[] b=new byte[65536];int n;long size=0;while((n=in.read(b))!=-1){if(pendingEntry!=null){synchronized(this){if(entries.get(pendingEntry.key)!=pendingEntry||!alive(pendingEntry))return null;}}size+=n;if(size>MAX_FILE)throw new IOException("Media copy limit");out.write(b,0,n);md.update(b,0,n);}out.getFD().sync();return hex(md.digest());}
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IOException(impossible);}
    }
    private static String fileHash(File f)throws IOException{try{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}return hex(md.digest());}catch(java.security.NoSuchAlgorithmException impossible){throw new IOException(impossible);}}
    private static String hash(byte[] data){try{return hex(MessageDigest.getInstance("SHA-256").digest(data));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private static String hex(byte[] digest){StringBuilder b=new StringBuilder();for(byte x:digest)b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();}
    private static void mkdir(File f)throws IOException{if(!f.isDirectory()&&!f.mkdirs())throw new IOException("Cannot create media directory");}
    private static Properties read(File f,int max)throws IOException{if(f.length()>max)throw new IOException("Oversized metadata");Properties p=new Properties();try(InputStream in=new FileInputStream(f)){p.load(in);}return p;}
    private static void write(File f,Properties p)throws IOException{try(FileOutputStream out=new FileOutputStream(f)){p.store(out,"WXEntry local media cache");out.getFD().sync();}}
    private void removeOwn(File f)throws IOException{File target=f.getCanonicalFile();if(!target.getPath().startsWith(root.getPath()+File.separator))throw new IOException("Cleanup outside media cache");if(f.isDirectory()){File[] cs=f.listFiles();if(cs!=null)for(File c:cs)removeOwn(c);}if(f.exists()&&!f.delete())throw new IOException("Cannot clean media cache");}
}
