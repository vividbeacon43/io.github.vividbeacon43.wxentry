package com.zafiro.wxentry.kernel;
import java.io.*;import java.nio.file.*;import java.util.*;

public final class RecallMediaCacheTest {
    static int checks;static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static class Clock implements RecallMediaCache.Clock {long now=1700000000000L;public long now(){return now;}}
    static final String A="0123456789abcdef0123456789abcdef",B="fedcba9876543210fedcba9876543210";
    static File file(Path root,String path,byte[] data)throws Exception{File f=root.resolve(path).toFile();f.getParentFile().mkdirs();Files.write(f.toPath(),data);return f;}
    static RecallMediaCache.Entry entry(RecallMediaCache c,Clock t,long id,int type)throws Exception{return c.register(A,"room@chatroom",id,90000+id,type,t.now);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory(Paths.get(args[0]),"media-cache-");Clock t=new Clock();File dir=root.resolve("module-cache").toFile();RecallMediaCache c=new RecallMediaCache(dir,t);
        byte[] bytes=new byte[8192];new Random(4).nextBytes(bytes);File source=file(root,"MicroMsg/"+A+"/video/one.mp4",bytes);
        RecallMediaCache.Entry e=entry(c,t,1,43);ok(e!=null,"received video registered");
        ok(!c.snapshot(e,source,source.length(),false),"partially downloaded native video is not cached");
        ok(!c.snapshot(e,source,source.length()+1,true),"native total mismatch rejected");
        ok(c.snapshot(e,source,source.length(),true)&&c.copies(e).size()==1,"completed native video cached");
        byte[] original=Files.readAllBytes(source.toPath());long expiry=e.expires;t.now+=1000;
        ok(c.register(A,"room@chatroom",1,90001,43,t.now)==e&&e.expires==expiry,"duplicate receive cannot extend deadline");
        ok(c.snapshot(e,source,source.length(),true)&&c.copies(e).size()==1,"duplicate completion does not duplicate backup");
        t.now=expiry-1;ok(c.sweep()==0&&c.copies(e).size()==1,"pending copy lives until the last millisecond");
        t.now=expiry;ok(c.recall(e)==null,"recall at the exact expiry cannot revive copy");
        ok(c.sweep()==1&&c.entries().isEmpty(),"unrecalled copy deleted at three minutes");
        ok(Arrays.equals(original,Files.readAllBytes(source.toPath())),"expiry never touches official file");
        e=entry(c,t,2,62);ok(c.snapshot(e,source,source.length(),true),"short video is cached");t.now+=179999;ok(c.recall(e)==e&&e.isRecalled(),"recall commits before deadline");
        t.now+=600000;ok(c.sweep()==0&&c.copies(e).size()==1,"recalled copy retained after three minutes");
        Files.delete(source.toPath());ok(c.restore(e,B,Collections.singletonList(source))==0&&!source.exists(),"other account cannot restore recalled bytes");
        File wrong=new File(source.getParentFile(),"wrong.mp4");ok(c.restore(e,A,Collections.singletonList(wrong))==0&&!wrong.exists(),"native path must match exact saved source");
        ok(c.restore(e,A,Collections.singletonList(source))==1&&Arrays.equals(original,Files.readAllBytes(source.toPath())),"approved native path restored from real bytes");
        Files.write(source.toPath(),new byte[]{9});ok(c.restore(e,A,Collections.singletonList(source))==0&&source.length()==1,"existing official path is not overwritten");
        c=new RecallMediaCache(dir,t);e=c.find(A,"room@chatroom",2,90002);ok(e!=null&&e.isRecalled()&&c.copies(e).size()==1,"retained copy survives process restart");
        RecallMediaCache.Entry pending=entry(c,t,3,49);File attachment=file(root,"com.tencent.mm/MicroMsg/Download/document.pdf",bytes);
        ok(c.snapshot(pending,attachment,bytes.length,true),"native attachment in common download folder cached with account identity");
        t.now+=180000;c=new RecallMediaCache(dir,t);ok(c.find(A,"room@chatroom",3,90003)==null&&attachment.exists(),"startup sweeps expired copies without deleting attachment");
        pending=entry(c,t,4,49);File foreign=file(root,"MicroMsg/"+B+"/video/foreign.mp4",bytes);ok(!c.snapshot(pending,foreign,bytes.length,true),"another account's media path rejected");
        File outside=file(root,"unrelated/secret.dat",bytes);ok(!c.snapshot(pending,outside,bytes.length,true),"unrelated filesystem file rejected");
        ok(c.register(A,"room@chatroom",5,90005,1,t.now)==null,"text is not registered as media");
        ok(c.register(A,"room@chatroom",5,90005,43,t.now+1)==null,"future reception timestamp rejected");
        ok(c.register(A,"room@chatroom",5,90005,43,t.now-180000)==null,"expired arrival not registered");
        pending=entry(c,t,6,49);ok(c.snapshot(pending,attachment,bytes.length,true),"attachment snapshot available");c.recall(pending);RecallMediaCache.Copy copy=c.copies(pending).get(0);Files.write(copy.file.toPath(),new byte[bytes.length]);Files.delete(attachment.toPath());
        ok(c.restore(pending,A,Collections.singletonList(attachment))==0&&!attachment.exists(),"tampered media hash cannot restore");
        pending=entry(c,t,7,43);File valid=file(root,"MicroMsg/"+A+"/video/seven.mp4",bytes);c.snapshot(pending,valid,bytes.length,true);ok(c.discardPending()>=1&&valid.exists(),"turning off only discards temporary module copies");
        ok(c.find(A,"room@chatroom",2,90002)!=null,"turning off does not discard retained recalled media");
        ok(!MediaMessagePolicy.media(49,2001,0)&&!MediaMessagePolicy.media(49,2000,0),"red packets and transfers never become attachments");
        ok(!MediaMessagePolicy.media(43,0,1)&&!MediaMessagePolicy.media(49,6,1),"own video/file recalls stay native");
        ok(MediaMessagePolicy.media(43,0,0)&&MediaMessagePolicy.media(62,0,0)&&MediaMessagePolicy.media(49,6,0),"video, short-video and file types accepted");
        ok(MediaMessagePolicy.original(false,true,3,0,true),"independent original switch runs without any anti-recall input");
        ok(!MediaMessagePolicy.original(true,true,3,0,true)&&!MediaMessagePolicy.original(false,false,3,0,true),"module off and explicit original switch honored");
        ok(!MediaMessagePolicy.original(false,true,3,1,true)&&!MediaMessagePolicy.original(false,true,3,0,false),"own photos and unavailable originals excluded");
        System.out.println("PASS "+checks+" media cache expiry, retention, restore, identity and independent original policy checks");
    }
}
