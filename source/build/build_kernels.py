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
    sources += [ROOT/'kernel'/name for name in ['RedPacketUi.java','RedPacketUiNative.java','RedPacketPolicy.java','RedPacketEngine.java','RedPacketNative.java','RedPacketRuntime.java','RedPacketSettings.java','ReceiptConfig.java','ReceiptStore.java','ReceiptTargets.java','ReceiptSettings.java','MomentsConfig.java','MomentsSettings.java','SnsLocalData.java','SnsLocalSelection.java','SnsLocalStore.java','SnsLocalSettings.java','SettingsUi.java','WechatAppearance.java','MenuPalette.java','FeatureMenuUi.java','PlusPanelLayout.java','RecallTipRuntime.java','RecallMarkStore.java','RecallMarkLayout.java','NativeSceneBus.java','MediaMessagePolicy.java','RecallMediaCache.java','MediaNative.java','MediaRecallRuntime.java','OriginalImageRuntime.java','ScanCameraRuntime.java','TransferEngine.java','TransferNative.java','TransferRuntime.java']]
    if edition in ['plus','vip']:sources += [ROOT/'kernel/CallToolsRuntime.java',ROOT/'kernel/ImageRecallRuntime.java',ROOT/'kernel/RecallImageCache.java',*[ROOT/'kernel'/n for n in ['SnsForwardCard.java','SnsForwardDraft.java','SnsForwardFiles.java','SnsForwardComposer.java','SnsForwardNative.java','SnsForwardRuntime.java','SnsAdPolicy.java','SnsAdRuntime.java','SnsLocalNative.java','SnsLocalRuntime.java','SnsLocalPicker.java','SnsLocalPanel.java','SnsLocalProjection.java','SnsLocalSurfaces.java','SnsLocalGallery.java','SnsLocalTimes.java','SnsLocalTimesStore.java']]]
    if edition=='vip':sources += sorted((ROOT/'kernel').glob('SnsSchedule*.java'))
    run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-cp',CP,'-d',classes,*sources])
    jar=out/'classes.jar'
    with zipfile.ZipFile(jar,'w') as z:
        for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes).as_posix())
    run([JAVA,'-cp',LIBS/'d8.jar','com.android.tools.r8.D8','--min-api','24','--lib',LIBS/'android.jar','--classpath',LIBS/'xposed-api-82.jar','--output',dex,jar])
    return dex/'classes.dex'
game_tests=OUT/'game-emoji-tests';game_tests.mkdir()
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',game_tests,ROOT/'kernel/GameEmojiData.java',ROOT/'tests/GameEmojiDataTest.java'])
game_queue_result=run([JAVA,'-cp',game_tests,'com.zafiro.wxentry.kernel.GameEmojiDataTest']);print(game_queue_result,flush=True)
source=(ROOT/'loader/Main.java').read_text(encoding='utf-8')
apk_version=re.search(r'MY_VN\s*=\s*"([^"]+)"',source).group(1)
apk_code=int(re.search(r'MY_VC\s*=\s*(\d+)',source).group(1))
versions={re.search(r'\bBUILD\s*=\s*"(\d+)"',p.read_text(encoding='utf-8')).group(1)
          for p in (ROOT/'kernel').glob('Kernel_*.java')}
assert len(versions)==1, 'Kernel editions must share a release number'
version=versions.pop()
if args.signing_key:
    public=serialization.load_der_public_key(base64.b64decode(re.search(r'PUB_B64\s*=\s*"([^"]+)"',source).group(1)))
    signer=serialization.load_pem_private_key(args.signing_key.read_bytes(),password=None)
    assert public.public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)==signer.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
def const(name):return bytes(int(x.strip())&255 for x in re.search(r'\b'+name+r'\s*=\s*\{([^}]+)\}',source).group(1).split(',') if x.strip())
key,iv=const('K_KEY'),const('K_IV')
tests=OUT/'test-classes';tests.mkdir()
fixtures=list((ROOT/'tests/fixtures').rglob('*.java'))
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',tests,*fixtures,ROOT/'kernel/VoiceRecallRuntime.java',ROOT/'kernel/VoiceForwardRuntime.java',ROOT/'tests/VoiceRecallRuntimeTest.java',ROOT/'tests/VoiceForwardRuntimeTest.java'])
checks={'game_outcome_queue':game_queue_result,'voice_callback_behavior':run([JAVA,'-cp',tests,'com.zafiro.wxentry.kernel.VoiceRecallRuntimeTest'])}
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
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',ui_tests,*list((ROOT/'tests/settings-ui-fixtures').rglob('*.java')),ROOT/'kernel/SettingsUi.java',ROOT/'kernel/WechatAppearance.java',ROOT/'kernel/MenuPalette.java',ROOT/'tests/SettingsUiTest.java'])
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
tablet_tests=OUT/'tablet-mode-test-classes';tablet_tests.mkdir()
tablet_fixtures=list((ROOT/'tests/tablet-fixtures').rglob('*.java'))+[ROOT/'tests/runtime-fixtures/android/util/AtomicFile.java']+[ROOT/'tests/fixtures/de/robv/android/xposed'/n for n in ['XposedBridge.java','XC_MethodHook.java']]
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',tablet_tests,*tablet_fixtures,ROOT/'loader/TabletModeStore.java',ROOT/'loader/TabletModeHooks.java',ROOT/'loader/WechatRestartPlan.java',ROOT/'tests/TabletModeTest.java',ROOT/'tests/WechatRestartPlanTest.java'])
checks['tablet_early_boot_and_disable']=run([JAVA,'-cp',tablet_tests,'com.zafiro.wxentry.TabletModeTest',OUT]);print(checks['tablet_early_boot_and_disable'],flush=True)
checks['tablet_restart_scheduling']=run([JAVA,'-cp',tablet_tests,'com.zafiro.wxentry.WechatRestartPlanTest']);print(checks['tablet_restart_scheduling'],flush=True)
paid_tests=OUT/'tablet-paid-test-classes';paid_tests.mkdir()
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',paid_tests,*[ROOT/'loader'/n for n in ['KernelDelivery.java','EditionPolicy.java','TabletEntitlement.java']],ROOT/'tests/TabletEntitlementTest.java'])
checks['tablet_signed_paid_entitlement']=run([JAVA,'-cp',paid_tests,'com.zafiro.wxentry.TabletEntitlementTest',OUT]);print(checks['tablet_signed_paid_entitlement'],flush=True)
media_tests=OUT/'media-cache-test-classes';media_tests.mkdir()
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',media_tests,ROOT/'kernel/MediaMessagePolicy.java',ROOT/'kernel/RecallMediaCache.java',ROOT/'tests/RecallMediaCacheTest.java'])
checks['media_cache_and_original_policy']=run([JAVA,'-cp',media_tests,'com.zafiro.wxentry.kernel.RecallMediaCacheTest',OUT]);print(checks['media_cache_and_original_policy'],flush=True)
media_runtime_tests=OUT/'media-runtime-test-classes';media_runtime_tests.mkdir()
media_fixtures=list((ROOT/'tests/media-fixtures').rglob('*.java'))
media_fixtures += [ROOT/'tests/fixtures'/n for n in ['w41/u.java','com/tencent/mm/modelbase/p0.java','de/robv/android/xposed/XposedBridge.java']]
media_fixtures += [ROOT/'tests/runtime-fixtures/android/os'/n for n in ['Handler.java','Looper.java','SystemClock.java']]
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',media_runtime_tests,*media_fixtures,*[ROOT/'kernel'/n for n in ['MediaMessagePolicy.java','RecallMediaCache.java','MediaRecallRuntime.java','OriginalImageRuntime.java','ReceiptConfig.java']],ROOT/'tests/MediaRuntimeBehaviorTest.java'])
checks['media_revoke_original_queue_behavior']=run([JAVA,'-cp',media_runtime_tests,'com.zafiro.wxentry.kernel.MediaRuntimeBehaviorTest',OUT]);print(checks['media_revoke_original_queue_behavior'],flush=True)
schedule_tests=OUT/'schedule-test-classes';schedule_tests.mkdir()
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',schedule_tests,ROOT/'kernel/SnsScheduleData.java',ROOT/'kernel/SnsScheduleEngine.java',ROOT/'tests/SnsScheduleEngineTest.java'])
checks['vip_scheduled_moments_journal']=run([JAVA,'-cp',schedule_tests,'com.zafiro.wxentry.kernel.SnsScheduleEngineTest']);print(checks['vip_scheduled_moments_journal'],flush=True)
schedule_store_tests=OUT/'schedule-store-test-classes';schedule_store_tests.mkdir()
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',schedule_store_tests,*list((ROOT/'tests/schedule-fixtures').rglob('*.java')),ROOT/'kernel/ReceiptConfig.java',ROOT/'kernel/SnsScheduleData.java',ROOT/'kernel/SnsScheduleEngine.java',ROOT/'kernel/SnsScheduleStore.java',ROOT/'tests/SnsScheduleStoreTest.java'])
checks['vip_scheduled_moments_atomic_store']=run([JAVA,'-cp',schedule_store_tests,'com.zafiro.wxentry.kernel.SnsScheduleStoreTest',OUT]);print(checks['vip_scheduled_moments_atomic_store'],flush=True)
wake_tests=OUT/'schedule-wake-test-classes';wake_tests.mkdir()
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',wake_tests,*list((ROOT/'tests/schedule-wake-fixtures').rglob('*.java')),ROOT/'kernel/SnsScheduleData.java',ROOT/'kernel/SnsScheduleEngine.java',ROOT/'kernel/SnsScheduleWake.java',ROOT/'tests/SnsScheduleWakeTest.java'])
checks['vip_screen_off_prewake']=run([JAVA,'-cp',wake_tests,'com.zafiro.wxentry.kernel.SnsScheduleWakeTest']);print(checks['vip_screen_off_prewake'],flush=True)
moments_tests=OUT/'moments-feature-test-classes';moments_tests.mkdir()
local_tests=OUT/'sns-local-test-classes';local_tests.mkdir()
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',local_tests,*list((ROOT/'tests/sns-local-fixtures').rglob('*.java')),*[ROOT/'kernel'/n for n in ['SnsLocalData.java','SnsLocalSelection.java','SnsLocalStore.java','SnsLocalProjection.java','SnsLocalTimes.java','SnsLocalTimesStore.java','ReceiptConfig.java']],ROOT/'tests/SnsLocalProjectionTest.java',ROOT/'tests/SnsLocalTimesTest.java',ROOT/'tests/SnsLocalDataTest.java',ROOT/'tests/SnsLocalStoreTest.java',ROOT/'tests/SnsLocalSelectionTest.java'])
for cls,name in [('SnsLocalDataTest','local_moments_immutable_codec'),('SnsLocalStoreTest','local_moments_private_accounts_and_retention'),('SnsLocalSelectionTest','local_moments_all_and_random_selection'),('SnsLocalProjectionTest','local_moments_cross_surface_projection'),('SnsLocalTimesTest','local_moments_stable_random_times')]:
    checks[name]=run([JAVA,'-cp',local_tests,'com.zafiro.wxentry.kernel.'+cls,OUT]);print(checks[name],flush=True)
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',moments_tests,*list((ROOT/'tests/moments-fixtures').rglob('*.java')),*[ROOT/'kernel'/n for n in ['MomentsConfig.java','MomentsSettings.java','SnsAdPolicy.java','SnsAdRuntime.java','ReceiptConfig.java']],ROOT/'tests/MomentsFeatureTest.java'])
checks['moments_switches_and_native_ad_filter']=run([JAVA,'-cp',moments_tests,'com.zafiro.wxentry.kernel.MomentsFeatureTest',OUT]);print(checks['moments_switches_and_native_ad_filter'],flush=True)
sns_tests=OUT/'sns-draft-test-classes';sns_tests.mkdir()
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',sns_tests,ROOT/'tests/sns-fixtures/android/content/Intent.java',*[ROOT/'kernel'/n for n in ['SnsForwardCard.java','SnsForwardDraft.java','SnsForwardFiles.java','SnsForwardComposer.java']],ROOT/'tests/SnsForwardDraftTest.java'])
checks['sns_forward_draft_preview_only']=run([JAVA,'-cp',sns_tests,'com.zafiro.wxentry.kernel.SnsForwardDraftTest',OUT]);print(checks['sns_forward_draft_preview_only'],flush=True)
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
red_ui_tests=OUT/'redpacket-ui-test-classes';red_ui_tests.mkdir()
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',red_ui_tests,*list((ROOT/'tests/redpacket-ui-fixtures').rglob('*.java')),ROOT/'kernel/RedPacketUi.java',ROOT/'kernel/RedPacketUiNative.java',ROOT/'kernel/ReceiptConfig.java',ROOT/'tests/RedPacketUiTest.java',ROOT/'tests/RedPacketUiNativeTest.java'])
checks['redpacket_late_boot_context_and_visible_launch']=run([JAVA,'-cp',red_ui_tests,'com.zafiro.wxentry.kernel.RedPacketUiTest']);print(checks['redpacket_late_boot_context_and_visible_launch'],flush=True)
checks['redpacket_native_complete_ui_entry']=run([JAVA,'-cp',red_ui_tests,'com.zafiro.wxentry.kernel.RedPacketUiNativeTest']);print(checks['redpacket_native_complete_ui_entry'],flush=True)
runtime_tests=OUT/'runtime-test-classes';runtime_tests.mkdir()
run([JAVAC,'-encoding','UTF-8','-source','8','-target','8','-nowarn','-d',runtime_tests,*redfixtures,*list((ROOT/'tests/runtime-fixtures').rglob('*.java')),*list((ROOT/'tests/transfer-fixtures').rglob('*.java')),*redsources,*[ROOT/'kernel'/n for n in ['RedPacketRuntime.java','ReceiptStore.java','ReceiptTargets.java','VoiceForwardTargets.java','TransferEngine.java','TransferNative.java','TransferRuntime.java']],ROOT/'tests/fixtures/android/database/Cursor.java',ROOT/'tests/RedPacketRuntimeTest.java',ROOT/'tests/ReceiptTest.java',ROOT/'tests/TransferTest.java',ROOT/'tests/ReceiptKeywordTest.java'])
checks['redpacket_settings_reply_flow']=run([JAVA,'-Dwxentry.test.dir='+str(OUT/'runtime-test-data'),'-cp',runtime_tests,'com.zafiro.wxentry.kernel.RedPacketRuntimeTest'])
print(checks['redpacket_settings_reply_flow'],flush=True)
for cls,name in [('ReceiptTest','receipt_lists_seconds_and_migration'),('TransferTest','transfer_native_receive_flow'),('ReceiptKeywordTest','vip_receipt_keyword_filter')]:
    checks[name]=run([JAVA,'-Dwxentry.test.dir='+str(OUT/cls),'-cp',runtime_tests,'com.zafiro.wxentry.kernel.'+cls]);print(checks[name],flush=True)
if args.native_apk:
    run([JAVAC,'-encoding','UTF-8','-cp',LIBS/'jadx-all.jar','-d',tests,ROOT/'tests/NativeVoiceContractCheck.java',ROOT/'tests/NativeRedPacketContractCheck.java',ROOT/'tests/NativeTransferContractCheck.java',ROOT/'tests/NativeRecallTipContractCheck.java',ROOT/'tests/NativeTabletContractCheck.java',ROOT/'tests/NativeMediaContractCheck.java',ROOT/'tests/NativeSnsForwardContractCheck.java',ROOT/'tests/NativeSnsAdContractCheck.java',ROOT/'tests/NativeRedPacketUiContractCheck.java',ROOT/'tests/NativeSnsLocalContractCheck.java',ROOT/'tests/NativeSnsScheduleContractCheck.java',ROOT/'tests/NativeGameEmojiContractCheck.java'])
    for cls,name in [('NativeVoiceContractCheck','native_voice_contracts'),('NativeRedPacketContractCheck','native_redpacket_contracts'),('NativeTransferContractCheck','native_transfer_contracts'),('NativeRecallTipContractCheck','native_recall_message_allocator'),('NativeTabletContractCheck','native_tablet_login_contracts'),('NativeMediaContractCheck','native_media_file_original_contracts'),('NativeSnsForwardContractCheck','native_sns_forward_contracts'),('NativeSnsAdContractCheck','native_sns_ad_contracts'),('NativeRedPacketUiContractCheck','native_complete_redpacket_ui_contracts'),('NativeSnsLocalContractCheck','native_local_moments_contracts'),('NativeSnsScheduleContractCheck','native_scheduled_moments_contracts'),('NativeGameEmojiContractCheck','native_game_emoji_contracts')]:
        checks[name]=run([JAVA,'-cp',str(tests)+os.pathsep+str(LIBS/'jadx-all.jar'),cls,args.native_apk]);print(checks[name],flush=True)
run([JAVAC,'-encoding','UTF-8','-cp',LIBS/'jadx-all.jar','-d',tests,ROOT/'tests/DexEditionCheck.java'])
report={'version':version,'apk_target_version':apk_version,'checks':checks,'editions':{},'phone_tested':False,'production_deployed':False,'signed':bool(args.signing_key)}
if args.native_apk:report['native_apk_sha256']=hashlib.sha256(args.native_apk.read_bytes()).hexdigest()
for edition in ['free','plus','vip']:
    dex=compile_dex(edition);plain=dex.read_bytes();digest=hashlib.sha256(plain).hexdigest()
    message=run([JAVA,'-cp',str(tests)+os.pathsep+str(LIBS/'jadx-all.jar'),'DexEditionCheck',dex,edition,version])
    if not args.signing_key:
        report['editions'][edition]={'dex':str(dex),'sha256':digest,'bytes':len(plain),'check':message,'signature_verified':False}
        print(message,flush=True)
        continue
    pad=PKCS7(128).padder();padded=pad.update(plain)+pad.finalize()
    enc=Cipher(algorithms.AES(key),modes.CBC(iv)).encryptor();encrypted=enc.update(padded)+enc.finalize()
    signed=('kernel-v2|'+edition+'|'+version+'|'+digest).encode()
    sig=signer.sign(signed,padding.PKCS1v15(),hashes.SHA256())
    public.verify(sig,signed,padding.PKCS1v15(),hashes.SHA256())
    dec=Cipher(algorithms.AES(key),modes.CBC(iv)).decryptor();decrypted=dec.update(encrypted)+dec.finalize()
    unpad=PKCS7(128).unpadder();assert unpad.update(decrypted)+unpad.finalize()==plain
    publish=OUT/'server-release/kernels'/edition;publish.mkdir(parents=True)
    (publish/'kernel.enc').write_bytes(encrypted)
    (publish/'kernel.json').write_text(json.dumps({'protocol':2,'edition':edition,'v':version,'min_apk_v':apk_code,'sha256':digest,'enc_sha256':hashlib.sha256(encrypted).hexdigest(),'sig':base64.b64encode(sig).decode()},indent=2)+'\n')
    if edition=='free':
        legacy_signature=signer.sign((version+'|'+digest).encode(),padding.PKCS1v15(),hashes.SHA256())
        public.verify(legacy_signature,(version+'|'+digest).encode(),padding.PKCS1v15(),hashes.SHA256())
        (publish/'legacy.json').write_text(json.dumps({'v':version,'sha256':digest,'sig':base64.b64encode(legacy_signature).decode()},indent=2)+'\n')
    report['editions'][edition]={'dex':str(dex),'publish':str(publish),'sha256':digest,'bytes':len(plain),'check':message,'signature_verified':True}
    print(message,flush=True)
assert len(set(x['sha256'] for x in report['editions'].values()))==3
(ROOT/'build-local/result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('Three kernels built and verified; signed='+str(bool(args.signing_key))+'; no APK build or deployment.',flush=True)

