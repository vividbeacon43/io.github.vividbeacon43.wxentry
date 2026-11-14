"""Check public source boundaries and the signed APK before release."""
import argparse
from pathlib import Path, PurePosixPath
from zipfile import ZipFile
import re

PAID = re.compile(
    r'(?:Kernel_(?:vip|plus)|PaidTabletEntry|TabletModeHooks|TabletModeStore|TabletLoginEntry|'
    r'ExperimentalUi|WechatRestartPlan|RedPacket(?:Native|Runtime|Engine|Settings|Policy)|'
    r'Transfer(?:Runtime|Native|Engine)|Sns(?:Schedule|Forward|Ad|LocalRuntime|LocalNative)|'
    r'ImageRecallRuntime|RecallImageCache|CallToolsRuntime|ReadInferenceRuntime|ReadReplyPolicy)'
)
SECRET = re.compile(r'(?i)(?:^|/)(?:server|secrets|private)(?:/|$)|'
                    r'(?:private\.pem|\.jks|\.p12|\.sqlite3|admin\.token|bindings\.json|codes\.txt)$')


def check_apk(path):
    with ZipFile(path) as archive:
        if archive.testzip():
            raise ValueError('APK CRC check failed')
        entries = [name for name in archive.namelist() if re.fullmatch(r'classes\d*\.dex', name)]
        if set(entries) != {'classes.dex', 'classes2.dex'}:
            raise ValueError('Unexpected APK DEX inventory')
        for entry in entries:
            data = archive.read(entry)
            classes = re.findall(rb'Lcom/zafiro/wxentry/[A-Za-z0-9/$]+;', data)
            blocked = [item.decode() for item in classes if PAID.match(item.decode().rsplit('/', 1)[-1])]
            if blocked:
                raise ValueError(f'Paid runtime in {entry}: {blocked[0]}')


def check_source(path):
    with ZipFile(path) as archive:
        if archive.testzip():
            raise ValueError('Source CRC check failed')
        for raw in archive.namelist():
            name = PurePosixPath(raw)
            if name.is_absolute() or '..' in name.parts or '\\' in raw or SECRET.search(raw):
                raise ValueError(f'Unsafe public archive path: {raw}')
            if PAID.match(name.name):
                raise ValueError(f'Paid source in public archive: {raw}')
            if name.suffix not in ('.java', '.py', '.md', '.txt', ''):
                raise ValueError(f'Unexpected public archive file type: {raw}')
            if name.suffix in ('.java', '.py', '.md', '.txt') and re.search(rb'(?m)^-----BEGIN (?:[A-Z0-9]+ )?PRIVATE KEY-----\s*$', archive.read(raw)):
                raise ValueError(f'Private key in public archive: {raw}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--public-source', type=Path)
    args = parser.parse_args()
    check_apk(args.apk)
    if args.public_source:
        check_source(args.public_source)
    print('Public release boundary verified')
