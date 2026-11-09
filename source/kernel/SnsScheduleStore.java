package com.zafiro.wxentry.kernel;

import android.util.AtomicFile;
import java.io.*;
import java.security.MessageDigest;

/** All scheduling writes are synchronized and fsynced before native submission. */
final class SnsScheduleStore {
    interface Edit {void apply(SnsScheduleData data)throws Exception;}
    static String hash(String value){try{return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8")));}catch(Exception e){throw new IllegalStateException(e);}}
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    static File root()throws IOException{File f=new File(Kernel.WXDIR,"moments.schedule");if(!f.getAbsolutePath().equals(f.getCanonicalPath()))throw new IOException("缓存目录无效");return f;}
    static File account(String owner)throws IOException{if(!ReceiptConfig.validId(owner))throw new IOException("账号未就绪");return child(root(),hash(owner));}
    static File child(File parent,String name)throws IOException{File f=new File(parent,name);if(!f.getAbsolutePath().equals(f.getCanonicalPath())||!f.getCanonicalPath().startsWith(parent.getCanonicalPath()+File.separator))throw new IOException("文件路径无效");return f;}
    static synchronized SnsScheduleData load(String owner)throws IOException{
        AtomicFile f=new AtomicFile(child(account(owner),"queue.bin"));try(InputStream in=f.openRead()){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){b.write(buf,0,n);if(b.size()>1024*1024)throw new IOException("任务文件过大");}return SnsScheduleData.decode(owner,b.toByteArray());}catch(FileNotFoundException absent){if(f.getBaseFile().exists())throw absent;return new SnsScheduleData(owner);}
    }
    static synchronized boolean edit(String owner,Edit edit){
        if(!SnsScheduleRuntime.authorized(owner))return false;FileOutputStream out=null;AtomicFile file=null;
        try{SnsScheduleData d=load(owner);edit.apply(d);byte[] bytes=d.encode();if(!SnsScheduleRuntime.authorized(owner))return false;File dir=account(owner);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建任务目录");file=new AtomicFile(child(dir,"queue.bin"));out=file.startWrite();out.write(bytes);out.getFD().sync();file.finishWrite(out);return true;}catch(Throwable e){if(file!=null&&out!=null)file.failWrite(out);SnsScheduleRuntime.log("save-failed",e);return false;}
    }
}
