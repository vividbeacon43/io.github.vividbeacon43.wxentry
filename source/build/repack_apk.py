"""Build an aligned, unsigned WXEntry APK from an official module APK."""
from pathlib import Path
import argparse
import json
import re
import struct
import subprocess
import tempfile
import zipfile


def patch_manifest(raw, base_code, base_name, new_code, new_name):
    data=bytearray(raw)
    if struct.unpack_from('<H',data,0)[0]!=3:raise ValueError('Not Android binary XML')
    pool=None;chunks=[];position=struct.unpack_from('<H',data,2)[0]
    while position<len(data):
        kind,header,size=struct.unpack_from('<HHI',data,position)
        if size<header or position+size>len(data):raise ValueError('Invalid XML chunk')
        chunks.append((position,kind))
        if kind==1:pool=(position,header)
        position+=size
    if pool is None:raise ValueError('Missing string pool')
    start,header=pool
    count,styles,flags,string_start=struct.unpack_from('<IIII',data,start+8)
    utf8=bool(flags&256)
    def length(at,short):
        if short:
            value=struct.unpack_from('<H',data,at)[0]
            if value&0x8000:return ((value&0x7fff)<<16)|struct.unpack_from('<H',data,at+2)[0],at+4
            return value,at+2
        value=data[at]
        if value&128:return ((value&127)<<8)|data[at+1],at+2
        return value,at+1
    def read(index):
        if not 0<=index<count:raise ValueError('Invalid XML string index')
        offset=struct.unpack_from('<I',data,start+header+index*4)[0]
        at=start+string_start+offset
        units,at=length(at,not utf8)
        if utf8:
            size,at=length(at,False);codec='utf-8'
        else:size=units*2;codec='utf-16-le'
        return bytes(data[at:at+size]).decode(codec),at,size,codec
    changed=set()
    for at,kind in chunks:
        if kind!=0x102:continue
        extension=at+16
        if read(struct.unpack_from('<I',data,extension+4)[0])[0]!='manifest':continue
        attr_start,attr_size,attr_count=struct.unpack_from('<HHH',data,extension+8)
        for i in range(attr_count):
            attr=extension+attr_start+i*attr_size
            ns,name,raw_index=struct.unpack_from('<III',data,attr)
            if ns==0xffffffff or read(ns)[0]!='http://schemas.android.com/apk/res/android':continue
            name=read(name)[0];value_type=data[attr+15];value=struct.unpack_from('<I',data,attr+16)[0]
            if name=='versionCode':
                if value_type not in (0x10,0x11) or value!=base_code:raise ValueError('Unexpected baseline versionCode')
                struct.pack_into('<I',data,attr+16,new_code);changed.add(name)
            elif name=='versionName':
                if value_type!=3:raise ValueError('Unexpected versionName representation')
                for index in {value,raw_index}-{0xffffffff}:
                    text,payload,size,codec=read(index)
                    if text not in (base_name,new_name):raise ValueError('Unexpected baseline versionName')
                    updated=new_name.encode(codec)
                    if len(updated)!=size:raise ValueError('Unsafe manifest size change')
                    data[payload:payload+size]=updated
                changed.add(name)
    if changed!={'versionCode','versionName'}:raise ValueError('Missing manifest version attributes')
    return bytes(data)


def run(*command):
    process = subprocess.run([str(item) for item in command], capture_output=True, timeout=120, check=True)
    return process.stdout.decode('utf-8', 'replace')


def badging(aapt, apk):
    info = run(aapt, 'dump', 'badging', apk).splitlines()[0]
    result = re.fullmatch(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'.*", info)
    if not result:
        raise ValueError('Cannot read APK version from AndroidManifest.xml')
    return result[1], int(result[2]), result[3]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-apk', type=Path, required=True)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--build-tools', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    loader = json.loads((root/'build-loader/result.json').read_text(encoding='utf-8'))
    kernels = json.loads((root/'build-local/result.json').read_text(encoding='utf-8'))
    new_code, new_name = loader['versionCode'], loader['versionName']
    if kernels['apk_target_version'] != new_name:
        raise ValueError('Rebuild kernels and loader for the same APK version')
    aapt, zipalign = (args.build_tools/name for name in ('aapt.exe','zipalign.exe'))
    output = args.output.resolve()
    if output == args.base_apk.resolve() or output.exists():
        raise ValueError('Choose a new output path for the unsigned APK')
    package, base_code, base_name = badging(aapt, args.base_apk)
    if package not in ('io.github.vividbeacon43.wxentry', 'com.zafiro.wxentry') or new_code <= base_code:
        raise ValueError('Expected an older WXEntry APK as the base')
    loader_source = (root/'loader/Main.java').read_text(encoding='utf-8')
    expected = re.search(r'EXPECTED_SIG\s*=\s*"([a-f0-9]+)"', loader_source)[1]
    cert = run(args.java, '-jar', args.build_tools/'lib/apksigner.jar', 'verify', '--print-certs', args.base_apk)
    if not re.search(r'certificate SHA-256 digest:\s*'+expected+r'\b', cert):
        raise ValueError('Base APK must use the official WXEntry signing certificate')

    output.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        'classes.dex': Path(loader['loader_dex']).read_bytes(),
        'classes2.dex': Path(kernels['editions']['free']['dex']).read_bytes(),
    }
    with tempfile.TemporaryDirectory(prefix='wxentry-apk-', dir=output.parent) as temp:
        raw = Path(temp)/'unaligned.apk'
        aligned = Path(temp)/'aligned.apk'
        with zipfile.ZipFile(args.base_apk) as old, zipfile.ZipFile(raw, 'w', zipfile.ZIP_DEFLATED) as new:
            for item in old.infolist():
                if item.filename.startswith('META-INF/') or re.fullmatch(r'classes[0-9]*\.dex', item.filename):
                    continue
                data = old.read(item)
                if item.filename == 'AndroidManifest.xml':
                    data = patch_manifest(data, base_code, base_name, new_code, new_name)
                new.writestr(item, data)
            for filename, data in payload.items():
                new.writestr(filename, data)
        run(zipalign, '-f', '-p', 4, raw, aligned)
        run(zipalign, '-c', '-p', 4, aligned)
        if badging(aapt, aligned) != (package, new_code, new_name):
            raise RuntimeError('Unexpected package name or version')
        with zipfile.ZipFile(aligned) as apk:
            if apk.testzip() or any(apk.read(name) != data for name, data in payload.items()):
                raise RuntimeError('APK payload validation failed')
        aligned.rename(output)
    print(output)


if __name__ == '__main__':
    main()
