package android.content;
public class Context {
 public String name="com.tencent.mm";public ClassLoader loader=getClass().getClassLoader();
 public android.content.pm.PackageManager manager=new android.content.pm.PackageManager();
 public String getPackageName(){return name;}public ClassLoader getClassLoader(){return loader;}
 public android.content.pm.PackageManager getPackageManager(){return manager;}
}
