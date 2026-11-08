package com.zafiro.wxentry.kernel;
import java.io.*;import java.security.MessageDigest;import java.util.*;

/** Task-owned copies protect original SNS cache files from the native composer lifecycle. */
final class SnsForwardFiles {
    interface Guard {boolean valid();}
    static final long MAX_FILE=256L*1024*1024,MAX_DRAFT=512L*1024*1024,MAX_ROOT=512L*1024*1024;
    static final long KEEP_MS=24L*60*60*1000,ORPHAN_MS=180000L;
    final File root,directory;long bytes;boolean handed;
    SnsForwardFiles(File parent)throws IOException{
        root=parent.getCanonicalFile();if(!root.isDirectory()&&!root.mkdirs())throw new IOException("SNS_DRAFT_DIRECTORY");
        cleanup(root,System.currentTimeMillis());
        if(total(root)>=MAX_ROOT)throw new IOException("SNS_DRAFT_QUOTA");
        directory=new File(root,"draft-"+UUID.randomUUID());if(!directory.mkdir())throw new IOException("SNS_DRAFT_DIRECTORY");
    }
    File copy(File source,boolean video,int index,Guard guard)throws IOException{
        if(source==null||!source.isFile()||!guard.valid())throw new IOException("SNS_SOURCE_UNAVAILABLE");
        source=source.getCanonicalFile();if(source.getPath().startsWith(root.getPath()+File.separator))throw new IOException("SNS_SOURCE_UNAVAILABLE");
        long length=source.length(),modified=source.lastModified();String extension=extension(source,video);
        if(length<=0||length>MAX_FILE||bytes>MAX_DRAFT-length||total(root)>MAX_ROOT-length)throw new IOException("SNS_DRAFT_QUOTA");
        if(extension==null)throw new IOException("SNS_SOURCE_FORMAT");
        File draft=new File(directory,"part-"+UUID.randomUUID());File target=new File(directory,String.format(Locale.ROOT,"%02d-%s.%s",index,UUID.randomUUID(),extension));
        try{
            try(InputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(draft)){
                byte[] b=new byte[65536];int n;long written=0;while((n=in.read(b))!=-1){if(!guard.valid())throw new IOException("SNS_CANCELLED");written+=n;if(written>length)throw new IOException("SNS_SOURCE_CHANGED");out.write(b,0,n);}out.getFD().sync();
            }
            if(!guard.valid())throw new IOException("SNS_CANCELLED");
            if(source.length()!=length||source.lastModified()!=modified||draft.length()!=length)throw new IOException("SNS_SOURCE_CHANGED");
            if(!digest(source,"SHA-256").equals(digest(draft,"SHA-256")))throw new IOException("SNS_SOURCE_CHANGED");
            if(!guard.valid())throw new IOException("SNS_CANCELLED");
            if(!draft.renameTo(target))throw new IOException("SNS_DRAFT_COMMIT");bytes+=length;return target;
        }finally{draft.delete();}
    }
    static String extension(File f,boolean video)throws IOException{
        byte[] b=new byte[32];int n;try(InputStream in=new FileInputStream(f)){n=in.read(b);}if(n<12)return null;
        boolean ftyp=b[4]=='f'&&b[5]=='t'&&b[6]=='y'&&b[7]=='p';
        if(video)return ftyp?"mp4":null;
        if((b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return "jpg";
        if((b[0]&255)==137&&b[1]=='P'&&b[2]=='N'&&b[3]=='G')return "png";
        if(b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P')return "webp";
        if(b[0]=='G'&&b[1]=='I'&&b[2]=='F')return "gif";
        if(ftyp)return "heic";return null;
    }
    static String digest(File f,String algorithm)throws IOException{
        try{MessageDigest d=MessageDigest.getInstance(algorithm);try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder out=new StringBuilder();for(byte b:d.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(java.security.NoSuchAlgorithmException impossible){throw new IOException(impossible);}
    }
    void handoff(){handed=true;try{new File(directory,".handed").createNewFile();}catch(IOException ignored){directory.setLastModified(System.currentTimeMillis());}}
    void discard()throws IOException{if(!handed)remove(directory);}
    private void remove(File file)throws IOException{
        removeScoped(root,directory,file);
    }
    private static void removeScoped(File root,File directory,File file)throws IOException{
        File path=file.getCanonicalFile();if(!path.getPath().startsWith(directory.getCanonicalPath()+File.separator)&&!path.equals(directory.getCanonicalFile()))throw new IOException("SNS_CLEANUP_SCOPE");
        if(!path.getPath().startsWith(root.getPath()+File.separator))throw new IOException("SNS_CLEANUP_SCOPE");
        if(file.isDirectory()){File[] cs=file.listFiles();if(cs!=null)for(File child:cs)removeScoped(root,directory,child);}if(file.exists()&&!file.delete())throw new IOException("SNS_CLEANUP_FAILED");
    }
    static void cleanup(File parent,long now)throws IOException{
        File root=parent.getCanonicalFile();File[] folders=root.listFiles();if(folders==null)return;
        for(File folder:folders){if(!folder.getName().matches("draft-[a-f0-9-]{36}")||!folder.isDirectory())continue;
            File marker=new File(folder,".handed");long stamp=marker.isFile()?marker.lastModified():folder.lastModified();
            if(now-stamp>=(marker.isFile()?KEEP_MS:ORPHAN_MS))removeScoped(root,folder,folder);
        }
    }
    static long total(File directory)throws IOException{
        long bytes=0;File[] cs=directory.listFiles();if(cs!=null)for(File child:cs){File f=child.getCanonicalFile();if(!f.getPath().startsWith(directory.getCanonicalPath()+File.separator))throw new IOException("SNS_DRAFT_SCOPE");bytes+=child.isDirectory()?total(child):child.length();if(bytes>MAX_ROOT)return bytes;}return bytes;
    }
}
