package com.zafiro.wxentry.kernel;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Copies only user-selected images. Never cleans the host's original media. */
final class SnsScheduleMedia {
    static final long MAX_CACHE=512L*1024*1024,MAX_PHOTO=32L*1024*1024;
    static File dir(String owner,String id)throws IOException{if(!id.matches("[a-f0-9]{32}"))throw new IOException("任务无效");return SnsScheduleStore.child(SnsScheduleStore.account(owner),id);}
    static File path(SnsScheduleData.Task t,SnsScheduleData.Photo p)throws IOException{return SnsScheduleStore.child(dir(t.owner,t.id),p.name);}
    static String digest(File file)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}return SnsScheduleStore.hex(d.digest());}
    static void verify(SnsScheduleData.Task t)throws Exception{for(SnsScheduleData.Photo p:t.photos){File f=path(t,p);if(!f.isFile()||f.length()!=p.size||!p.sha.equals(digest(f)))throw new IOException("保存的图片缺失或已变动，请重新选择");}}
    static long size(File file)throws IOException{if(!file.getAbsolutePath().equals(file.getCanonicalPath()))throw new IOException("缓存路径无效");if(file.isFile())return file.length();long n=0;File[] list=file.listFiles();if(list!=null)for(File f:list){n+=size(f);if(n>MAX_CACHE)return n;}return n;}
    static synchronized List<SnsScheduleData.Photo> importPhotos(Context c,String owner,String id,List<Uri> uris)throws Exception{
        if(uris.size()>9)throw new IOException("最多选择 9 张图片");File folder=dir(owner,id);if(folder.exists())throw new IOException("请重新打开编辑页面");folder.mkdirs();List<SnsScheduleData.Photo> result=new ArrayList<SnsScheduleData.Photo>();
        try{long used=size(SnsScheduleStore.root());for(int i=0;i<uris.size();i++){if(!SnsScheduleRuntime.authorized(owner))throw new IOException("账号或 VIP 权限已变更");Uri uri=uris.get(i);if(!"content".equals(uri.getScheme()))throw new IOException("请从系统图片选择器选择");String name=String.format(java.util.Locale.ROOT,"%02d.image",i);File file=SnsScheduleStore.child(folder,name);long bytes=0;try(InputStream in=c.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(file)){if(in==null)throw new IOException("图片无法读取");byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){bytes+=n;if(bytes>MAX_PHOTO||used+bytes>MAX_CACHE)throw new IOException("单图限 32 MiB，定时图片缓存总量限 512 MiB");out.write(b,0,n);}out.getFD().sync();}BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);if(o.outWidth<1||o.outHeight<1||o.outWidth>30000||o.outHeight>30000)throw new IOException("图片格式或尺寸不支持");used+=bytes;result.add(new SnsScheduleData.Photo(name,digest(file),bytes));}return result;}catch(Exception e){clean(owner,id);throw e;}
    }
    static void clean(String owner,String id){try{File dir=dir(owner,id);File[] files=dir.listFiles();if(files!=null)for(File file:files)if(file.getName().matches("[0-9]{2}\\.image")&&file.getAbsolutePath().equals(file.getCanonicalPath())&&file.isFile())file.delete();dir.delete();}catch(Throwable e){SnsScheduleRuntime.log("cleanup-failed",e);}}
    static void sweep(String owner,Set<String> editing){try{SnsScheduleData data=SnsScheduleStore.load(owner);File[] dirs=SnsScheduleStore.account(owner).listFiles();if(dirs==null)return;for(File f:dirs){String id=f.getName();if(!id.matches("[a-f0-9]{32}")||editing.contains(id))continue;SnsScheduleData.Task task=data.find(id);if(task==null&&System.currentTimeMillis()-f.lastModified()>180000||task!=null&&(task.state==SnsScheduleData.State.PUBLISHED||task.state==SnsScheduleData.State.CANCELLED||task.state==SnsScheduleData.State.FAILED))clean(owner,id);}}catch(Throwable e){SnsScheduleRuntime.log("cleanup-unavailable",e);}}
}
