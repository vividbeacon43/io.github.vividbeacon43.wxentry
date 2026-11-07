import java.nio.file.*;import java.util.*;import java.security.*;import java.util.zip.Adler32;
import com.android.tools.smali.dexlib2.dexbacked.*;import com.android.tools.smali.dexlib2.iface.*;import com.zafiro.wxentry.FrameworkDexCompat;
public final class FrameworkDexOrderTest {
 static java.lang.reflect.Method runtime;
 static byte[] adapt(byte[] input,String bridge)throws Exception{return runtime==null?FrameworkDexCompat.adapt(input,bridge):(byte[])runtime.invoke(null,input,bridge);}
 static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static int u(byte[] b,int p){return(b[p]&255)|((b[p+1]&255)<<8)|((b[p+2]&255)<<16)|((b[p+3]&255)<<24);}
 static void sorted(List<String> values,String label){for(int i=1;i<values.size();i++)if(values.get(i-1).compareTo(values.get(i))>=0)throw new AssertionError(label+" order broken");check(true,label+" sorted");for(String value:values)if(Collections.binarySearch(values,value)<0)throw new AssertionError(label+" lookup missing");check(true,label+" all searchable");}
 public static void main(String[] args)throws Exception {
  String isolated=System.getProperty("wxentry.runtime.jar");
  if(isolated!=null){java.net.URLClassLoader loader=new java.net.URLClassLoader(new java.net.URL[]{Paths.get(isolated).toUri().toURL()},null);runtime=Class.forName("com.zafiro.wxentry.FrameworkDexCompat",true,loader).getMethod("adapt",byte[].class,String.class);}
  for(String name:args){byte[] input=Files.readAllBytes(Paths.get(name)),saved=input.clone();DexBackedDexFile original=new DexBackedDexFile(null,input);
   check(adapt(input,"de.robv.android.xposed.XposedBridge")==input,"ordinary framework no rewrite");
   Set<String> classNames=new TreeSet<>();for(ClassDef c:original.getClasses())classNames.add(c.getType());
   for(String bridge:new String[]{"TRNSW.SewGbseppMdb.FjO.XposedBridge","Q.QbMDJcMVVu.UWe.FklUW.XposedBridge","NnukBy.tOtBQ.X.VwOPfXj.XposedBridge","mxHbgo.leg.b.CpVwbo.Fr.XposedBridge","short.XposedBridge"}) {
    byte[] output=adapt(input,bridge);DexBackedDexFile dex=new DexBackedDexFile(null,output);
    sorted(dex.getStringSection(),"strings");sorted(dex.getTypeSection(),"types");
    Set<String> actual=new TreeSet<>();int methods=0;for(ClassDef c:dex.getClasses()){actual.add(c.getType());for(Method m:c.getMethods()){methods++;if(m.getImplementation()!=null)for(Object ins:m.getImplementation().getInstructions()){} }}
    check(actual.equals(classNames),"all original classes survive rewrite");check(methods>100,"method bodies decode after index rewriting");
    check(Arrays.equals(input,saved),"signed/cache input unchanged");check(u(output,32)==output.length,"rebuilt file size");
    MessageDigest sha=MessageDigest.getInstance("SHA-1");sha.update(output,32,output.length-32);check(Arrays.equals(sha.digest(),Arrays.copyOfRange(output,12,32)),"rebuilt SHA-1");Adler32 adler=new Adler32();adler.update(output,12,output.length-12);check((int)adler.getValue()==u(output,8),"rebuilt Adler32");
    String prefix=bridge.substring(0,bridge.length()-12).replace('.','/');boolean rewritten=false;for(String t:dex.getTypeSection()){if(t.contains("de/robv/android/xposed/"))throw new AssertionError("stale framework type");rewritten|=t.contains(prefix);}check(rewritten,"actual framework reference retained without stale types");
   }
  }
  System.out.println("PASS "+checks+" framework DEX ordering/search/body/immutability checks");
 }
}
