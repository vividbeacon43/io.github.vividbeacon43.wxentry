package android.util;
import java.io.*;
public class AtomicFile {
    public static boolean fail;
    final File file,backup;
    public AtomicFile(File base) { file=base;backup=new File(base+".bak"); }
    public File getBaseFile(){return file;}
    public FileOutputStream startWrite() throws IOException {
        if(fail)throw new IOException("write blocked");
        if(file.exists()) { if(backup.exists())file.delete();else if(!file.renameTo(backup))throw new IOException("backup"); }
        return new FileOutputStream(file);
    }
    public void finishWrite(FileOutputStream stream) { try { stream.getFD().sync();stream.close();backup.delete(); }catch(IOException e) { throw new RuntimeException(e); } }
    public void failWrite(FileOutputStream stream) { try { stream.close();file.delete();if(backup.exists())backup.renameTo(file); }catch(IOException e) {} }
    public FileInputStream openRead() throws FileNotFoundException {
        if(backup.exists()) { file.delete();backup.renameTo(file); }
        return new FileInputStream(file);
    }
}
