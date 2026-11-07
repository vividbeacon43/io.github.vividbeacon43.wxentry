"""Build the canonical-rewrite loader; no deployment or device operations."""
from pathlib import Path
import sys,subprocess,zipfile,hashlib,json,uuid,os
R=Path(__file__).resolve().parents[1];W=R.parent
JAVA=Path(r'C:\Users\Q\Documents\Codex\2026-10-22\ban\work\toolchain\jdk\jdk-17.0.20.1+1\bin')
LIB=W/'tools/libs';OUT=R/'build-loader'/('run-'+uuid.uuid4().hex[:10]);OUT.mkdir(parents=True)
sys.stdout.reconfigure(encoding='utf-8')
def run(args):
 p=subprocess.run([str(a) for a in args],capture_output=True,timeout=180)
 if p.returncode:
  (OUT/'private-build-error.log').write_bytes(p.stdout+p.stderr);print(p.stderr.decode('utf-8','replace')[-4500:]);raise RuntimeError('Loader build/check failed')
 return p.stdout.decode('utf-8','replace').strip()
runtime=OUT/'dex-runtime.jar'
prefixes=['com/android/tools/smali/dexlib2/','com/android/tools/smali/util/','com/google/common/','javax/annotation/','com/google/errorprone/','com/google/j2objc/','org/jspecify/']
with zipfile.ZipFile(LIB/'jadx-all.jar') as source,zipfile.ZipFile(runtime,'w',zipfile.ZIP_DEFLATED) as target:
 for name in source.namelist():
  if name.endswith('.class') and any(name.startswith(p) for p in prefixes):target.writestr(name,source.read(name))
classes=OUT/'classes';classes.mkdir();cp=os.pathsep.join(str(LIB/n) for n in ['android.jar','xposed-api-82.jar'])+os.pathsep+str(runtime)
sources=list((R/'loader').glob('*.java'))
run([JAVA/'javac.exe','-J-Duser.language=en','-encoding','UTF-8','-source','8','-target','8','-nowarn','-cp',cp,'-d',classes,*sources])
jar=OUT/'loader.jar'
with zipfile.ZipFile(jar,'w') as z:
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
rules=OUT/'keep-rules.pro';rules.write_text('''-keep class com.zafiro.wxentry.** { *; }
-keep,allowobfuscation enum * { *; }
-keepclassmembernames enum * { *; }
-repackageclasses com.zafiro.wxentry.dexrt
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.**
-dontwarn com.google.j2objc.**
''',encoding='utf-8')
dex=OUT/'dex';dex.mkdir()
optimized=OUT/'optimized-runtime.jar'
run([JAVA/'java.exe','-cp',LIB/'d8.jar','com.android.tools.r8.R8','--classfile','--release','--lib',JAVA.parent,'--classpath',LIB/'android.jar','--classpath',LIB/'xposed-api-82.jar','--pg-conf',rules,'--output',optimized,jar,runtime])
run([JAVA/'java.exe','-cp',LIB/'d8.jar','com.android.tools.r8.D8','--min-api','24','--lib',LIB/'android.jar','--classpath',LIB/'xposed-api-82.jar','--output',dex,optimized])
assert len(list(dex.glob('*.dex')))==1,'Loader must remain one DEX so embedded kernel stays classes2.dex'
tests=OUT/'tests';tests.mkdir()
run([JAVA/'javac.exe','-J-Duser.language=en','-encoding','UTF-8','-cp',str(classes)+os.pathsep+str(runtime),'-d',tests,R/'tests/FrameworkDexOrderTest.java'])
build=json.loads((R/'build-local/result.json').read_text(encoding='utf-8'))
check=run([JAVA/'java.exe','-Dwxentry.runtime.jar='+str(optimized),'-cp',os.pathsep.join(map(str,[tests,classes,LIB/'jadx-all.jar'])),'FrameworkDexOrderTest',*[build['editions'][e]['dex'] for e in ['free','plus','vip']]])
run([JAVA/'javac.exe','-J-Duser.language=en','-encoding','UTF-8','-cp',LIB/'jadx-all.jar','-d',tests,R/'tests/DexRuntimeEnumContractTest.java'])
enum_check=run([JAVA/'java.exe','-cp',str(tests)+os.pathsep+str(LIB/'jadx-all.jar'),'DexRuntimeEnumContractTest',dex/'classes.dex'])
report={'versionCode':97,'versionName':'1.97','loader_dex':str(dex/'classes.dex'),'sha256':hashlib.sha256((dex/'classes.dex').read_bytes()).hexdigest(),'bytes':(dex/'classes.dex').stat().st_size,'checks':check,'enum_contracts':enum_check,'optimized_runtime_tested':True,'signed_cache_input_immutable':True,'runtime_dependency_source_sha256':hashlib.sha256((LIB/'jadx-all.jar').read_bytes()).hexdigest(),'single_dex':True,'phone_tested':False}
(R/'build-loader/result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
