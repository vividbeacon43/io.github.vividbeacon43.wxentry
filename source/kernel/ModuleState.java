package com.zafiro.wxentry.kernel;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

final class ModuleState {
    private ModuleState() {}

    static boolean flag(String path) { return new File(path).exists(); }
    static String hash(String key) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(key.getBytes("UTF-8"));
            StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            return out.toString();
        } catch(Throwable failure) { throw new IllegalStateException(failure); }
    }
    static synchronized boolean write(String path,String value) {
        AtomicFile file=new AtomicFile(new File(path));FileOutputStream stream=null;
        try {
            new File(path).getParentFile().mkdirs();
            stream=file.startWrite();stream.write(value.getBytes("UTF-8"));file.finishWrite(stream);return true;
        } catch(Throwable failure) {
            if(stream!=null)file.failWrite(stream);
            Kernel.T("module state write failed");return false;
        }
    }
    static synchronized String read(String path) {
        try {
            java.io.InputStream stream=new AtomicFile(new File(path)).openRead();
            try {
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                byte[] buffer=new byte[4096];int count;
                while((count=stream.read(buffer))!=-1)bytes.write(buffer,0,count);
                return bytes.toString("UTF-8");
            } finally { stream.close(); }
        } catch(java.io.FileNotFoundException absent) { return null; }
        catch(Throwable failure) { throw new IllegalStateException("Cannot read red packet state",failure); }
    }
}
