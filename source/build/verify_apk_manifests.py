"""Check actual APK/manifest pairs against Main.java's update signature contract; never signs or deploys."""
from pathlib import Path
import argparse, json, os, re, subprocess, tempfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java-bin', type=Path, required=True)
parser.add_argument('--manifest-dir', type=Path, action='append', required=True,
                    help='Directory containing an actual apk.json and wxentry.apk; repeat for rollback')
parser.add_argument('--result', type=Path)
args = parser.parse_args()
main = (ROOT/'loader/Main.java').read_text(encoding='utf-8')
assert 'verifySig(v + "|" + name + "|" + sha, sig)' in main, 'Reinspect loader protocol before updating this contract test'
public = re.search(r'PUB_B64\s*=\s*"([^"]+)"', main).group(1)
tuples = []
versions = []
for directory in args.manifest_dir:
    meta = json.loads((directory/'apk.json').read_text(encoding='utf-8'))
    tuples.extend([meta['v'], meta['name'], meta['sha256'], meta['sig'], str((directory/'wxentry.apk').resolve())])
    versions.append(meta['name'])
suffix = '.exe' if os.name == 'nt' else ''
def run(command):
    result = subprocess.run([str(x) for x in command], capture_output=True, timeout=90)
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', 'replace')[-1600:])
    return result.stdout.decode('utf-8', 'replace').strip()
with tempfile.TemporaryDirectory(prefix='wxentry-apk-contract-') as temp:
    run([args.java_bin/('javac'+suffix), '-encoding', 'UTF-8', '-d', temp, ROOT/'tests/ApkManifestContractTest.java'])
    output = run([args.java_bin/('java'+suffix), '-cp', temp, 'ApkManifestContractTest', public, *tuples])
assert output == 'PASS '+str(4*len(versions))+' APK manifest protocol and delivery checks'
if args.result:
    args.result.write_text(json.dumps({'checks':output, 'versions':versions, 'loader_signature_protocol':'v|name|sha256'}, indent=2)+'\n', encoding='utf-8')
print(output)
