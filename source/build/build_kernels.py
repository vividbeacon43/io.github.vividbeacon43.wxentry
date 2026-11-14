"""Build and verify WXEntry kernels; signing is optional. Never deploy or change the APK."""
from pathlib import Path
import sys,subprocess,zipfile,json,hashlib,re,base64,os,uuid,argparse
ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT.parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java-bin',type=Path,default=Path(r'C:\Users\Q\Documents\Codex\2026-10-22\ban\work\toolchain\jdk\jdk-17.0.20.1+1\bin'))
parser.add_argument('--libs',type=Path,default=WORK/'tools/libs')
parser.add_argument('--python-libs',type=Path,default=WORK/'tools/ssh-python')
parser.add_argument('--signing-key',type=Path,help='Optional existing private manifest key; never packaged')
parser.add_argument('--baseline-dir',type=Path,default=ROOT/'baseline-v212')
parser.add_argument('--free-only',action='store_true',help='Build the public free kernel without private sources')
parser.add_argument('--native-apk',type=Path,help='Optional local WeChat APK for native contract checks')
args=parser.parse_args()
sys.path.insert(0,str(args.python_libs))
if args.signing_key:
    from cryptography.hazmat.primitives import serialization,hashes
    from cryptography.hazmat.primitives.asymmetric import padding
    from cryptography.hazmat.primitives.ciphers import Cipher,algorithms,modes
    from cryptography.hazmat.primitives.padding import PKCS7
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
BIN=args.java_bin
LIBS=args.libs.resolve()
JAVA=BIN/('java.exe' if (BIN/'java.exe').exists() else 'java')
JAVAC=BIN/('javac.exe' if (BIN/'javac.exe').exists() else 'javac')
OUT=ROOT/'build-local'/('run-'+uuid.uuid4().hex[:10]);OUT.mkdir(parents=True)
CP=os.pathsep.join(str(LIBS/n) for n in ['android.jar','xposed-api-82.jar'])
def run(args):
    command=[str(a) for a in args]
    if Path(command[0]).name.lower() in ['javac','javac.exe']:command.insert(1,'-J-Duser.language=en')
    p=subprocess.run(command,capture_output=True,timeout=120)
    if p.returncode:
        sys.stderr.write(p.stderr.decode('utf-8','replace')[-5000:]);raise RuntimeError('Build or verification failed')
    return p.stdout.decode('utf-8','replace').strip()
def compile_dex(edition):
    out=OUT/edition;src=out/'src';classes=out/'classes';dex=out/'dex'
    for p in [src,classes,dex]:p.mkdir(parents=True)
    (src/'Kernel.java').write_bytes((ROOT/f'kernel/Kernel_{edition}.java').read_bytes())
    sources=[src/'Kernel.java',ROOT/'kernel/VoiceTextRuntime.java',*[ROOT/'kernel'/n for n in ['GameEmojiData.java','GameEmojiStore.java','GameEmojiRuntime.java','GameEmojiUi.java']],ROOT/'kernel/UnreadReadShortcut.java',ROOT/'kernel/VoiceRecallRuntime.java',ROOT/'kernel/VoiceForwardRuntime.java',ROOT/'kernel/VoiceForwardSender.java',ROOT/'kernel/VoiceForwardPicker.java',ROOT/'kernel/VoiceForwardTargets.java',ROOT/'kernel/VoiceForwardQueue.java',ROOT/'kernel/VoiceForwardIcon.java',ROOT/'kernel/VoiceForwardLayout.java']
    sources += [ROOT/'kernel'/name for name in ['RedPacketUi.java','RedPacketUiNative.java','ReceiptConfig.java','ReceiptStore.java','ReceiptTargets.java','ReceiptSettings.java','MomentsConfig.java','MomentsSettings.java','SnsLocalData.java','SnsLocalSelection.java','SnsLocalStore.java','SnsLocalSettings.java','SettingsUi.java','WechatAppearance.java','MenuPalette.java','FeatureMenuUi.java','PlusPanelLayout.java','RecallTipRuntime.java','RecallMarkStore.java','RecallMarkLayout.java','NativeSceneBus.java','MediaMessagePolicy.java','RecallMediaCache.java','MediaNative.java','MediaRecallRuntime.java','OriginalImageRuntime.java','ScanCameraRuntime.java']]
    sources += [ROOT/'kernel'/name for name in ('ModuleState.java','RedPacketMessage.java')]
    sources.append(ROOT/'kernel/ForegroundRedPacket.java')
    run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-cp',CP,'-d',classes,*sources])
    jar=out/'classes.jar'
    with zipfile.ZipFile(jar,'w') as z:
        for f in classes.rglob('*.class'):
            name=f.relative_to(classes).as_posix()
            if name != 'com/zafiro/wxentry/Main.class':
                z.write(f,name)
    run([JAVA,'-cp',LIBS/'d8.jar','com.android.tools.r8.D8','--min-api','24','--lib',LIBS/'android.jar','--classpath',LIBS/'xposed-api-82.jar','--output',dex,jar])
    return dex/'classes.dex'
if __name__ == "__main__":
    if args.signing_key or args.native_apk:
        parser.error('--free-only builds the public kernel without publication or private contract checks')
    java_source=(ROOT/'loader/Main.java').read_text(encoding='utf-8')
    kernel_source=(ROOT/'kernel/Kernel_free.java').read_text(encoding='utf-8')
    apk_version=re.search(r'MY_VN\s*=\s*"([^"]+)"',java_source).group(1)
    version=re.search(r'\bBUILD\s*=\s*"(\d+)"',kernel_source).group(1)
    tests=OUT/'edition-check';tests.mkdir()
    jar=LIBS/'jadx-all.jar'
    run([JAVAC,'-encoding','UTF-8','-cp',jar,'-d',tests,ROOT/'tests/DexEditionCheck.java'])
    dex=compile_dex('free')
    plain=dex.read_bytes()
    check=run([JAVA,'-cp',os.pathsep.join(map(str,(tests,jar))),'DexEditionCheck',dex,'free',version])
    result={'version':version,'apk_target_version':apk_version,'signed':False,'editions':{'free':{
        'dex':str(dex),'sha256':hashlib.sha256(plain).hexdigest(),'bytes':len(plain),'check':check}}}
    (ROOT/'build-local/result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(check,flush=True)
    sys.exit(0)

