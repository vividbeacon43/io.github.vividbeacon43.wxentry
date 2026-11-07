package com.zafiro.wxentry;
import java.io.*;import java.lang.reflect.*;import java.nio.file.*;import de.robv.android.xposed.*;
public class TabletModeTest {
 static int checks;static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static void reset(){TabletModeHooks.initialized=false;TabletModeHooks.requested=false;TabletModeHooks.active=false;TabletModeHooks.authorized=false;TabletModeHooks.bound.clear();TabletModeHooks.store=null;TabletModeHooks.status="off";XposedBridge.hooks.clear();}
 public static void main(String[] args)throws Throwable{
  File root=Files.createTempDirectory(Paths.get(args[0]),"tablet-").toFile();TabletModeStore store=new TabletModeStore(root);
  ok(!store.read(),"missing setting defaults off");store.write(true);ok(new TabletModeStore(root).read(),"on persists before any account exists");
  android.util.AtomicFile.fail=true;try{store.write(false);throw new AssertionError("expected failure");}catch(IOException expected){}finally{android.util.AtomicFile.fail=false;}
  ok(store.read(),"failed setting write preserves on");store.write(false);ok(!store.read(),"off persists");
  reset();TabletModeHooks.start(TabletModeTest.class.getClassLoader(),root);
  ok(XposedBridge.hooks.isEmpty()&&!TabletModeHooks.active,"default off installs no framework or native hook");
  ok(!com.tencent.mm.ui.ok.C()&&com.tencent.mm.ui.ok.originals==1,"default off preserves original phone classification");
  reset();store.write(true);TabletModeHooks.start(TabletModeTest.class.getClassLoader(),root);
  Method attach=android.app.Application.class.getDeclaredMethod("attach",android.content.Context.class);
  ok(XposedBridge.hooks.containsKey(attach)&&!TabletModeHooks.active,"opt-in installs early application lifecycle, no login needed");
  android.content.Context context=new android.content.Context();context.name="other.app";TabletModeHooks.bind(context);
  ok(!TabletModeHooks.active,"another package cannot activate");context.name="com.tencent.mm";context.manager.info.versionCode=3210;TabletModeHooks.bind(context);
  ok(!TabletModeHooks.active&&TabletModeHooks.status.equals("unsupported-version"),"unsupported WeChat version fails closed");
  context.manager.info.versionCode=3220;context.manager.info.versionName="8.0.79";TabletModeHooks.bind(context);
  ok(!TabletModeHooks.active,"version label mismatch fails closed");context.manager.info.versionName="8.0.80";
  XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.args=new Object[]{context};XposedBridge.hooks.get(attach).invoke(p);
  Method tablet=com.tencent.mm.ui.ok.class.getDeclaredMethod("C");ok(TabletModeHooks.active&&XposedBridge.hooks.containsKey(tablet),"tablet classification ready during application attach before login");
  int count=XposedBridge.hooks.size();TabletModeHooks.bind(context);ok(XposedBridge.hooks.size()==count,"repeated activity/context bind does not duplicate hooks");
  p=new XC_MethodHook.MethodHookParam();XposedBridge.hooks.get(tablet).invoke(p);
  ok(p.stopped&&Boolean.TRUE.equals(p.result)&&com.tencent.mm.ui.ok.originals==1,"enabled native predicate returns tablet without running phone classifier");
  ok(cj3.b.created==0,"hook validation never starts a QR request or login");
  com.tencent.mm.plugin.login_exdevice.ui.LoginAsExDeviceUI qr=new com.tencent.mm.plugin.login_exdevice.ui.LoginAsExDeviceUI();qr.r="sensitive-session-id";qr.s=new byte[]{1,2,3};
  Method callback=qr.getClass().getDeclaredMethod("onSceneEnd",int.class,int.class,String.class,com.tencent.mm.modelbase.n1.class);
  ok(XposedBridge.hooks.containsKey(callback),"native QR outcome observed for diagnosis");
  p=new XC_MethodHook.MethodHookParam();p.thisObject=qr;p.args=new Object[]{0,0,"private-native-message",new cj3.b()};XposedBridge.hooks.get(callback).after(p);
  String logs=String.join("\n",Main.logs);ok(logs.contains("scene=502 error=0/0 hasId=true hasKey=true"),"native QR readiness flags recorded");
  ok(!logs.contains(qr.r)&&!logs.contains("private-native-message"),"QR credentials and native message never logged");
  store.write(false);ok(!TabletModeHooks.configured()&&TabletModeHooks.active,"configuration changes are persisted for next process, not partial mid-login changes");
  reset();TabletModeHooks.start(TabletModeTest.class.getClassLoader(),root);ok(!TabletModeHooks.active&&XposedBridge.hooks.isEmpty(),"restart after off restores native phone behavior");
  Files.write(store.path.toPath(),"invalid".getBytes("UTF-8"));reset();TabletModeHooks.start(TabletModeTest.class.getClassLoader(),root);
  ok(!TabletModeHooks.active&&XposedBridge.hooks.isEmpty()&&TabletModeHooks.status.equals("setting-unreadable"),"corrupt setting fails closed");
  store.write(true);reset();TabletModeHooks.start(TabletModeTest.class.getClassLoader(),root);
  context.loader=new ClassLoader(TabletModeTest.class.getClassLoader()) {protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(name.equals("cj3.b"))throw new ClassNotFoundException(name);return super.loadClass(name,resolve);}};
  TabletModeHooks.bind(context);ok(!TabletModeHooks.active&&!XposedBridge.hooks.containsKey(tablet),"missing QR contract cannot leave a partial tablet hook");
  File other=new File(root,"another-app-user");ok(!new TabletModeStore(other).read(),"app users keep separate settings");
  store.write(false);File backup=new File(store.path+".bak");ok(store.path.renameTo(backup),"simulate interrupted atomic write");Files.write(store.path.toPath(),"1\n".getBytes("UTF-8"));ok(!store.read(),"atomic backup restores last committed mode");
  store.write(true);Main.paid=false;reset();TabletModeHooks.start(TabletModeTest.class.getClassLoader(),root);
  ok(!TabletModeHooks.active&&XposedBridge.hooks.isEmpty(),"old enabled setting cannot install hooks for free user");
  ok(!TabletModeHooks.configured(),"free user cannot report enabled state from old preference");
  Main.paid=true;reset();TabletModeHooks.start(TabletModeTest.class.getClassLoader(),root);context=new android.content.Context();TabletModeHooks.bind(context);
  ok(TabletModeHooks.active,"paid activation still starts tablet mode before account lookup");
  Main.paid=false;TabletModeHooks.refreshAuthorization();p=new XC_MethodHook.MethodHookParam();XposedBridge.hooks.get(tablet).invoke(p);
  ok(!p.stopped&&!TabletModeHooks.active,"downgrade releases native predicate in current process");
  Main.paid=true;count=XposedBridge.hooks.size();TabletModeHooks.bind(context);p=new XC_MethodHook.MethodHookParam();XposedBridge.hooks.get(tablet).invoke(p);
  ok(TabletModeHooks.active&&p.stopped&&Boolean.TRUE.equals(p.result),"paid authorization restoration reuses valid hook");
  ok(XposedBridge.hooks.size()==count,"authorization restoration does not duplicate hooks");
  System.out.println("PASS "+checks+" early tablet mode, atomic persistence, isolation and disable checks");
 }
}
