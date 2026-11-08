"""Sign 2.03 using the original certificate and prepare a forward-version APK rollback."""
from pathlib import Path
import ast,struct,subprocess,json,zipfile,hashlib,re,sys,uuid,urllib.request
R=Path(__file__).resolve().parents[1];W=R.parent;OLD=Path(r'C:\Users\Q\Documents\Codex\2026-11-03\new-chat\work')
JAVA=Path(r'C:\Users\Q\Documents\Codex\2026-10-22\ban\work\toolchain\jdk\jdk-17.0.20.1+1\bin')
BT=Path(r'C:\Users\Q\Documents\Codex\2026-10-22\ban\work\toolchain\build-tools\android-15')
OUT=R/'build-apk'/('run-'+uuid.uuid4().hex[:10]);OUT.mkdir(parents=True);OUTPUTS=W.parent/'outputs'
sys.stdout.reconfigure(encoding='utf-8')
sha=lambda b:hashlib.sha256(b).hexdigest()
def run(args,stage):
 p=subprocess.run([str(a) for a in args],stdin=subprocess.DEVNULL,capture_output=True,timeout=180)
 if p.returncode:
  (OUT/(stage+'-private-error.log')).write_bytes(p.stdout+p.stderr);raise RuntimeError(stage+' failed; private diagnostic saved')
 return p.stdout.decode('utf-8','replace')
baseline_dir=Path(json.loads((OLD/'voice-recall-fix/latest.json').read_text(encoding="utf-8"))['snapshot']);baseline=baseline_dir/'wxentry.apk'
summary=json.loads((baseline_dir/'summary.json').read_text(encoding="utf-8"))['files'];assert sha(baseline.read_bytes())==summary['wxentry.apk']['sha256']
signing=OLD/'signing-tool-private/wxentry-sign-tool'
for line in (signing/'SHA256SUMS').read_text(encoding="utf-8").splitlines():
 if not line.strip():continue
 digest,name=line.split(None,1);path=(signing/name.lstrip('*').strip()).resolve();assert path.is_relative_to(signing.resolve()) and sha(path.read_bytes())==digest
expected=re.search(r'EXPECTED_SIG\s*=\s*"([a-f0-9]+)"',(R/'loader/Main.java').read_text(encoding='utf-8')).group(1)
cert=run([JAVA/'java.exe','-jar',BT/'lib/apksigner.jar','verify','--print-certs',baseline],'baseline-cert')
assert re.search('certificate SHA-256 digest: '+expected,cert)
alias=re.search(r'--ks-key-alias\s+[\'"]?([A-Za-z0-9_.-]+)',(signing/'sign_apk.sh').read_text(encoding="utf-8")).group(1)
source=ast.parse((OLD/'wxentry-hotupdate-v194/build/repack_apk.py').read_text(encoding='utf-8'))
function=next(n for n in source.body if isinstance(n,ast.FunctionDef) and n.name=='patch_manifest')
patch=ast.unparse(function).replace('def patch_manifest(raw):','def patch_manifest(raw,version_code,version_name):').replace('value != 84','value != 88').replace("attr + 16, 85)","attr + 16, version_code)").replace("('1.84', '1.85')","('1.88', version_name)").replace("'1.85'.encode(codec)","version_name.encode(codec)")
assert 'version_code)' in patch and 'version_name.encode' in patch
exec(compile(patch,'manifest-patcher','exec'),globals())
build=json.loads((R/'build-local/result.json').read_text(encoding="utf-8"));loader=json.loads((R/'build-loader/result.json').read_text(encoding="utf-8"))
# Forward-version rollback uses the exact stable loader logic (2.01) with 2.04 version fields.
rollback_loader=json.loads((W/'rollback-loader204/build-loader/result.json').read_text(encoding="utf-8"))
old_build=json.loads((W/'wxentry-v242/build-local/result.json').read_text(encoding="utf-8"))
keypass=OUT/'key-password-private.txt';keypass.write_bytes((signing/'pass.txt').read_bytes())
try:
 reports={}
 for version,name,ld,kernel in [(103,'2.03',Path(loader['loader_dex']),Path(build['editions']['free']['dex'])),(104,'2.04',Path(rollback_loader['loader_dex']),Path(old_build['editions']['free']['dex']))]:
  raw=OUT/(name+'-raw.apk');aligned=OUT/(name+'-unsigned.apk');signed=OUTPUTS/('wxentry-'+name+('-transfer-free-rp.apk' if version==103 else '-rollback-v242.apk'))
  with zipfile.ZipFile(baseline) as src,zipfile.ZipFile(raw,'w',zipfile.ZIP_DEFLATED) as dst:
   for info in src.infolist():
    if info.filename.startswith('META-INF/') or re.fullmatch(r'classes[0-9]*\.dex',info.filename):continue
    data=src.read(info.filename)
    if info.filename=='AndroidManifest.xml':data=patch_manifest(data,version,name)
    dst.writestr(info,data)
   dst.write(ld,'classes.dex');dst.write(kernel,'classes2.dex')
   if version in [103,104]:
    with zipfile.ZipFile(W/'tools/libs/jadx-all.jar') as deps:dst.writestr('assets/third-party/Apache-2.0.txt',deps.read('META-INF/LICENSE'))
    dst.write(R/'licenses/smali-NOTICE.txt','assets/third-party/smali-NOTICE.txt')
    dst.writestr('assets/third-party/NOTICE.txt','Includes a minimized dexlib2 (Google smali, BSD 3-Clause) and Guava (Google, Apache 2.0) for rewriting runtime DEX indexes.\nSources: https://github.com/google/smali and https://github.com/google/guava\n')
  run([BT/'zipalign.exe','-f','-p','4',raw,aligned],'zipalign-'+name)
  run([JAVA/'java.exe','-jar',BT/'lib/apksigner.jar','sign','--ks',signing/'wxentry2.jks','--ks-pass','file:'+str(signing/'pass.txt'),'--key-pass','file:'+str(keypass),'--ks-key-alias',alias,'--v1-signing-enabled','true','--v2-signing-enabled','true','--v3-signing-enabled','true','--out',signed,aligned],'sign-'+name)
  verify=run([JAVA/'java.exe','-jar',BT/'lib/apksigner.jar','verify','--min-sdk-version','21','--verbose','--print-certs',signed],'verify-'+name)
  assert re.search('certificate SHA-256 digest: '+expected,verify)
  for scheme in ['v1','v2','v3']:assert re.search('Verified using '+scheme+r' scheme[^\r\n]*:\s*true',verify)
  run([BT/'zipalign.exe','-c','-p','4',signed],'check-align-'+name)
  badging=run([BT/'aapt.exe','dump','badging',signed],'manifest-'+name).splitlines()[0];assert "versionCode='"+str(version)+"'" in badging and "versionName='"+name+"'" in badging
  with zipfile.ZipFile(signed) as z:assert z.testzip() is None;assert z.read('classes.dex')==ld.read_bytes() and z.read('classes2.dex')==kernel.read_bytes()
  reports[name]={'file':str(signed),'versionCode':version,'versionName':name,'sha256':sha(signed.read_bytes()),'bytes':signed.stat().st_size,'same_signer':True,'v1_v2_v3_verified':True,'loader_sha256':sha(ld.read_bytes()),'embedded_free_sha256':sha(kernel.read_bytes()),'phone_tested':False}
 (R/'build-apk/result.json').write_text(json.dumps(reports,indent=2)+'\n',encoding='utf-8');print(json.dumps(reports,indent=2))
finally:keypass.unlink(missing_ok=True)
