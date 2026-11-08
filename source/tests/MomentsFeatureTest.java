package com.zafiro.wxentry.kernel;
import java.nio.file.*;import java.util.*;import de.robv.android.xposed.*;

public final class MomentsFeatureTest {
 static int checks;static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static List<Object> hook(List<Object> rows,boolean thrown)throws Throwable{XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.args=new Object[]{new zt3.c()};p.thisObject=new el4.g();p.result=rows;p.thrown=thrown;XposedBridge.hook.after(p);return (List<Object>)p.result;}
 public static void main(String[] args)throws Throwable{
  Kernel.WXDIR=Files.createTempDirectory(Paths.get(args[0]),"moments-").toString()+java.io.File.separator;
  ok(MomentsConfig.decode(null).forward&&!MomentsConfig.decode(null).adBlock,"existing forwarding preserved and new ad switch defaults off");
  for(boolean f:new boolean[]{false,true})for(boolean a:new boolean[]{false,true}){MomentsConfig c=MomentsConfig.decode(new MomentsConfig(f,a).encode());ok(c.forward==f&&c.adBlock==a,"independent switch round trip");}
  for(String invalid:new String[]{"","format=2\nforward=1\nads=1\n","format=1\nforward=1\nads=1\nads=0\n","format=1\nforward=1\nads=yes\n","format=1\nforward=1\nads=1\nextra=1\n",String.join("",Collections.nCopies(1025,"x"))}){MomentsConfig c=MomentsConfig.decode(invalid);ok(!c.forward&&!c.adBlock,"invalid existing configuration fails closed");}
  final Object first=new Object(),last=new Object(),ad=new Object();final List<Object> rows=Collections.unmodifiableList(Arrays.asList(first,ad,null,last));
  SnsAdPolicy.Marker<Object> marker=new SnsAdPolicy.Marker<Object>(){public boolean isAd(Object value){return value==ad;}};
  ok(SnsAdPolicy.filter(rows,false,marker)==rows,"off returns original page identity");List<Object> kept=SnsAdPolicy.filter(rows,true,marker);
  ok(kept.equals(Arrays.asList(first,null,last))&&rows.size()==4,"filter preserves ordinary order, nulls, and original page");
  ok(SnsAdPolicy.filter(Arrays.asList(first,last),true,marker).get(0)==first,"ordinary row identity retained");ok(SnsAdPolicy.filter(Collections.singletonList(ad),true,marker).isEmpty(),"all-ad page has no placeholder row");
  ok(MomentsSettings.current().forward&&!MomentsSettings.current().adBlock,"new account defaults");
  ok(MomentsSettings.set(MomentsConfig.Feature.AD_BLOCK,true)&&MomentsSettings.current().forward,"ad switch cannot disable forwarding");
  final int[] notices={0};MomentsSettings.forwardChanged=new Runnable(){public void run(){notices[0]++;}};
  ok(MomentsSettings.set(MomentsConfig.Feature.FORWARD,false)&&!MomentsSettings.current().forward&&MomentsSettings.current().adBlock&&notices[0]==1,"turning forwarding off notifies active preparation and leaves ads setting");
  Kernel.owner="wxid_moments_b";ok(MomentsSettings.current().forward&&!MomentsSettings.current().adBlock,"account switch cannot inherit filtering or forwarding flags");
  ok(MomentsSettings.set(MomentsConfig.Feature.FORWARD,false),"second account can save independently");Kernel.owner="wxid_moments_a";ok(!MomentsSettings.current().forward&&MomentsSettings.current().adBlock,"first account settings retained");
  ok(MomentsSettings.set(MomentsConfig.Feature.FORWARD,true)&&notices[0]==3,"forwarding can be re-enabled");
  Kernel.owner="wxid_moments_corrupt";RedPacketRuntime.write(MomentsSettings.path(Kernel.owner),"bad\n");ok(!MomentsSettings.current().forward&&!MomentsSettings.current().adBlock,"corrupt saved account remains disabled");Kernel.owner="wxid_moments_a";
  SnsAdRuntime.start(MomentsFeatureTest.class.getClassLoader());ok(XposedBridge.method.getName().equals("X7"),"hooks merged native timeline page");
  cl4.p ordinary=new cl4.p(2),nativeAd=new cl4.p(34);Object unknown=new Object();List<Object> nativeRows=Collections.unmodifiableList(Arrays.<Object>asList(ordinary,nativeAd,unknown,ordinary));
  ok(hook(nativeRows,false).equals(Arrays.<Object>asList(ordinary,unknown,ordinary)),"native marker removes only ads while preserving ordinary duplicate rows and unknown types");
  ok(nativeRows.size()==4&&hook(nativeRows,true)==nativeRows,"source/failed query untouched");
  nativeAd.fail=true;ok(hook(nativeRows,false)==nativeRows,"unavailable native marker leaves entire original page intact");nativeAd.fail=false;
  MomentsSettings.set(MomentsConfig.Feature.AD_BLOCK,false);ok(hook(nativeRows,false)==nativeRows,"off restores unfiltered next refresh");MomentsSettings.set(MomentsConfig.Feature.AD_BLOCK,true);
  for(String edition:new String[]{"free","pro","invalid"}){Kernel.edition=edition;ok(hook(nativeRows,false)==nativeRows&&!MomentsSettings.set(MomentsConfig.Feature.AD_BLOCK,true),"unentitled edition blocked "+edition);}
  Kernel.edition="plus";Kernel.verified=false;ok(hook(nativeRows,false)==nativeRows&&!MomentsSettings.set(MomentsConfig.Feature.FORWARD,true),"lost signed authorization blocks saved flags and changes");Kernel.verified=true;Kernel.MODULE_OFF=true;ok(hook(nativeRows,false)==nativeRows,"module off stops filtering");Kernel.MODULE_OFF=false;Kernel.edition="vip";ok(hook(nativeRows,false).size()==3,"VIP can use filtering");
  Kernel.owner="";ok(!MomentsSettings.current().forward&&!MomentsSettings.current().adBlock&&!MomentsSettings.set(MomentsConfig.Feature.AD_BLOCK,true),"unresolved account cannot change or inherit flags");
  System.out.println("PASS "+checks+" Moments switch/account/entitlement and native ad-page filtering checks");
 }
}
