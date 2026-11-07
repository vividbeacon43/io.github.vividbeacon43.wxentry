"""Build and verify v230 kernels; signing is optional. Never deploy or change the APK."""
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
    sources=[src/'Kernel.java',ROOT/'kernel/VoiceRecallRuntime.java',ROOT/'kernel/VoiceForwardRuntime.java',ROOT/'kernel/VoiceForwardSender.java',ROOT/'kernel/VoiceForwardPicker.java',ROOT/'kernel/VoiceForwardTargets.java',ROOT/'kernel/VoiceForwardQueue.java',ROOT/'kernel/VoiceForwardIcon.java',ROOT/'kernel/VoiceForwardLayout.java']
    sources += [ROOT/'kernel'/name for name in ['RedPacketPolicy.java','RedPacketEngine.java','RedPacketNative.java','RedPacketRuntime.java','RedPacketSettings.java','ReceiptConfig.java','ReceiptStore.java','ReceiptTargets.java','ReceiptSettings.java','SettingsUi.java','PlusPanelLayout.java','RecallTipRuntime.java','RecallMarkStore.java','RecallMarkLayout.java','NativeSceneBus.java','TransferEngine.java','TransferNative.java','TransferRuntime.java']]
    if edition in ['plus','vip']:sources += [ROOT/'kernel/ImageRecallRuntime.java',ROOT/'kernel/RecallImageCache.java']
    run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-cp',CP,'-d',classes,*sources])
    jar=out/'classes.jar'
    with zipfile.ZipFile(jar,'w') as z:
        for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes).as_posix())
    run([JAVA,'-cp',LIBS/'d8.jar','com.android.tools.r8.D8','--min-api','24','--lib',LIBS/'android.jar','--classpath',LIBS/'xposed-api-82.jar','--output',dex,jar])
    return dex/'classes.dex'
source=(ROOT/'loader/Main.java').read_text(encoding='utf-8')
if args.signing_key:
    public=serialization.load_der_public_key(base64.b64decode(re.search(r'PUB_B64\s*=\s*"([^"]+)"',source).group(1)))
    signer=serialization.load_pem_private_key(args.signing_key.read_bytes(),password=None)
    assert public.public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)==signer.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
def const(name):return bytes(int(x.strip())&255 for x in re.search(r'\b'+name+r'\s*=\s*\{([^}]+)\}',source).group(1).split(',') if x.strip())
key,iv=const('K_KEY'),const('K_IV')
tests=OUT/'test-classes';tests.mkdir()
fixtures=list((ROOT/'tests/fixtures').rglob('*.java'))
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',tests,*fixtures,ROOT/'kernel/VoiceRecallRuntime.java',ROOT/'kernel/VoiceForwardRuntime.java',ROOT/'tests/VoiceRecallRuntimeTest.java',ROOT/'tests/VoiceForwardRuntimeTest.java'])
checks={'voice_callback_behavior':run([JAVA,'-cp',tests,'com.zafiro.wxentry.kernel.VoiceRecallRuntimeTest'])}
checks['voice_forward_behavior']=run([JAVA,'-cp',tests,'com.zafiro.wxentry.kernel.VoiceForwardRuntimeTest'])
print(checks['voice_callback_behavior'],flush=True)
print(checks['voice_forward_behavior'],flush=True)
sender_tests=OUT/'sender-test-classes';sender_tests.mkdir()
sender_fixtures=[p for p in fixtures if p.name!='VoiceForwardSender.java']
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',sender_tests,*sender_fixtures,ROOT/'kernel/VoiceForwardSender.java',ROOT/'kernel/VoiceForwardQueue.java',ROOT/'tests/VoiceForwardSenderTest.java'])
checks['modern_voice_sender_behavior']=run([JAVA,'-cp',sender_tests,'com.zafiro.wxentry.kernel.VoiceForwardSenderTest'])
print(checks['modern_voice_sender_behavior'],flush=True)
layout_tests=OUT/'layout-test-classes';layout_tests.mkdir()
layout_fixtures=[p for p in fixtures if p.name!='VoiceForwardLayout.java']
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',layout_tests,*layout_fixtures,ROOT/'kernel/VoiceForwardLayout.java',ROOT/'tests/VoiceForwardLayoutTest.java'])
checks['voice_marker_layout']=run([JAVA,'-cp',layout_tests,'com.zafiro.wxentry.kernel.VoiceForwardLayoutTest'])
print(checks['voice_marker_layout'],flush=True)
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',tests,*fixtures,ROOT/'kernel/VoiceForwardTargets.java',ROOT/'kernel/VoiceForwardQueue.java',ROOT/'tests/VoiceForwardQueueTest.java',ROOT/'tests/VoiceForwardTargetsTest.java'])
for cls,check_name in [('VoiceForwardQueueTest','batch_queue_behavior'),('VoiceForwardTargetsTest','chat_list_filters')]:
    checks[check_name]=run([JAVA,'-cp',tests,'com.zafiro.wxentry.kernel.'+cls]);print(checks[check_name],flush=True)
ui_tests=OUT/'settings-ui-test-classes';ui_tests.mkdir()
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',ui_tests,*list((ROOT/'tests/settings-ui-fixtures').rglob('*.java')),ROOT/'kernel/SettingsUi.java',ROOT/'tests/SettingsUiTest.java'])
checks['settings_switch_binding_and_layout']=run([JAVA,'-cp',ui_tests,'com.zafiro.wxentry.kernel.SettingsUiTest']);print(checks['settings_switch_binding_and_layout'],flush=True)
tip_tests=OUT/'recalltip-test-classes';tip_tests.mkdir()
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',tip_tests,*list((ROOT/'tests/recalltip-fixtures').rglob('*.java')),ROOT/'kernel/RecallTipRuntime.java',ROOT/'kernel/RecallMarkStore.java',ROOT/'kernel/ReceiptConfig.java',ROOT/'tests/RecallTipRuntimeTest.java'])
checks['recall_following_message_regression']=run([JAVA,'-cp',tip_tests,'com.zafiro.wxentry.kernel.RecallTipRuntimeTest']);print(checks['recall_following_message_regression'],flush=True)
mark_tests=OUT/'recallmark-ui-test-classes';mark_tests.mkdir()
mark_fixtures=[p for p in (ROOT/'tests/recalltip-fixtures').rglob('*.java') if p.name!='RecallMarkLayout.java']
mark_fixtures+=list((ROOT/'tests/recallmark-ui-fixtures').rglob('*.java'))
mark_fixtures+=[ROOT/'tests/fixtures/de/robv/android/xposed'/n for n in ['XposedBridge.java','XC_MethodHook.java']]
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',mark_tests,*mark_fixtures,*[ROOT/'kernel'/n for n in ['RecallTipRuntime.java','RecallMarkStore.java','RecallMarkLayout.java','ReceiptConfig.java']],ROOT/'tests/RecallMarkLayoutTest.java'])
checks['recall_inline_display_recycling']=run([JAVA,'-cp',mark_tests,'com.zafiro.wxentry.kernel.RecallMarkLayoutTest']);print(checks['recall_inline_display_recycling'],flush=True)
panel_tests=OUT/'plus-panel-test-classes';panel_tests.mkdir()
panel_fixtures=list((ROOT/'tests/recallmark-ui-fixtures/android').rglob('*.java'))
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',panel_tests,*panel_fixtures,ROOT/'kernel/PlusPanelLayout.java',ROOT/'tests/PlusPanelLayoutTest.java'])
checks['plus_panel_live_presence_and_recovery']=run([JAVA,'-cp',panel_tests,'com.zafiro.wxentry.kernel.PlusPanelLayoutTest']);print(checks['plus_panel_live_presence_and_recovery'],flush=True)
baseline=args.baseline_dir
for name in ['VoiceRecallRuntime.java','ImageRecallRuntime.java','RecallImageCache.java','VoiceForwardRuntime.java','VoiceForwardSender.java','VoiceForwardPicker.java','VoiceForwardTargets.java','VoiceForwardQueue.java','VoiceForwardIcon.java','VoiceForwardLayout.java']:
    assert (ROOT/'kernel'/name).read_bytes()==(baseline/'kernel'/name).read_bytes()
for name in ['KernelDelivery.java','EditionPolicy.java','MenuEntryDispatch.java']:
    assert (ROOT/'loader'/name).read_bytes()==(baseline/'loader'/name).read_bytes()
checks['v212_voice_image_sources_and_delivery_policy_preserved']=True
redtests=OUT/'redpacket-test-classes';redtests.mkdir()
redfixtures=list((ROOT/'tests/redpacket-fixtures').rglob('*.java'))
redfixtures += [ROOT/'tests/fixtures/de/robv/android/xposed'/n for n in ['XposedBridge.java','XC_MethodHook.java']]
redsources=[ROOT/'kernel/ReceiptConfig.java',ROOT/'kernel/NativeSceneBus.java',ROOT/'kernel/RedPacketPolicy.java',ROOT/'tests/RedPacketPolicyTest.java',ROOT/'kernel/RedPacketEngine.java',ROOT/'kernel/RedPacketNative.java',ROOT/'tests/RedPacketEngineTest.java',ROOT/'tests/RedPacketNativeTest.java']
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',redtests,*redfixtures,*redsources])
for cls,name in [('RedPacketPolicyTest','redpacket_premium_policy'),('RedPacketEngineTest','redpacket_state_behavior'),('RedPacketNativeTest','redpacket_native_adapter')]:
    checks[name]=run([JAVA,'-cp',redtests,'com.zafiro.wxentry.kernel.'+cls]);print(checks[name],flush=True)
runtime_tests=OUT/'runtime-test-classes';runtime_tests.mkdir()
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',runtime_tests,*redfixtures,*list((ROOT/'tests/runtime-fixtures').rglob('*.java')),*list((ROOT/'tests/transfer-fixtures').rglob('*.java')),*redsources,*[ROOT/'kernel'/n for n in ['RedPacketRuntime.java','ReceiptStore.java','ReceiptTargets.java','VoiceForwardTargets.java','TransferEngine.java','TransferNative.java','TransferRuntime.java']],ROOT/'tests/fixtures/android/database/Cursor.java',ROOT/'tests/RedPacketRuntimeTest.java',ROOT/'tests/ReceiptTest.java',ROOT/'tests/TransferTest.java'])
checks['redpacket_settings_reply_flow']=run([JAVA,'-Dwxentry.test.dir='+str(OUT/'runtime-test-data'),'-cp',runtime_tests,'com.zafiro.wxentry.kernel.RedPacketRuntimeTest'])
print(checks['redpacket_settings_reply_flow'],flush=True)
for cls,name in [('ReceiptTest','receipt_lists_seconds_and_migration'),('TransferTest','transfer_native_receive_flow')]:
    checks[name]=run([JAVA,'-Dwxentry.test.dir='+str(OUT/cls),'-cp',runtime_tests,'com.zafiro.wxentry.kernel.'+cls]);print(checks[name],flush=True)
if args.native_apk:
    run([JAVAC,'-encoding','UTF-8','-cp',LIBS/'jadx-all.jar','-d',tests,ROOT/'tests/NativeVoiceContractCheck.java',ROOT/'tests/NativeRedPacketContractCheck.java',ROOT/'tests/NativeTransferContractCheck.java',ROOT/'tests/NativeRecallTipContractCheck.java'])
    for cls,name in [('NativeVoiceContractCheck','native_voice_contracts'),('NativeRedPacketContractCheck','native_redpacket_contracts'),('NativeTransferContractCheck','native_transfer_contracts'),('NativeRecallTipContractCheck','native_recall_message_allocator')]:
        checks[name]=run([JAVA,'-cp',str(tests)+os.pathsep+str(LIBS/'jadx-all.jar'),cls,args.native_apk]);print(checks[name],flush=True)
run([JAVAC,'-encoding','UTF-8','-cp',LIBS/'jadx-all.jar','-d',tests,ROOT/'tests/DexEditionCheck.java'])
report={'version':'230','apk_target_version':'1.91','checks':checks,'editions':{},'phone_tested':False,'production_deployed':False,'signed':bool(args.signing_key)}
if args.native_apk:report['native_apk_sha256']=hashlib.sha256(args.native_apk.read_bytes()).hexdigest()
for edition in ['free','plus','vip']:
    dex=compile_dex(edition);plain=dex.read_bytes();digest=hashlib.sha256(plain).hexdigest()
    message=run([JAVA,'-cp',str(tests)+os.pathsep+str(LIBS/'jadx-all.jar'),'DexEditionCheck',dex,edition])
    if not args.signing_key:
        report['editions'][edition]={'dex':str(dex),'sha256':digest,'bytes':len(plain),'check':message,'signature_verified':False}
        print(message,flush=True)
        continue
    pad=PKCS7(128).padder();padded=pad.update(plain)+pad.finalize()
    enc=Cipher(algorithms.AES(key),modes.CBC(iv)).encryptor();encrypted=enc.update(padded)+enc.finalize()
    signed=('kernel-v2|'+edition+'|230|'+digest).encode()
    sig=signer.sign(signed,padding.PKCS1v15(),hashes.SHA256())
    public.verify(sig,signed,padding.PKCS1v15(),hashes.SHA256())
    dec=Cipher(algorithms.AES(key),modes.CBC(iv)).decryptor();decrypted=dec.update(encrypted)+dec.finalize()
    unpad=PKCS7(128).unpadder();assert unpad.update(decrypted)+unpad.finalize()==plain
    publish=OUT/'server-release/kernels'/edition;publish.mkdir(parents=True)
    (publish/'kernel.enc').write_bytes(encrypted)
    (publish/'kernel.json').write_text(json.dumps({'protocol':2,'edition':edition,'v':'230','sha256':digest,'enc_sha256':hashlib.sha256(encrypted).hexdigest(),'sig':base64.b64encode(sig).decode()},indent=2)+'\n')
    if edition=='free':
        legacy_signature=signer.sign(('230|'+digest).encode(),padding.PKCS1v15(),hashes.SHA256())
        public.verify(legacy_signature,('230|'+digest).encode(),padding.PKCS1v15(),hashes.SHA256())
        (publish/'legacy.json').write_text(json.dumps({'v':'230','sha256':digest,'sig':base64.b64encode(legacy_signature).decode()},indent=2)+'\n')
    report['editions'][edition]={'dex':str(dex),'publish':str(publish),'sha256':digest,'bytes':len(plain),'check':message,'signature_verified':True}
    print(message,flush=True)
assert len(set(x['sha256'] for x in report['editions'].values()))==3
(ROOT/'build-local/result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('Three kernels built and verified; signed='+str(bool(args.signing_key))+'; no APK build or deployment.',flush=True)
