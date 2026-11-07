package com.zafiro.wxentry;
import android.util.AtomicFile;
import java.io.*;

/** App-user setting: available before a WeChat account exists. */
final class TabletModeStore {
    final File path;
    TabletModeStore(File directory){path=new File(directory,"experimental.tablet.mode");}
    synchronized boolean read() throws IOException {
        try(InputStream in=new AtomicFile(path).openRead()){
            byte[] b=new byte[4];int n=in.read(b);if(n==2&&b[1]=='\n'&&(b[0]=='0'||b[0]=='1')&&in.read()==-1)return b[0]=='1';
            throw new IOException("Invalid tablet setting");
        }catch(FileNotFoundException missing){return false;}
    }
    synchronized void write(boolean enabled) throws IOException {
        File parent=path.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create setting directory");
        AtomicFile atomic=new AtomicFile(path);FileOutputStream out=null;
        try{out=atomic.startWrite();out.write(enabled?new byte[]{'1','\n'}:new byte[]{'0','\n'});atomic.finishWrite(out);}
        catch(Throwable failure){if(out!=null)atomic.failWrite(out);throw new IOException("Cannot save tablet setting",failure);}
    }
}
