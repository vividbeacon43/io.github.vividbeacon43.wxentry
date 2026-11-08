import java.io.*;import java.util.*;import java.util.zip.*;
import com.android.tools.smali.dexlib2.*;import com.android.tools.smali.dexlib2.dexbacked.*;import com.android.tools.smali.dexlib2.iface.*;
public final class NativeSnsAdContractCheck extends NativeTabletContractCheck {
 public static void main(String[] args)throws Exception{
  Set<String> wanted=new HashSet<>(Arrays.asList(type("el4.g"),type("cl4.p")));
  try(ZipFile z=new ZipFile(args[0])){Enumeration<? extends ZipEntry> es=z.entries();while(es.hasMoreElements()){ZipEntry e=es.nextElement();if(!e.getName().matches("classes[0-9]*\\.dex"))continue;DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(z.getInputStream(e)));for(ClassDef c:dex.getClasses())if(wanted.contains(c.getType()))classes.put(c.getType(),c);}}
  Method page=method("el4.g","X7","Ljava/util/List;",type("zt3.c"));literal(page,"handleADSql");calls(page,"java.util.ArrayList","addAll");
  Method ad=method("cl4.p","isAd","Z");calls(ad,"lm.t6","getSourceType");
  if((page.getAccessFlags()&8)!=0||(ad.getAccessFlags()&8)!=0)throw new AssertionError("Native Moments methods must be instance methods");checks++;
  System.out.println("PASS "+checks+" native Moments merged-page and advertising marker contracts");
 }
}
