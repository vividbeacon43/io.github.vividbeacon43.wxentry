import java.nio.file.*;import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;import com.android.tools.smali.dexlib2.iface.*;
public final class DexRuntimeEnumContractTest {
 public static void main(String[] args)throws Exception {
  DexBackedDexFile dex=new DexBackedDexFile(null,Files.readAllBytes(Paths.get(args[0])));int enums=0;
  for(ClassDef type:dex.getClasses())if("Ljava/lang/Enum;".equals(type.getSuperclass())) {
   enums++;boolean values=false,valueOf=false;
   for(Method m:type.getMethods()) {
    if(m.getName().equals("values")&&m.getParameterTypes().isEmpty()&&m.getReturnType().equals("["+type.getType())&&(m.getAccessFlags()&9)==9)values=true;
    if(m.getName().equals("valueOf")&&m.getParameterTypes().size()==1&&m.getParameterTypes().get(0).equals("Ljava/lang/String;")&&m.getReturnType().equals(type.getType())&&(m.getAccessFlags()&9)==9)valueOf=true;
   }
   if(!values||!valueOf)throw new AssertionError("Enum reflection contract removed from final DEX: "+type.getType());
  }
  if(enums<5)throw new AssertionError("Runtime enums unexpectedly absent");
  System.out.println("PASS "+enums+" final DEX enum reflection contracts");
 }
}
