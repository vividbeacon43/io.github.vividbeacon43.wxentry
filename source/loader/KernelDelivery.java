package com.zafiro.wxentry;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Authenticated four-channel delivery. Edition, version and bytes share one RSA signature. */
public final class KernelDelivery {
    public static final class Manifest {
        public final String edition,version,sha,signature,grantSignature;
        public Manifest(String e,String v,String h,String s,String grant) {
            EditionPolicy.manifestData(e,v,h);
            if(s==null || s.length()<40 || s.length()>1024) throw new IllegalArgumentException("Invalid signature");
            if(grant==null || grant.length()<40 || grant.length()>1024) throw new IllegalArgumentException("Invalid activation grant");
            edition=e; version=v; sha=h; signature=s; grantSignature=grant;
        }
        String signedData() { return EditionPolicy.manifestData(edition,version,sha); }
        String grantData(String owner) { return "kernel-grant-v1|"+owner+"|"+edition+"|"+version+"|"+sha; }
    }
    public static final class Bundle {
        public final Manifest manifest;
        public final byte[] dex;
        Bundle(Manifest m,byte[] d) { manifest=m; dex=d; }
    }
    public static final class Result {
        public final Manifest manifest;
        public final boolean changed,editionChanged;
        Result(Manifest m,boolean c,boolean e) { manifest=m; changed=c; editionChanged=e; }
    }
    public static class AuthorizationDenied extends IOException {
        public AuthorizationDenied() { super("Kernel authorization denied"); }
    }
    public interface Transport {
        Manifest manifest(String token) throws Exception;
        byte[] download(String token,Manifest manifest) throws Exception;
        byte[] decrypt(byte[] encrypted) throws Exception;
        boolean verify(String data,String signature) throws Exception;
        boolean isCurrent(String token);
        boolean blocked(String sha);
    }
    private final File directory;
    private final Transport transport;
    private volatile String decisionScope,decisionEdition;
    public KernelDelivery(File dir,Transport t) { directory=dir; transport=t; }
    public String edition(String token) {
        try { return scope(token).equals(decisionScope) && EditionPolicy.valid(decisionEdition)?decisionEdition:"free"; }
        catch(Exception invalid) { return "free"; }
    }
    public synchronized Bundle cached(String token) {
        try {
            Bundle b=readCache(token);
            String owner=scope(token);
            if(owner.equals(decisionScope) && !b.manifest.edition.equals(decisionEdition)) return null;
            if(!transport.isCurrent(token)) return null;
            decisionScope=owner; decisionEdition=b.manifest.edition;
            return b;
        } catch(Exception invalid) { return null; }
    }
    public synchronized Result refresh(String token) throws Exception {
        String owner=scope(token);
        Manifest m=probe(token);
        if(transport.blocked(m.sha)) throw new IOException("Known bad kernel");
        Bundle old=null;
        try { old=readCache(token); } catch(Exception missing) {}
        if(old!=null && old.manifest.edition.equals(m.edition)) {
            if(old.manifest.sha.equals(m.sha) && old.manifest.version.equals(m.version))
                return new Result(m,false,false);
            // A version comparison never crosses edition boundaries.
            if(Integer.parseInt(old.manifest.version)>Integer.parseInt(m.version))
                return new Result(old.manifest,false,false);
        }
        byte[] encrypted;
        try { encrypted=transport.download(token,m); }
        catch(AuthorizationDenied denied) { decisionScope=owner; decisionEdition="denied"; throw denied; }
        if(encrypted==null || encrypted.length<1000 || encrypted.length>16*1024*1024)
            throw new IOException("Invalid encrypted kernel size");
        byte[] plain=transport.decrypt(encrypted);
        if(plain==null || plain.length<1000 || plain[0]!='d' || plain[1]!='e' || plain[2]!='x'
                || plain[3]!='\n' || !m.sha.equals(hash(plain))) throw new IOException("Kernel hash mismatch");
        if(!transport.isCurrent(token)) throw new IOException("Activation changed");
        publish(encrypted,m,owner);
        return new Result(m,true,old!=null && !old.manifest.edition.equals(m.edition));
    }
    public synchronized Manifest probe(String token) throws Exception {
        String owner=scope(token);
        if(!transport.isCurrent(token)) throw new IOException("Activation changed");
        Manifest m;
        try { m=transport.manifest(token); }
        catch(AuthorizationDenied denied) { decisionScope=owner; decisionEdition="denied"; throw denied; }
        if(!transport.verify(m.signedData(),m.signature) || !transport.verify(m.grantData(owner),m.grantSignature))
            throw new IOException("Manifest or activation signature mismatch");
        if(!transport.isCurrent(token)) throw new IOException("Activation changed");
        decisionScope=owner; decisionEdition=m.edition;
        return m;
    }
    private Bundle readCache(String token) throws Exception {
        String owner=scope(token);
        Properties p=new Properties();
        p.load(new ByteArrayInputStream(read(new File(directory,"kernel.channel"),4096)));
        if(!"2".equals(p.getProperty("format")) || !owner.equals(p.getProperty("scope")))
            throw new IOException("Kernel cache belongs to another activation");
        Manifest m=new Manifest(p.getProperty("edition"),p.getProperty("version"),p.getProperty("sha256"),p.getProperty("signature"),p.getProperty("grant_signature"));
        if(!transport.verify(m.signedData(),m.signature) || !transport.verify(m.grantData(owner),m.grantSignature)
                || transport.blocked(m.sha)) throw new IOException("Invalid cache signature");
        byte[] plain=transport.decrypt(read(new File(directory,"kernel.enc"),16*1024*1024));
        if(!m.sha.equals(hash(plain))) throw new IOException("Invalid cache hash");
        return new Bundle(m,plain);
    }
    private void publish(byte[] encrypted,Manifest m,String owner) throws IOException {
        if(!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create cache directory");
        Properties p=new Properties();
        p.setProperty("format","2"); p.setProperty("scope",owner);
        p.setProperty("edition",m.edition); p.setProperty("version",m.version);
        p.setProperty("sha256",m.sha); p.setProperty("signature",m.signature);
        p.setProperty("grant_signature",m.grantSignature);
        ByteArrayOutputStream meta=new ByteArrayOutputStream(); p.store(meta,"WXEntry signed kernel channel");
        atomic(new File(directory,"kernel.enc"),encrypted);
        atomic(new File(directory,"kernel.sha"),(m.sha+"\n").getBytes("UTF-8"));
        atomic(new File(directory,"kernel.ver"),(m.version+"\n").getBytes("UTF-8"));
        atomic(new File(directory,"kernel.channel"),meta.toByteArray()); // Commit metadata last.
    }
    public synchronized void clear() {
        for(String name:new String[]{"kernel.channel","kernel.enc","kernel.sha"}) new File(directory,name).delete();
    }
    static String scope(String token) throws IOException {
        if(token==null || token.length()<20 || token.length()>16384) throw new IOException("No activation token");
        return hash(token.getBytes("UTF-8"));
    }
    static String hash(byte[] data) throws IOException {
        try {
            StringBuilder h=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(data)) h.append(String.format(Locale.ROOT,"%02x",b&255));
            return h.toString();
        } catch(Exception e) { throw new IOException(e); }
    }
    private static byte[] read(File f,int max) throws IOException {
        if(f.length()>max) throw new IOException("Oversized cache");
        ByteArrayOutputStream out=new ByteArrayOutputStream(); FileInputStream in=new FileInputStream(f);
        try { byte[] b=new byte[32768]; int n; while((n=in.read(b))!=-1) {
            if(out.size()+n>max) throw new IOException("Oversized cache"); out.write(b,0,n);
        }} finally { in.close(); }
        return out.toByteArray();
    }
    static void atomic(File target,byte[] bytes) throws IOException {
        File draft=File.createTempFile("channel-",".tmp",target.getParentFile());
        try {
            FileOutputStream out=new FileOutputStream(draft);
            try { out.write(bytes); out.getFD().sync(); } finally { out.close(); }
            if(!draft.renameTo(target)) {
                File prior=new File(target.getParentFile(),"channel-old-"+UUID.randomUUID()+".tmp");
                if(!target.isFile() || !target.renameTo(prior)) throw new IOException("Cannot replace cache");
                if(!draft.renameTo(target)) { prior.renameTo(target); throw new IOException("Cannot publish cache"); }
                prior.delete();
            }
        } finally { draft.delete(); }
    }
}
