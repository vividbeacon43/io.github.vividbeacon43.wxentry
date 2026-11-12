#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""wxentry 授权服务（仅标准库 + openssl 命令行）
监听 127.0.0.1:8099，由 Caddy 反代 /wx/* 过来。

v6 加固（2026-11-01）：诊断上传 /diag 改为 HMAC-SHA256 验签 + 时间戳防重放 +
  身份分级（wh 对不上 bindings.json 的进 diag-unverified 隔离目录），解压限长防炸弹。

v3 加固（2026-10-30）：
  - 每个连接 15s 超时、请求体最大 4KB（防慢速连接/慢速 body 耗尽线程）
  - 最多 32 个并发处理线程，超出直接 503（不排队、不崩）
  - /api.php 每 IP 20 次/60 秒（签名是 RSA 运算，最贵的一步）
  - codes.txt 按 mtime 缓存，不再每个请求都读盘
  - 支持 HEAD（Caddy/curl -I 探活）
接口：
  GET  /kernel.json      GET /kernel.enc      POST /api.php?a=activate {"code","wxid"}
"""
import base64, hashlib, hmac, json, math, os, re, subprocess, sys, tempfile, threading, time, zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BASE = os.path.dirname(os.path.abspath(__file__))
CODES = os.path.join(BASE, 'codes.txt')
BIND  = os.path.join(BASE, 'bindings.json')
KEY   = os.path.join(BASE, 'private.pem')
PORT  = int(os.environ.get('WX_PORT', '8099'))

SOCK_TIMEOUT   = 15      # 单连接 socket 超时（秒）
MAX_CONCURRENT = 32      # 并发处理线程上限
MAX_BODY       = 4096    # 请求体上限（字节）

# ---- 诊断上传（v6：HMAC 验签 + 身份分级 + 隔离目录）----
DIAG_MAX       = 512 * 1024   # 请求体上限
DIAG_MAX_TEXT  = 256 * 1024   # 解压后上限（防 gzip 炸弹：解压时就截断）
DIAG_DIR       = os.path.join(BASE, 'diag')             # 验签通过且身份对得上绑定 → 可信
DIAG_QUAR      = os.path.join(BASE, 'diag-unverified')  # 验签通过但身份不明 → 隔离，永不混放
DIAG_KEY       = b'wxentry-diag-v2-bddaea09a90e'         # 与客户端 Main.DIAG_KEY 完全一致
DIAG_TS_WIN    = 900          # 客户端时间戳容差（秒），挡重放
DIAG_RATE      = {}           # successful ordinary writes per IP: 30/24h, 20s gap
DIAG_WH_RATE   = {}           # successful ordinary writes per trust + wh
DIAG_SELFTEST_IP_RATE = {}     # successful selftests: 1/24h per IP
DIAG_SELFTEST_WH_RATE = {}     # and 1/24h per valid claimed wh
DIAG_ABUSE_RATE = {}           # separate attempt guard; not a storage budget
DIAG_ABUSE_LIMIT = 120
DIAG_ABUSE_WINDOW = 60
DIAG_KEEP      = 500          # 可信目录保留份数
DIAG_KEEP_QUAR = 300          # 隔离目录保留份数
DIAG_QUAR_DAYS = 30           # 隔离目录过期天数
DIAG_HEAD      = '=== wxentry diag ==='
DIAG_REASONS   = ('activate-fail', 'boot-fail', 'prev-boot', 'manual', 'crash', 'kernel-fail', 'selftest', 'realname-debug', 'realname-nomap', 'loader-broken')
_diag_lock = threading.Lock()
DIAG_SEEN      = {}           # (wh, sha256(正文)) -> ts，24h 内同一份内容只存一次（挡重放刷盘）
RL_MAX, RL_WIN = 20, 60  # /api.php 每 IP RL_WIN 秒内最多 RL_MAX 次

ACT_LOG = os.path.join(BASE, 'activate.log')


_who_rl = {}
_who_lock = threading.Lock()


def who_rate_ok(ip, window=60, limit=120):
    """whoami 是只读查询（token 还会缓存），单独给一条宽松的限流，避免和激活抢额度。"""
    now = time.time()
    with _who_lock:
        q = [t for t in _who_rl.get(ip, ()) if now - t < window]
        if len(q) >= limit:
            _who_rl[ip] = q
            return False
        q.append(now)
        _who_rl[ip] = q
        if len(_who_rl) > 4096:
            for k in [k for k, v in _who_rl.items() if not v or now - v[-1] > window]:
                _who_rl.pop(k, None)
        return True



def alog(wxid, code, result, ip=''):
    """激活尝试留痕：只记 wxid 后 6 位与码后 4 位，用来定位“码都不行”的客服问题。"""
    try:
        if os.path.exists(ACT_LOG) and os.path.getsize(ACT_LOG) > 2 * 1024 * 1024:
            os.replace(ACT_LOG, ACT_LOG + '.1')
    except OSError:
        pass
    try:
        with open(ACT_LOG, 'a', encoding='utf-8') as f:
            f.write('%s %-8s %-4s %-12s %s\n' % (time.strftime('%Y-%m-%d %H:%M:%S'),
                    (wxid or '-')[-8:], (code or '-')[-4:], result, ip or '-'))
    except Exception:
        pass



def sign(data: bytes) -> bytes:
    fd, path = tempfile.mkstemp()
    try:
        with os.fdopen(fd, 'wb') as f:
            f.write(data)
        out = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', KEY, path],
                             capture_output=True, timeout=10)
        if out.returncode != 0:
            raise RuntimeError(out.stderr.decode('utf-8', 'ignore'))
        return out.stdout
    finally:
        try:
            os.unlink(path)
        except OSError:
            pass


_codes_lock = threading.Lock()
_codes_cache = (None, set())

def _der(b, i):
    tag = b[i]; i += 1
    l = b[i]; i += 1
    if l & 0x80:
        n = l & 0x7f
        l = int.from_bytes(b[i:i + n], 'big'); i += n
    return tag, b[i:i + l], i + l


_pub_cache = [None]


def _rsa_pub():
    if _pub_cache[0]:
        return _pub_cache[0]
    with open(PUB, encoding='ascii') as f:
        pem = base64.b64decode(''.join(l for l in f.read().splitlines()
                                     if not l.startswith('-----')))
    _, spki, _ = _der(pem, 0)
    _, _, i = _der(spki, 0)
    _, bits, _ = _der(spki, i)
    _, rk, _ = _der(bits[1:], 0)
    _, n, i = _der(rk, 0)
    _, e, _ = _der(rk, i)
    _pub_cache[0] = (int.from_bytes(n, 'big'), int.from_bytes(e, 'big'))
    return _pub_cache[0]


_DIGEST_INFO = bytes.fromhex('3031300d060960864801650304020105000420')


def token_ok(token):
    """验授权凭证（RSA-SHA256）并返回 payload；无效一律 None。"""
    try:
        if not token or '.' not in token:
            return None
        p_b64, s_b64 = token.split('.', 1)
        payload = base64.b64decode(p_b64)
        sig = base64.b64decode(s_b64)
        n, e = _rsa_pub()
        k = (n.bit_length() + 7) // 8
        m = pow(int.from_bytes(sig, 'big'), e, n).to_bytes(k, 'big')
        h = hashlib.sha256(payload).digest()
        pad = b'\x00\x01' + b'\xff' * (k - 3 - len(_DIGEST_INFO) - len(h)) + b'\x00' + _DIGEST_INFO + h
        if m != pad:
            return None
        return json.loads(payload)
    except Exception:
        return None


def load_codes():
    """按 mtime 缓存：改 codes.txt 不用重启服务。"""
    global _codes_cache
    try:
        mt = os.path.getmtime(CODES)
    except OSError:
        return set()
    with _codes_lock:
        if _codes_cache[0] == mt:
            return _codes_cache[1]
        with open(CODES, encoding='utf-8') as f:
            s = {ln.strip() for ln in f if ln.strip()}
        _codes_cache = (mt, s)
        return s


class StoreError(Exception):
    """绑定库不可读 / 不可写。调用方必须返回 503，且不得改动原文件。"""


_bind_lock = threading.RLock()      # 读-改-写全程持锁（可重入，供内部复用）


def _bind_read_locked():
    """调用方必须已持有 _bind_lock。损坏一律抛 StoreError，绝不返回空库。"""
    if not os.path.exists(BIND):
        return {}
    try:
        with open(BIND, encoding='utf-8') as f:
            raw = f.read()
    except OSError as e:
        raise StoreError('read: %s' % e)
    if not raw.strip():
        return {}                    # 0 字节文件：首次创建时可以当空库
    try:
        d = json.loads(raw)
    except Exception as e:
        raise StoreError('parse: %s' % e)
    if not isinstance(d, dict):
        raise StoreError('shape: %s' % type(d).__name__)
    return d


def _bind_write_locked(d):
    """临时文件 + fsync + 原子替换；失败时删临时文件并抛 StoreError。"""
    tmp = '%s.tmp.%d.%d' % (BIND, os.getpid(), threading.get_ident())
    try:
        with open(tmp, 'w', encoding='utf-8') as f:
            json.dump(d, f, ensure_ascii=False)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, BIND)
    except Exception as e:
        try:
            os.unlink(tmp)
        except OSError:
            pass
        raise StoreError('write: %s' % e)
    try:
        dfd = os.open(os.path.dirname(BIND) or '.', os.O_RDONLY)
        try:
            os.fsync(dfd)
        finally:
            os.close(dfd)
    except OSError:
        pass


def bind_txn(fn):
    """把「读取 -> 判断 -> 修改 -> 提交」放在同一个事务里。
    fn(d) 的返回值原样返回；fn 抛异常则不提交；内容没变就不写盘。"""
    with _bind_lock:
        d = _bind_read_locked()
        before = json.dumps(d, sort_keys=True, ensure_ascii=False)
        out = fn(d)
        if json.dumps(d, sort_keys=True, ensure_ascii=False) != before:
            _bind_write_locked(d)
        return out


def load_bind():
    """只读快照。损坏时抛 StoreError，由调用方决定 503 还是隔离。"""
    with _bind_lock:
        return _bind_read_locked()


def save_bind(d):
    """兼容旧调用；新代码请用 bind_txn，避免读-改-写之间被插队。"""
    with _bind_lock:
        _bind_write_locked(d)


def bind_claim(code, wxid, prefer=False):
    """原子领取：('ok', rec) / ('other', None) / ('err', None)。"""
    def _fn(d):
        rec = d.get(code)
        if isinstance(rec, dict) and rec.get('wxid') and rec.get('wxid') != wxid:
            return 'other', None
        if not isinstance(rec, dict) or not rec.get('wxid'):
            d[code] = {'wxid': wxid, 'ts': int(time.time())}
            rec = d[code]
        if prefer:
            # Monotonic within the binding transaction, including re-activating an older owned code.
            rec['active_seq'] = max([int(v.get('active_seq') or 0) for v in d.values()
                                     if isinstance(v, dict) and v.get('wxid') == wxid] or [0]) + 1
        return 'ok', dict(rec)
    try:
        return bind_txn(_fn)
    except StoreError as e:
        sys.stderr.write('bind store error: %s\n' % e)
        return 'err', None


ADMIN = os.path.join(BASE, 'admin.token')
ADMIN_PATH = os.path.join(BASE, 'admin.path')
WISH = os.path.join(BASE, 'wishes.jsonl')     # 许愿收集（600，一行一条）
DISABLED = os.path.join(BASE, 'disabled.json')   # 远程停用：{wxid: {reason, at}}
REPLIES  = os.path.join(BASE, 'replies.json')    # 许愿回复：{wxid: {text, at}}
APPEALS  = os.path.join(BASE, 'appeals.jsonl')   # 申诉记录（一行一条）
ENTITLE  = os.path.join(BASE, 'entitlements.json')  # 手动按功能授权：{wxid: ["feat1"]}
PACKS    = os.path.join(BASE, 'packs.json')         # 功能包：{"free":[...],"pro":[...],"plus":[...],"vip":[...]}
CODE_TIER= os.path.join(BASE, 'code_tiers.json')    # 功能码等级：{code: "pro"}
TIER_ORDER = ['free', 'plus', 'vip']
TIER_NAME  = {'free': '免费版', 'plus': '高级版', 'vip': 'VIP 版'}
KERNEL_CHANNELS = tuple(TIER_ORDER)
IMAGE_FEATURE = 'antirevoke.image.pro'
_pol_lock = threading.Lock()
_appeal_rate = {}
PUB = os.path.join(BASE, 'public.pem')
WISH_MAX_CHARS = 500
WISH_PER_DAY = 1
WISH_IP_PER_HOUR = 10     # 里面的随机串 = 密径；缺文件 = 管理台彻底关闭
COOKIE = 'wxadm'
GEN_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'   # 无 0/O/1/I/L，避免手抄看错

_admin_cache = (None, None)
_admin_lock = threading.Lock()
_admin_fail = {}
_codes_wlock = threading.RLock()


def admin_token():
    """admin.token 不存在 = 管理台关闭（返回 None）。改文件即时生效。"""
    global _admin_cache
    try:
        mt = os.path.getmtime(ADMIN)
    except OSError:
        return None
    with _admin_lock:
        if _admin_cache[0] == mt:
            return _admin_cache[1]
        with open(ADMIN, encoding='utf-8') as f:
            t = f.read().strip()
        _admin_cache = (mt, t or None)
        return _admin_cache[1]


_path_cache = (None, None)


def admin_secret_path():
    """admin.path 里的一行随机串，例如 a/7XK2M...(24 位)。文件不存在 → None（管理台关闭）。"""
    global _path_cache
    try:
        mt = os.path.getmtime(ADMIN_PATH)
    except OSError:
        return None
    with _admin_lock:
        if _path_cache[0] == mt:
            return _path_cache[1]
        with open(ADMIN_PATH, encoding='utf-8') as f:
            v = f.read().strip().strip('/')
        _path_cache = (mt, v or None)
        return _path_cache[1]


def cookie_value(tok):
    import hashlib
    return hashlib.sha256(('wxentry-admin|' + tok).encode('utf-8')).hexdigest()[:40]


def cookie_ok(handler):
    want = admin_token()
    if not want:
        return False
    raw = handler.headers.get('Cookie') or ''
    got = ''
    for part in raw.split(';'):
        k, _, v = part.strip().partition('=')
        if k == COOKIE:
            got = v.strip()
            break
    return bool(got) and hmac.compare_digest(got, cookie_value(want))


def admin_authed(handler):
    """cookie 或 X-Admin-Token 头，认一个就行（头是给 curl 用的）。"""
    if cookie_ok(handler):
        return True
    want = admin_token()
    got = handler.headers.get('X-Admin-Token') or ''
    return bool(want) and hmac.compare_digest(got, want)


def admin_rate_ok(ip, window=600, limit=10):
    """管理接口失败次数限流：每 IP 10 分钟内最多 10 次失败。"""
    now = time.time()
    with _admin_lock:
        q = [t for t in _admin_fail.get(ip, ()) if now - t < window]
        if len(q) >= limit:
            _admin_fail[ip] = q
            return False
        q.append(now)
        _admin_fail[ip] = q
        return True


def admin_fail_clear(ip):
    with _admin_lock:
        _admin_fail.pop(ip, None)


def read_codes():
    if not os.path.exists(CODES):
        return []
    with open(CODES, encoding='utf-8') as f:
        return [ln.strip() for ln in f if ln.strip()]


def write_codes(lines):
    global _codes_cache
    with _codes_wlock:
        tmp = CODES + '.tmp'
        with open(tmp, 'w', encoding='utf-8') as f:
            f.write('\n'.join(lines) + ('\n' if lines else ''))
        os.replace(tmp, CODES)
    with _codes_lock:
        _codes_cache = (None, set())          # 让 load_codes() 下次重读


def admin_gen(n):
    with _codes_wlock:
        import secrets
        lines = read_codes()
        seen = set(lines)
        new = []
        while len(new) < n:
            c = 'WX-' + '-'.join(''.join(secrets.choice(GEN_ALPHABET) for _ in range(4)) for _ in range(3))
            if c not in seen:
                seen.add(c)
                new.append(c)
        write_codes(lines + new)
        return new


# ---- 2026-11-07 新用户自动发码（auto free grant） -------------------------
AUTO_FREE_CFG   = os.path.join(BASE, 'auto_free.json')
AUTO_FREE_STATE = os.path.join(BASE, 'auto_free_state.json')
AUTO_FREE_LOG   = os.path.join(BASE, 'auto_free.log')
_auto_lock = threading.Lock()


def _jload(path, default):
    try:
        with open(path, encoding='utf-8') as f:
            return json.load(f)
    except Exception:
        return default


def auto_free_policy():
    d = _jload(AUTO_FREE_CFG, {})
    if not isinstance(d, dict):
        d = {}

    def _n(k, v):
        try:
            return int(d.get(k, v))
        except Exception:
            return v
    return {'on': bool(d.get('on', True)),
            'daily': max(0, _n('daily', 200)),
            'ip_daily': max(0, _n('ip_daily', 5)),
            'wh_daily': max(0, _n('wh_daily', 1)),
            'tier': 'free'}


def auto_free_state():
    day = time.strftime('%Y-%m-%d')
    st = _jload(AUTO_FREE_STATE, {})
    if not isinstance(st, dict) or st.get('day') != day:
        st = {'day': day, 'total': 0, 'ips': {}, 'whs': {}}
    if not isinstance(st.get('ips'), dict):
        st['ips'] = {}
    if not isinstance(st.get('whs'), dict):
        st['whs'] = {}
    return st


def auto_free_take(ip, wh):
    """当天额度检查 + 记账（同一把锁内完成）。返回 (True, '') 或 (False, 原因)。"""
    with _auto_lock:
        pol = auto_free_policy()
        if not pol['on']:
            return False, 'off'
        st = auto_free_state()
        if pol['daily'] and int(st.get('total') or 0) >= pol['daily']:
            return False, 'daily'
        if ip and pol['ip_daily'] and int(st['ips'].get(ip) or 0) >= pol['ip_daily']:
            return False, 'ip'
        if wh and pol['wh_daily'] and int(st['whs'].get(wh) or 0) >= pol['wh_daily']:
            return False, 'wh'
        st['total'] = int(st.get('total') or 0) + 1
        if ip:
            st['ips'][ip] = int(st['ips'].get(ip) or 0) + 1
        if wh:
            st['whs'][wh] = int(st['whs'].get(wh) or 0) + 1
        try:
            tmp = AUTO_FREE_STATE + '.tmp'
            with open(tmp, 'w', encoding='utf-8') as f:
                json.dump(st, f, ensure_ascii=False)
            os.replace(tmp, AUTO_FREE_STATE)
            os.chmod(AUTO_FREE_STATE, 0o644)
        except Exception:
            pass
        return True, ''


def auto_free_grant(wxid, ip, wh):
    """Recover before allocating; serialize the entire same-account allocation transaction."""
    with _bind_lock:
        existing = preferred_binding(wxid)  # strict binding read: corrupt storage never becomes empty.
        if existing:
            return existing
        ok, why = auto_free_take(ip, wh)
        if not ok:
            return None, why
        # Match the administrator's lock order before changing codes or code tiers.
        with _pol_lock:
            new = admin_gen(1)
            if not new:
                return None, 'gen'
            code = new[0]
            ct = _load_map(CODE_TIER)
            ct[code] = 'free'
            _save_map(CODE_TIER, ct)
            status, rec = bind_claim(code, wxid)
            if status != 'ok' or not rec:
                return None, 'bind-' + str(status)
        return code, rec
# ---- end auto free -------------------------------------------------------



_wish_lock = threading.Lock()
_wrl = {}


def wish_rate_ok(ip):
    now = time.time()
    with _rl_lock:
        q = [t for t in _wrl.get(ip, ()) if now - t < 3600]
        if len(q) >= WISH_IP_PER_HOUR:
            _wrl[ip] = q
            return False
        q.append(now)
        _wrl[ip] = q
        if len(_wrl) > 4096:
            for k in [k for k, v in _wrl.items() if not v or now - v[-1] > 3600]:
                _wrl.pop(k, None)
        return True


def _bad_type(req, *names):
    """字段存在但类型不是字符串 -> 调用方直接 400，别让它走到业务分支。"""
    for n in names:
        if n in req and not isinstance(req[n], str):
            return True
    return False


def _s(v):
    """只接受 str；其它类型（数字/数组/对象/null）一律当空串，
    避免校验路径上 .strip() 抛 AttributeError 把请求打成异常。"""
    return v if isinstance(v, str) else ''


def clean_text(t):
    t = ''.join(ch for ch in (t or '') if ch == '\n' or (32 <= ord(ch) < 127) or ord(ch) > 127)
    t = t.replace('\r', '').strip()
    return t[:WISH_MAX_CHARS]


def wish_rows():
    rows = []
    if os.path.exists(WISH):
        with open(WISH, encoding='utf-8') as f:
            for ln in f:
                ln = ln.strip()
                if not ln:
                    continue
                try:
                    rows.append(json.loads(ln))
                except Exception:
                    pass
    return rows


def wish_add(rec):
    with _wish_lock:
        today = sum(1 for r in wish_rows()
                    if r.get('wxid') == rec['wxid'] and r.get('day') == rec['day'])
        if today >= WISH_PER_DAY:
            return False
        fd = os.open(WISH, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
        with os.fdopen(fd, 'a', encoding='utf-8') as f:
            os.chmod(WISH, 0o600)
            f.write(json.dumps(rec, ensure_ascii=False) + '\n')
        return True


def _load_map(path):
    try:
        with open(path, encoding='utf-8') as f:
            return json.load(f) or {}
    except Exception:
        return {}


def _save_map(path, d):
    tmp = path + '.tmp'
    with open(tmp, 'w', encoding='utf-8') as f:
        json.dump(d, f, ensure_ascii=False)
    os.replace(tmp, path)


def canonical_tier(tier):
    # Existing professional codes/users join the advanced edition; no code is discarded.
    return 'plus' if tier == 'pro' else tier


def preferred_binding(wxid):
    active = load_codes()
    tiers = _load_map(CODE_TIER)
    hits = [(c, v) for c, v in load_bind().items()
            if c in active and isinstance(v, dict) and v.get('wxid') == wxid]
    if not hits:
        return None
    paid = [(c, v) for c, v in hits if canonical_tier(tiers.get(c) or 'free') in ('plus', 'vip')]
    # Free recovery must never mask a paid activation. Older records use their original timestamp.
    return max(paid or hits, key=lambda item: (int(item[1].get('active_seq') or 0),
                                              int(item[1].get('ts') or 0), item[0]))


def claim_license(wxid, code, paid):
    with _bind_lock:
        if not paid:
            existing = preferred_binding(wxid)
            if existing:
                return 'ok', existing[0], existing[1]
        status, rec = bind_claim(code, wxid, prefer=paid)
        return status, code, rec


def license_reply(wxid, code, rec, **extra):
    ts = int(rec.get('ts') or time.time())
    key = (code, wxid, ts)
    token = _tok_get(key)
    if token is None:
        payload = json.dumps({'code': code, 'wxid': wxid, 'iat': ts},
                             separators=(',', ':'), ensure_ascii=False).encode('utf-8')
        token = base64.b64encode(payload).decode() + '.' + base64.b64encode(sign(payload)).decode()
        _tok_put(key, token)
    tier, feats = feat_of(wxid)
    return dict({'ok': True, 'code': code, 'token': token, 'tier': tier,
                 'tier_name': TIER_NAME.get(tier, tier), 'kernel_edition': tier,
                 'feat': ','.join(feats)}, **extra)


def tier_of(wxid):
    """这个 wxid 绑过的所有码里最高的等级"""
    best = None
    try:
        tiers = _load_map(CODE_TIER)
        active = load_codes()
        for code, rec in load_bind().items():
            if code not in active or (rec or {}).get('wxid') != wxid:
                continue
            t = canonical_tier(tiers.get(code) or 'free')
            if t in TIER_ORDER and (best is None or TIER_ORDER.index(t) > TIER_ORDER.index(best)):
                best = t
    except Exception:
        pass
    return best


def feat_of(wxid):
    """返回 (等级, 功能列表)：等级对应的功能包 + 手动授权的并集；没码 = 免费版"""
    t = tier_of(wxid) or 'free'
    if False:
        pass
    feats = []
    packs = _load_map(PACKS)
    for inherited in TIER_ORDER[:TIER_ORDER.index(t) + 1]:
        for f in list(packs.get(inherited) or []) + (list(packs.get('pro') or []) if inherited == 'plus' else []):
            if f and f not in feats:
                feats.append(str(f))
    if TIER_ORDER.index(t) >= TIER_ORDER.index('plus') and IMAGE_FEATURE not in feats:
        feats.append(IMAGE_FEATURE)
    for f in (_load_map(ENTITLE).get(wxid) or []):
        if f and f not in feats:
            feats.append(str(f))
    return t, feats


def load_disabled():
    return _load_map(DISABLED)


def save_disabled(d):
    with _pol_lock:
        _save_map(DISABLED, d)


def load_replies():
    return _load_map(REPLIES)


def save_replies(d):
    with _pol_lock:
        _save_map(REPLIES, d)


_tok = {}
_tok_lock = threading.Lock()   # payload 由 (code,wxid,ts) 唯一决定 → 签名结果可复用


def _tok_get(k):
    with _tok_lock:
        return _tok.get(k)


def _tok_put(k, v):
    with _tok_lock:
        if len(_tok) > 4096:
            _tok.clear()
        _tok[k] = v


_rl = {}
_rl_lock = threading.Lock()

def client_ip(handler):
    """取真实客户端 IP：服务只听 127.0.0.1，直连方是 Caddy，才信它写的 X-Forwarded-For。"""
    peer = handler.client_address[0]
    if peer in ('127.0.0.1', '::1', 'localhost'):
        for h in ('X-Forwarded-For', 'X-Real-IP'):
            v = handler.headers.get(h)
            if v:
                ip = v.split(',')[0].strip()
                if ip:
                    return ip
    return peer


def rate_ok(ip):
    now = time.time()
    with _rl_lock:
        q = [t for t in _rl.get(ip, ()) if now - t < RL_WIN]
        if len(q) >= RL_MAX:
            _rl[ip] = q
            return False
        q.append(now)
        _rl[ip] = q
        if len(_rl) > 4096:                      # 简单的表清理
            for k in [k for k, v in _rl.items() if not v or now - v[-1] > RL_WIN]:
                _rl.pop(k, None)
        return True



# ---------------- 诊断上传：验签与身份 ----------------


def diag_gunzip(b64):
    """Accept a single complete gzip member, with bounded output and strict base64."""
    if not isinstance(b64, str) or not b64 or not b64.isascii():
        raise ValueError('bad gz')
    try:
        raw = base64.b64decode(b64, validate=True)
    except Exception:
        raise ValueError('bad gz')
    if len(raw) < 8 or len(raw) > DIAG_MAX:
        raise ValueError('bad size')
    decoder = zlib.decompressobj(16 + zlib.MAX_WBITS)
    try:
        decoded = decoder.decompress(raw, DIAG_MAX_TEXT + 1)
    except Exception:
        raise ValueError('bad gz')
    if len(decoded) > DIAG_MAX_TEXT:
        raise ValueError('too big')
    if not decoder.eof or decoder.unused_data or decoder.unconsumed_tail:
        raise ValueError('bad gz')
    return decoded



def diag_identity(wh, token=None):
    """A public/shared HMAC key and wxid hash are not identity credentials.

    Legacy 1.15 reports remain accepted as unverified. An optional RSA license
    token must authenticate both the binding and the claimed identity before a
    report can enter diag/. Never return or write the token itself.
    """
    if not isinstance(wh, str) or not re.fullmatch(r'[0-9a-f]{12}', wh):
        return None
    if not isinstance(token, str) or not token or len(token) > 8192:
        return None
    payload = token_ok(token)
    if not isinstance(payload, dict):
        return None
    wxid, code = payload.get('wxid'), payload.get('code')
    if not isinstance(wxid, str) or not isinstance(code, str):
        return None
    if not wxid or not code or hashlib.sha256(wxid.encode('utf-8')).hexdigest()[:12] != wh:
        return None
    try:
        record = load_bind().get(code)
    except StoreError:
        return None                  # 绑定库坏了 -> 身份不明，进隔离区
    if not isinstance(record, dict) or record.get('wxid') != wxid:
        return None
    return code


def diag_rate(store, key, limit, gap=0):
    """store 内按 key 记最近 24h 时间戳；超限或间隔太短返回 False。"""
    now = time.time()
    with _rl_lock:
        q = [t for t in store.get(key, ()) if now - t < 86400]
        if len(q) >= limit or (gap and q and now - q[-1] < gap):
            store[key] = q
            return False
        q.append(now)
        store[key] = q
        if len(store) > 8192:
            for k in [k for k, v in store.items() if not v or now - v[-1] > 86400]:
                store.pop(k, None)
        return True


def diag_budget_wait(store, key, limit, gap=0, now=None, window=86400):
    """Inspect a rolling budget; rejected attempts never append timestamps."""
    now = time.time() if now is None else now
    with _rl_lock:
        stamps = [stamp for stamp in store.get(key, ()) if now - stamp < window]
        if stamps:
            store[key] = stamps
        else:
            store.pop(key, None)
        wait = max(0, (stamps[0] + window - now) if len(stamps) >= limit else 0,
                   (stamps[-1] + gap - now) if gap and stamps else 0)
        return max(1, math.ceil(wait)) if wait > 0 else 0


def diag_budget_commit(store, key, now, window=86400):
    with _rl_lock:
        stamps = [stamp for stamp in store.get(key, ()) if now - stamp < window]
        stamps.append(now)
        store[key] = stamps
        if len(store) > 8192:
            for stale in [k for k, v in store.items() if not v or now - v[-1] >= window]:
                store.pop(stale, None)


def diag_sane(raw, reason, loader):
    """结构校验：伪造者即使拿到 key，也得把整份日志的框架编出来。
    返回解码后的正文，不合格返回 None。"""
    try:
        txt = raw.decode('utf-8')
    except UnicodeDecodeError:
        return None
    if not txt.startswith(DIAG_HEAD + '\n'):
        return None
    if len(txt) < 200:
        return None
    if ('\nreason=' + reason + '\n') not in txt:
        return None
    if ('\nloader=' + loader + '\n') not in txt:
        return None
    # 1.54 客户端：文件缺失/被截断时不再保证四节齐全，这里放宽成「至少两节」，
    # 否则整份现场会被 400 丢掉（历史上 126 次 bad payload）。
    have = sum(1 for need in ('\n--- boot.prev ---', '\n--- boot.log ---',
                              '\n--- loader.log ---', '\n--- trace.log ---')
               if need in txt)
    if have < 2:
        return None
    head = txt[:40000]
    ctrl = sum(1 for ch in head if ord(ch) < 9 or 13 < ord(ch) < 32 or ord(ch) == 127)
    if ctrl > 40:                      # 塞二进制/乱码的直接丢
        return None
    return txt


def diag_store(d, txt, hdr, keep, prefix='na', expire_days=0):
    """独占方式落盘（文件名带身份与原因，方便人工翻），顺手做保留份数/过期清理。"""
    if os.path.islink(d):
        raise OSError('unsafe diagnostic directory')
    os.makedirs(d, mode=0o700, exist_ok=True)
    os.chmod(d, 0o700)
    prefix = re.sub(r'[^A-Za-z0-9_.-]', '_', prefix)[:48] or 'na'
    for _ in range(5):
        name = '%s_%s_%04x.txt' % (time.strftime('%Y%m%d-%H%M%S'), prefix,
                                   int(time.time() * 1000) & 0xffff)
        path = os.path.join(d, name)
        try:
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, 'O_NOFOLLOW', 0), 0o600)
        except FileExistsError:
            time.sleep(0.002)
            continue
        with os.fdopen(fd, 'w', encoding='utf-8') as f:
            f.write(hdr)
            f.write(txt)
        break
    else:
        raise RuntimeError('name clash')
    try:
        fs = [os.path.join(d, x) for x in os.listdir(d) if x.endswith('.txt') and not os.path.islink(os.path.join(d, x)) and os.path.isfile(os.path.join(d, x))]
        fs.sort(key=lambda q: os.path.getmtime(q), reverse=True)
        for old in fs[keep:]:
            os.remove(old)
        if expire_days:
            cut = time.time() - expire_days * 86400
            for q in fs[:keep]:
                if os.path.getmtime(q) < cut:
                    os.remove(q)
    except Exception:
        pass
    return name


class Handler(BaseHTTPRequestHandler):
    server_version = 'wxentry'
    timeout = SOCK_TIMEOUT

    def log_message(self, fmt, *args):
        # 只记状态码，绝不记请求行（里面带密径），也不记查询串
        code = ''
        try:
            if len(args) >= 3:
                code = str(args[1])
        except Exception:
            pass
        sys.stderr.write('%s - %s\n' % (self.address_string(), code))

    def _send(self, code, body: bytes, ctype='application/json; charset=utf-8'):
        self.send_response(code)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        if self.command != 'HEAD':
            self.wfile.write(body)

    def _json(self, obj, code=200):
        self._send(code, json.dumps(obj, ensure_ascii=False).encode('utf-8'))

    def do_HEAD(self):
        self._route(None)

    def do_GET(self):
        self._route(None)


    def do_POST(self):
        lengths = self.headers.get_all('Content-Length', [])
        try:
            if len(lengths) > 1 or self.headers.get('Transfer-Encoding'):
                raise ValueError('unsupported framing')
            n = int(lengths[0]) if lengths else 0
            if n < 0:
                raise ValueError('negative length')
        except (TypeError, ValueError):
            self.close_connection = True
            return self._json({'ok': False, 'msg': 'bad length'}, 400)
        path = self.path.split('?')[0]
        if path.startswith('/wx/'):
            path = path[3:]
        limit = DIAG_MAX if path in ('/diag', '/diag.php') else MAX_BODY
        if n > limit:
            self.close_connection = True
            return self._json({'ok': False, 'msg': 'too large'}, 413)
        self._route(self.rfile.read(n) if n else b'')

    def _route(self, body):
        path = self.path.split('?')[0]
        if path.startswith('/wx/'):
            path = path[3:]
        if path == '/kernel.json':
            return self._legacy_free_kernel('legacy.json')
        if path == '/kernel.enc':
            return self._legacy_free_kernel('kernel.enc')
        if path == '/kernel/announcements':
            return self._kernel_announcements()
        if path == '/kernel/manifest':
            return self._kernel_delivery(body, download=False)
        if path == '/kernel/download':
            return self._kernel_delivery(body, download=True)
        if path == '/apk.json':
            return self._file('apk.json')
        if path in ('/wxentry.apk', '/apk'):
            return self._file('wxentry.apk')
        if path in ('/api.php', '/activate'):
            return self._activate(body)
        if path == '/wish':
            return self._wish(body)
        if path == '/status':
            return self._status(body)
        if path == '/whoami':
            return self._whoami(body)
        if path == '/appeal':
            return self._appeal(body)
        if path == '/unlock':
            return self._unlock(body)
        if path in ('/wechat', '/getwechat'):
            # 官方 Android ARM64 安装包（微信 8.0.80 / 3220），302 直连腾讯 CDN
            self.send_response(302)
            self.send_header('Location', 'https://dldir1v6.qq.com/weixin/android/weixin8080android3220_0x28005032_arm64.apk')
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Content-Length', '0')
            self.end_headers()
            return
        if path in ('/diag', '/diag.php'):
            return self._diag(body)
        if path in ('/admin', '/admin/', '/admin/api'):
            return self._json({'ok': False, 'msg': 'not found'}, 404)   # 公开路径永远不存在
        sp = admin_secret_path() if admin_token() else None
        if sp:
            base = '/' + sp
            if path in (base, base + '/'):
                return self._admin_page()
            if path == base + '/api':
                return self._admin_api(body)
        self._json({'ok': False, 'msg': 'not found'}, 404)


    def _diag_limited(self, seconds):
        body = json.dumps({'ok': False, 'msg': 'rate limited'}).encode('utf-8')
        self.send_response(429)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Retry-After', str(max(1, int(seconds))))
        self.end_headers()
        self.wfile.write(body)

    def _diag(self, body):
        """Bounded private upload; shared-key-only reports are always quarantined."""
        if self.command != 'POST':
            return self._json({'ok': False, 'msg': 'method not allowed'}, 405)
        if self.headers.get('X-Wx-Diag') != '1':
            return self._json({'ok': False, 'msg': 'forbidden'}, 403)
        ip = client_ip(self)
        # Keep a separate high-limit attempt guard for invalid or hostile traffic.
        with _diag_lock:
            now = time.time()
            wait = diag_budget_wait(DIAG_ABUSE_RATE, ip, DIAG_ABUSE_LIMIT,
                                    now=now, window=DIAG_ABUSE_WINDOW)
            if wait:
                return self._diag_limited(wait)
            diag_budget_commit(DIAG_ABUSE_RATE, ip, now, window=DIAG_ABUSE_WINDOW)
        try:
            obj = json.loads(body.decode('utf-8')) if body else {}
        except Exception:
            return self._json({'ok': False, 'msg': 'bad json'}, 400)
        if not isinstance(obj, dict):
            return self._json({'ok': False, 'msg': 'bad json'}, 400)

        reason_raw = str(obj.get('reason') or '')[:160]
        reason = re.sub(r'[^A-Za-z0-9_.:\-]', '_', reason_raw)[:80]
        reason_disp = reason_raw.replace('\n', ' ').replace('\r', ' ')[:120]
        loader = re.sub(r'[^0-9.]', '', str(obj.get('loader') or ''))[:16]
        claimed_wh = obj.get('wh')
        wh = claimed_wh if isinstance(claimed_wh, str) and re.fullmatch(r'[0-9a-f]{12}', claimed_wh) else ''
        cp = re.sub(r'[^A-Z0-9]', '', str(obj.get('cp') or '').upper())[:4]
        mac = str(obj.get('hmac') or '').strip().lower()
        try:
            ts = int(obj.get('ts') or 0)
        except Exception:
            ts = 0
        if reason_raw.split(':')[0].strip() not in DIAG_REASONS:
            return self._json({'ok': False, 'msg': 'bad reason'}, 400)
        if not re.fullmatch(r'[0-9a-f]{64}', mac):
            return self._json({'ok': False, 'msg': 'unsigned'}, 400)
        if abs(time.time() - ts) > DIAG_TS_WIN:
            return self._json({'ok': False, 'msg': 'stale'}, 400)
        try:
            raw = diag_gunzip(obj.get('gz'))
            signed_prefix = ('%s|%d|' % (reason_raw, ts)).encode('utf-8')
        except (ValueError, UnicodeError) as error:
            message = str(error) if isinstance(error, ValueError) else 'bad reason'
            return self._json({'ok': False, 'msg': message}, 400)
        want = hmac.new(DIAG_KEY, signed_prefix + raw, hashlib.sha256).hexdigest()
        if not hmac.compare_digest(want, mac):
            return self._json({'ok': False, 'msg': 'bad sig'}, 403)
        txt = diag_sane(raw, reason_raw, loader)
        if txt is None:
            return self._json({'ok': False, 'msg': 'bad payload'}, 400)

        is_selftest = reason_raw.split(':')[0].strip() == 'selftest'
        # lic is the 1.16 contract. Legacy token is accepted only when lic is absent.
        license_proof = obj.get('lic') if 'lic' in obj else obj.get('token')
        code = None if is_selftest else diag_identity(wh, license_proof)
        verified = code is not None
        principal = (('selftest:' + (wh or ('ip:' + ip))) if is_selftest else
                     ('verified:' + wh) if verified else ('unverified:' + (wh or ('ip:' + ip))))
        fingerprint = (principal, hashlib.sha256(raw).hexdigest())
        now = time.time()
        with _diag_lock:
            if len(DIAG_SEEN) > 4096:
                for key, stamp in list(DIAG_SEEN.items()):
                    if now - stamp > 86400:
                        DIAG_SEEN.pop(key, None)
            if now - DIAG_SEEN.get(fingerprint, 0) < 86400:
                return self._json({'ok': True, 'file': '', 'trust': 'duplicate'})
            if is_selftest:
                budgets = [(DIAG_SELFTEST_IP_RATE, ip, 1, 0)]
                if wh:
                    budgets.append((DIAG_SELFTEST_WH_RATE, wh, 1, 0))
            else:
                budgets = [(DIAG_RATE, ip, 30, 20),
                           (DIAG_WH_RATE, principal, 20 if verified else 5, 0)]
            wait = max(diag_budget_wait(store, key, limit, gap, now)
                       for store, key, limit, gap in budgets)
            if wait:
                return self._diag_limited(wait)

            agent = str(self.headers.get('User-Agent') or '').replace('\r', ' ').replace('\n', ' ')[:120]
            code_hint = code.split('-')[1][:4] if verified and '-' in code else cp
            trust = 'verified' if verified else 'unverified'
            header = ('reason=%s\nwh=%s\ncode=%s\ncode4=%s\nloader=%s\ntrust=%s\n'
                      'identity_proof=%s\nsig=hmac-sha256\nip=%s\nts=%d\nsize=%d\nua=%s\n\n'
                      % (reason_disp or reason, wh or '-', 'bound' if verified else '-',
                         code_hint or '-', loader or '-', trust,
                         'rsa-license' if verified else 'none', ip, ts, len(raw), agent))
            try:
                prefix = (wh or 'noid') + '_' + (reason.split(':')[-1] or 'na')
                if verified:
                    name = diag_store(DIAG_DIR, txt, header, DIAG_KEEP, prefix)
                else:
                    name = diag_store(DIAG_QUAR, txt, header, DIAG_KEEP_QUAR, prefix, DIAG_QUAR_DAYS)
            except Exception:
                return self._json({'ok': False, 'msg': 'write failed'}, 500)
            for store, key, limit, gap in budgets:
                diag_budget_commit(store, key, now)
            DIAG_SEEN[fingerprint] = now
        return self._json({'ok': True, 'file': name, 'trust': trust})

    def _file(self, name):
        p = os.path.join(BASE, name)
        if not os.path.exists(p):
            return self._json({'ok': False, 'msg': 'missing ' + name}, 404)
        with open(p, 'rb') as f:
            data = f.read()
        self._send(200, data)

    def _legacy_free_kernel(self, name):
        # Legacy/public endpoints never serve a paid kernel, including before publication.
        directory = os.path.join(BASE, 'kernels', 'free')
        path = os.path.join(directory, name)
        if name == 'kernel.enc':
            legacy_payload = os.path.join(directory, 'legacy.enc')
            if os.path.isfile(legacy_payload):
                path = legacy_payload
        try:
            with open(path, 'rb') as f:
                data = f.read()
        except OSError:
            return self._json({'ok': False, 'msg': 'free kernel not published'}, 503)
        return self._send(200, data, 'application/json; charset=utf-8' if name.endswith('.json') else 'application/octet-stream')

    def _kernel_announcements(self):
        if self.command not in ('GET', 'HEAD'):
            return self._json({'ok': False, 'msg': 'GET required'}, 405)
        try:
            with open(os.path.join(BASE, 'announcements.json'), 'rb') as f:
                raw = f.read(131073)
            if len(raw) > 131072:
                raise ValueError('announcements too large')
            releases = json.loads(raw.decode('utf-8'))['releases']
            if not isinstance(releases, list):
                raise ValueError('expected a release list')
            result, seen = [], set()
            for entry in releases:
                version = entry['version']
                if not isinstance(version, str) or not re.fullmatch(r'[1-9][0-9]{0,8}', version) or version in seen:
                    raise ValueError('invalid or duplicate release version')
                date = entry.get('date', '')
                if not isinstance(date, str) or (date and not re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}', date)):
                    raise ValueError('invalid release date')
                notes = {key: entry.get(key, []) for key in ('added', 'fixed')}
                if not any(notes.values()) or any(
                        not isinstance(lines, list) or any(not isinstance(line, str) or not line.strip() for line in lines)
                        for lines in notes.values()):
                    raise ValueError('invalid release notes')
                seen.add(version)
                result.append(dict(version=version, date=date, **notes))
            result.sort(key=lambda entry: int(entry['version']), reverse=True)
            body = json.dumps({'releases': result}, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
            if len(body) > 131072:
                raise ValueError('response too large')
        except FileNotFoundError:
            return self._json({'releases': []})
        except (OSError, ValueError, TypeError, KeyError, AttributeError, RecursionError):
            return self._json({'ok': False, 'msg': 'announcements unavailable'}, 503)
        return self._send(200, body)
    def _kernel_delivery(self, body, download=False):
        if self.command != 'POST':
            return self._json({'ok': False, 'msg': 'POST required'}, 405)
        try:
            req = json.loads((body or b'{}').decode('utf-8'))
        except Exception:
            return self._json({'ok': False, 'msg': 'bad json'}, 400)
        if not isinstance(req, dict) or _bad_type(req, 'token', 'v', 'sha256'):
            return self._json({'ok': False, 'msg': 'bad json'}, 400)
        payload = token_ok(_s(req.get('token')).strip())
        if not isinstance(payload, dict):
            return self._json({'ok': False, 'msg': 'unauthorized'}, 401)
        wxid, code = payload.get('wxid'), payload.get('code')
        if not isinstance(wxid, str) or not isinstance(code, str):
            return self._json({'ok': False, 'msg': 'unauthorized'}, 401)
        try:
            binding = load_bind().get(code) or {}
        except StoreError:
            return self._json({'ok': False, 'msg': 'authorization store unavailable'}, 503)
        if code not in load_codes() or binding.get('wxid') != wxid:
            return self._json({'ok': False, 'msg': 'authorization no longer active'}, 403)
        if wxid in load_disabled():
            return self._json({'ok': False, 'msg': 'account disabled'}, 403)
        edition = tier_of(wxid) or 'free'
        directory = os.path.join(BASE, 'kernels', edition)
        try:
            with open(os.path.join(directory, 'kernel.json'), encoding='utf-8') as f:
                manifest = json.load(f)
            if (not isinstance(manifest, dict) or manifest.get('edition') != edition
                    or not re.fullmatch(r'[1-9][0-9]{0,8}', str(manifest.get('v', '')))
                    or not re.fullmatch(r'[a-f0-9]{64}', str(manifest.get('sha256', '')))
                    or not isinstance(manifest.get('sig'), str)):
                raise ValueError('invalid channel manifest')
        except (OSError, ValueError, TypeError):
            return self._json({'ok': False, 'msg': 'edition kernel unavailable'}, 503)
        minimum_apk = manifest.get('min_apk_v', 0)
        apk_version = req.get('apk_v', 0)
        if type(minimum_apk) is not int or not 0 <= minimum_apk <= 999999999:
            return self._json({'ok': False, 'msg': 'invalid kernel compatibility setting'}, 503)
        if type(apk_version) is not int or not 0 <= apk_version <= 999999999:
            return self._json({'ok': False, 'msg': 'invalid APK version'}, 400)
        if apk_version < minimum_apk:
            return self._json({'ok': False, 'msg': 'update module APK first', 'min_apk_v': minimum_apk}, 426)
        if not download:
            if not rate_ok(client_ip(self)):
                return self._json({'ok': False, 'msg': 'too many requests'}, 429)
            owner = hashlib.sha256(_s(req.get('token')).strip().encode('utf-8')).hexdigest()
            grant = ('kernel-grant-v1|%s|%s|%s|%s' % (owner, edition, manifest['v'], manifest['sha256'])).encode('utf-8')
            try:
                grant_sig = base64.b64encode(sign(grant)).decode('ascii')
            except Exception:
                return self._json({'ok': False, 'msg': 'activation grant unavailable'}, 503)
            return self._json(dict(manifest, ok=True, grant_sig=grant_sig))
        # Reject stale release pairs; edition is derived solely from the bound activation.
        if req.get('v') != manifest['v'] or req.get('sha256') != manifest['sha256']:
            return self._json({'ok': False, 'msg': 'kernel release changed; refresh manifest'}, 409)
        try:
            with open(os.path.join(directory, 'kernel.enc'), 'rb') as f:
                data = f.read()
            if hashlib.sha256(data).hexdigest() != manifest.get('enc_sha256'):
                raise ValueError('incomplete publication')
        except (OSError, ValueError):
            return self._json({'ok': False, 'msg': 'edition kernel unavailable'}, 503)
        return self._send(200, data, 'application/octet-stream')

    def _wish(self, body):
        """许愿：必须带有效授权凭证（RSA 签名），wxid 只认凭证里的，不认客户端自报。"""
        ip = client_ip(self)
        if not wish_rate_ok(ip):
            return self._json({'ok': False, 'msg': '太频繁了，稍后再试'}, 429)
        try:
            req = json.loads((body or b'{}').decode('utf-8'))
        except Exception:
            return self._json({'ok': False, 'msg': 'bad json'})
        if not isinstance(req, dict):
            return self._json({'ok': False, 'msg': 'bad json'})
        raw_token = req.get('token')
        token = raw_token.strip() if isinstance(raw_token, str) else ''
        payload = token_ok(token)
        if (not isinstance(payload, dict)
                or not isinstance(payload.get('wxid'), str)
                or not isinstance(payload.get('code'), str)):
            return self._json({'ok': False, 'msg': '授权校验失败，请先激活'})
        wxid = payload['wxid'].strip()
        code = payload['code'].strip()
        try:
            rec = load_bind().get(code)
        except StoreError:
            return self._json({'ok': False, 'msg': '授权库暂时不可用，请稍后重试'}, 503)
        if not wxid or not rec or rec.get('wxid') != wxid:
            return self._json({'ok': False, 'msg': '该激活码不属于当前微信'})
        if not isinstance(req.get('text'), str):
            return self._json({'ok': False, 'msg': '内容格式不对'})
        text = clean_text(req['text'])
        if len(text) < 2:
            return self._json({'ok': False, 'msg': '内容太短了'})
        day = time.strftime('%Y-%m-%d')
        if not wish_add({'ts': int(time.time()), 'day': day, 'wxid': wxid, 'code': code,
                         'text': text, 'ip': ip}):
            return self._json({'ok': False, 'msg': '今天已经许过愿啦，明天再来' if WISH_PER_DAY == 1
                               else '今天已经许过 %d 个啦，明天再来' % WISH_PER_DAY})
        return self._json({'ok': True, 'msg': '已收到你的许愿，谢谢！'})

    def _status(self, body):
        """客户端每 30 分钟问一次：这个微信号是否被停用、有没有许愿回复。
        只认授权凭证里的 wxid，客户端自报的一律不认。"""
        try:
            req = json.loads((body or b'{}').decode('utf-8'))
        except Exception:
            return self._json({'ok': False, 'msg': 'bad json'}, 400)
        token = _s(req.get('token')).strip() if isinstance(req, dict) else ''
        payload = token_ok(token)
        wxid = (payload or {}).get('wxid')
        if not wxid:
            return self._json({'ok': False, 'msg': 'unauthorized'}, 401)
        d = load_disabled().get(wxid)
        r = load_replies().get(wxid) or {}
        out = {'ok': True, 'ts': int(time.time()), 'disabled': bool(d)}
        if d:
            out['reason'] = d.get('reason') or ''
            out['at'] = d.get('at') or 0
        if r.get('text'):
            out['reply'] = r.get('text')
            out['reply_at'] = r.get('at') or 0
        t, feats = feat_of(wxid)
        out['tier'] = t
        out['tier_name'] = TIER_NAME.get(t, t)
        out['kernel_edition'] = t
        if feats:
            out['feat'] = ','.join(feats)
        return self._json(out)

    def _appeal(self, body):
        """申诉：带授权凭证 + 一段说明，进 appeals.jsonl，管理台可看。"""
        try:
            req = json.loads((body or b'{}').decode('utf-8'))
        except Exception:
            return self._json({'ok': False, 'msg': 'bad json'}, 400)
        if not isinstance(req, dict):
            return self._json({'ok': False, 'msg': 'bad json'}, 400)
        token = _s(req.get('token')).strip()
        payload = token_ok(token)
        wxid = (payload or {}).get('wxid')
        if not wxid:
            return self._json({'ok': False, 'msg': 'unauthorized'}, 401)
        text = clean_text(_s(req.get('text')))[:1000]
        if len(text) < 2:
            return self._json({'ok': False, 'msg': '写点内容吧'})
        now = int(time.time())
        with _pol_lock:
            last = _appeal_rate.get(wxid, 0)
            if now - last < 3600:
                return self._json({'ok': False, 'msg': '申诉太频繁，一小时后再试'}, 429)
            _appeal_rate[wxid] = now
            try:
                fd = os.open(APPEALS, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
                with os.fdopen(fd, 'a', encoding='utf-8') as f:
                    f.write(json.dumps({'wxid': wxid, 'text': text, 'ts': now}, ensure_ascii=False) + '\n')
            except Exception:
                return self._json({'ok': False, 'msg': '保存失败'})
        return self._json({'ok': True})

    def _whoami(self, body):
        """按 wxid 取回已激活凭证：服务端已经绑过码就直接发 token，不再消耗新激活码。
        被拉黑的 wxid 一律拒绝，哪怕它手里还有别的未用码。"""
        if not who_rate_ok(client_ip(self)):
            alog('', '', 'whoami-429', client_ip(self))
            return self._json({'ok': False, 'msg': '请求过于频繁，稍后再试'}, 429)
        try:
            req = json.loads((body or b'{}').decode('utf-8'))
        except Exception:
            return self._json({'ok': False, 'msg': 'bad json'}, 400)
        if not isinstance(req, dict) or _bad_type(req, 'wxid'):
            return self._json({'ok': False, 'msg': 'bad json'}, 400)
        wxid = _s(req.get('wxid')).strip()
        if not (3 <= len(wxid) <= 40) or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-' for c in wxid):
            return self._json({'ok': False, 'msg': '微信号异常'})
        d = load_disabled().get(wxid)
        if d:
            return self._json({'ok': False, 'disabled': True,
                               'reason': (d.get('reason') if isinstance(d, dict) else '') or ''})
        try:
            existing = preferred_binding(wxid)
            created = existing is None
            code, rec = existing or auto_free_grant(wxid, client_ip(self), _s(req.get('wh')).strip()[:64])
            if not code:
                return self._json({'ok': False, 'none': True, 'auto': str(rec), 'msg': '暂时无法自动开通，请稍后重试'})
            return self._json(license_reply(wxid, code, rec, auto=created, restored=not created))
        except StoreError:
            return self._json({'ok': False, 'msg': '授权库暂时不可用，请稍后重试'}, 503)
        except Exception:
            return self._json({'ok': False, 'msg': '激活服务暂时不可用，请稍后重试'}, 503)

    def _unlock(self, body):
        """功能码解锁：校验码的等级 → 绑定到当前 wxid → 立即返回新的功能列表。"""
        try:
            req = json.loads((body or b'{}').decode('utf-8'))
        except Exception:
            return self._json({'ok': False, 'msg': 'bad json'}, 400)
        if not isinstance(req, dict):
            return self._json({'ok': False, 'msg': 'bad json'}, 400)
        if _bad_type(req, 'token', 'code'):
            return self._json({'ok': False, 'msg': 'bad json'}, 400)
        payload = token_ok(_s(req.get('token')).strip())
        wxid = (payload or {}).get('wxid')
        if not wxid:
            return self._json({'ok': False, 'msg': 'unauthorized'}, 401)
        if wxid in load_disabled():
            return self._json({'ok': False, 'msg': '该微信号已被停用'})
        code = _s(req.get('code')).strip().upper()
        if not code:
            return self._json({'ok': False, 'msg': '请输入功能码'})
        tier = canonical_tier(_load_map(CODE_TIER).get(code))
        if not tier:
            if code in load_codes():
                return self._json({'ok': False, 'msg': '这个码是激活码，不含功能包'})
            return self._json({'ok': False, 'msg': '功能码不存在或已作废'})
        if code not in load_codes():
            return self._json({'ok': False, 'msg': '功能码已作废'})
        st, bound_code, _rec = claim_license(wxid, code, tier in ('plus', 'vip'))
        if st == 'err':
            return self._json({'ok': False, 'msg': '授权库暂时不可用，请稍后重试'}, 503)
        if st == 'other':
            return self._json({'ok': False, 'msg': '该功能码已绑定其他微信号'})
        try:
            return self._json(license_reply(wxid, bound_code, _rec))
        except Exception:
            return self._json({'ok': False, 'msg': '凭证生成失败，请稍后重试'}, 503)

    def _admin_page(self):
        body = ADMIN_HTML.encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        if self.command != 'HEAD':
            self.wfile.write(body)

    def _admin_reply(self, obj, setc=None):
        body = json.dumps(obj, ensure_ascii=False).encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        if setc:
            self.send_header('Set-Cookie', setc)
        self.end_headers()
        if self.command != 'HEAD':
            self.wfile.write(body)

    def _admin_api(self, body):
        ip = client_ip(self)
        # 没开管理台 / 未授权 → 一律 404，不暴露接口存在
        if not admin_authed(self):
            if not admin_rate_ok(ip):
                return self._json({'ok': False, 'msg': 'too many'}, 429)
            return self._json({'ok': False, 'msg': 'not found'}, 404)
        admin_fail_clear(ip)
        try:
            req = json.loads((body or b'{}').decode('utf-8'))
        except Exception:
            return self._json({'ok': False, 'msg': 'bad json'})
        if not isinstance(req, dict):
            return self._json({'ok': False, 'msg': 'bad json'})
        act = _s(req.get('a'))
        if act == 'login':
            c = '%s=%s; Path=/; Max-Age=7776000; HttpOnly; Secure; SameSite=Strict' % (COOKIE, cookie_value(admin_token()))
            return self._admin_reply({'ok': True}, c)
        if act == 'logout':
            c = '%s=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Strict' % COOKIE
            return self._admin_reply({'ok': True}, c)
        if act == 'autofree':
            cur = _jload(AUTO_FREE_CFG, {})
            if not isinstance(cur, dict):
                cur = {}
            touched = False
            if 'on' in req:
                cur['on'] = bool(req.get('on'))
                touched = True
            for k in ('daily', 'ip_daily', 'wh_daily'):
                if k in req:
                    try:
                        cur[k] = max(0, min(10000, int(req.get(k))))
                    except Exception:
                        return self._json({'ok': False, 'msg': 'bad ' + k})
                    touched = True
            if 'tier' in req:
                t = _s(req.get('tier')).strip()
                if t != 'free':
                    return self._json({'ok': False, 'msg': '自动开通仅生成免费版激活码'})
                cur['tier'] = 'free'
                touched = True
            if touched:
                try:
                    _save_map(AUTO_FREE_CFG, cur)
                    os.chmod(AUTO_FREE_CFG, 0o644)
                except Exception as e:
                    return self._json({'ok': False, 'msg': 'save failed: %s' % e}, 500)
            st = auto_free_state()
            return self._json({'ok': True, 'policy': auto_free_policy(), 'day': st.get('day'),
                               'count': int(st.get('total') or 0),
                               'ips': sorted(st.get('ips').items(), key=lambda kv: -kv[1])[:10]})
        if act == 'list':
            try:
                bind = load_bind()
            except StoreError:
                return self._json({'ok': False, 'msg': '授权库暂时不可用'}, 503)
            tiers = _load_map(CODE_TIER)
            rows = [{'code': c, 'wxid': (bind.get(c) or {}).get('wxid'),
                     'ts': (bind.get(c) or {}).get('ts'), 'tier': canonical_tier(tiers.get(c) or 'free')} for c in read_codes()]
            return self._json({'ok': True, 'codes': rows,
                               'free': sum(1 for r in rows if not r['wxid']), 'total': len(rows)})
        if act == 'gen':
            try:
                n = int(req.get('n') or 0)
            except Exception:
                n = 0
            if not (1 <= n <= 200):
                return self._json({'ok': False, 'msg': '数量 1~200'})
            tier = _s(req.get('tier')).strip() or 'free'
            if tier not in TIER_ORDER:
                return self._json({'ok': False, 'msg': '不支持的激活码版本'})
            with _pol_lock:
                new = admin_gen(n)
                ct = _load_map(CODE_TIER)
                for c in new:
                    ct[c] = tier
                _save_map(CODE_TIER, ct)
            return self._json({'ok': True, 'new': new, 'tier': tier})
        if act == 'wishes':
            try:
                n = int(req.get('n') or 200)
            except Exception:
                n = 200
            rows = wish_rows()
            rep_ = load_replies()
            for r in rows:
                q = rep_.get(r.get('wxid') or '')
                if q:
                    r['reply'] = q.get('text')
                    r['reply_at'] = q.get('at')
            return self._json({'ok': True, 'total': len(rows), 'wishes': rows[-max(1, min(n, 500)):],
                               'replies': rep_})
        if act == 'diag':
            try:
                count = max(1, min(int(req.get('n') or 20), 50))
            except (TypeError, ValueError):
                return self._json({'ok': False, 'msg': 'bad n'}, 400)
            out = {}
            for k, d in (('verified', DIAG_DIR), ('unverified', DIAG_QUAR)):
                try:
                    fs = [os.path.join(d, x) for x in os.listdir(d) if x.endswith('.txt') and not os.path.islink(os.path.join(d, x)) and os.path.isfile(os.path.join(d, x))]
                except OSError:
                    fs = []
                fs.sort(key=lambda q: os.path.getmtime(q), reverse=True)
                out[k] = {'count': len(fs),
                          'latest': [os.path.basename(q) for q in fs[:count]]}
            return self._json({'ok': True, 'diag': out})
        if act == 'diagpurge':
            if (req.get('dir') or '') != 'unverified':
                return self._json({'ok': False, 'msg': '只允许清理隔离目录'})
            n = 0
            try:
                for x in os.listdir(DIAG_QUAR):
                    if x.endswith('.txt'):
                        os.remove(os.path.join(DIAG_QUAR, x))
                        n += 1
            except OSError:
                pass
            return self._json({'ok': True, 'removed': n})
        if act == 'revoke':
            c = (req.get('code') or '').strip()
            lines = read_codes()
            if c not in lines:
                return self._json({'ok': False, 'msg': '没有这个码'})
            write_codes([x for x in lines if x != c])
            return self._json({'ok': True, 'revoked': c})
        if act == 'packs':
            if 'set' in req:
                pk = req.get('packs') or {}
                clean = {}
                for t in TIER_ORDER:
                    v = pk.get(t) or []
                    clean[t] = [str(x).strip() for x in v if str(x).strip()]
                with _pol_lock:
                    _save_map(PACKS, clean)
                return self._json({'ok': True, 'packs': clean})
            packs = _load_map(PACKS)
            packs['plus'] = list(dict.fromkeys(list(packs.get('plus') or []) + list(packs.get('pro') or [])))
            packs.pop('pro', None)
            return self._json({'ok': True, 'packs': packs,
                               'tiers': TIER_ORDER, 'names': TIER_NAME})
        if act == 'disable':
            w = (req.get('wxid') or '').strip()
            reason = clean_text(req.get('reason') or '')[:300]
            if not (3 <= len(w) <= 40):
                return self._json({'ok': False, 'msg': 'wxid 不对'})
            if not reason:
                return self._json({'ok': False, 'msg': '写个停用原因'})
            d = load_disabled()
            d[w] = {'reason': reason, 'at': int(time.time())}
            save_disabled(d)
            return self._json({'ok': True, 'disabled': d})
        if act == 'enable':
            w = (req.get('wxid') or '').strip()
            d = load_disabled()
            if w in d:
                del d[w]
                save_disabled(d)
            return self._json({'ok': True, 'disabled': d})
        if act == 'disabled':
            return self._json({'ok': True, 'disabled': load_disabled()})
        if act == 'appeals':
            rows = []
            try:
                with open(APPEALS, encoding='utf-8') as f:
                    for ln in f:
                        ln = ln.strip()
                        if ln:
                            try:
                                rows.append(json.loads(ln))
                            except Exception:
                                pass
            except OSError:
                pass
            return self._json({'ok': True, 'total': len(rows), 'appeals': rows[-200:]})
        if act == 'wishreply':
            w = (req.get('wxid') or '').strip()
            text = clean_text(req.get('text') or '')[:500]
            if not w:
                return self._json({'ok': False, 'msg': '缺 wxid'})
            r = load_replies()
            if text:
                r[w] = {'text': text, 'at': int(time.time())}
            else:
                r.pop(w, None)          # 空文本 = 删除回复
            save_replies(r)
            return self._json({'ok': True, 'replies': r})
        return self._json({'ok': False, 'msg': 'unknown action'})

    def _activate(self, body):
        if not rate_ok(client_ip(self)):
            alog('', '', 'rate-limit', client_ip(self))
            return self._json({'ok': False, 'msg': '请求过于频繁，稍后再试'}, 429)
        try:
            req = json.loads((body or b'{}').decode('utf-8'))
        except Exception:
            return self._json({'ok': False, 'msg': 'bad json'}, 400)
        if not isinstance(req, dict):
            return self._json({'ok': False, 'msg': 'bad json'}, 400)
        if _bad_type(req, 'code', 'wxid'):
            return self._json({'ok': False, 'msg': 'bad json'}, 400)
        code = _s(req.get('code')).strip().upper()
        wxid = _s(req.get('wxid')).strip()
        if not (8 <= len(code) <= 40) or any(c not in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-' for c in code):
            return self._json({'ok': False, 'msg': '激活码格式不对'})
        if not (3 <= len(wxid) <= 40) or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-' for c in wxid):
            return self._json({'ok': False, 'msg': '微信号异常'})

        if wxid in load_disabled():
            return self._json({'ok': False, 'msg': '该微信号已被停用'})

        if code not in load_codes():
            alog(wxid, code, 'no-code', client_ip(self))
            return self._json({'ok': False, 'msg': '激活码不存在或已作废'})

        # 2026-11-04：一个微信号最多绑 1 个免费码，防止囤码（升级码不受限）
        ctype = canonical_tier(_load_map(CODE_TIER).get(code) or 'free')
        st, bound_code, rec = claim_license(wxid, code, ctype in ('plus', 'vip'))
        if st == 'err':
            return self._json({'ok': False, 'msg': '授权库暂时不可用，请稍后重试'}, 503)
        if st == 'other':
            alog(wxid, code, 'bound-other', client_ip(self))
            return self._json({'ok': False, 'msg': '该激活码已绑定其他微信号'})

        try:
            return self._json(license_reply(wxid, bound_code, rec))
        except Exception:
            return self._json({'ok': False, 'msg': '凭证生成失败，请稍后重试'}, 503)


ADMIN_HTML = r"""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>激活码管理台</title>
<style>
:root{color-scheme:dark}
*{box-sizing:border-box}
body{margin:0;padding:14px 14px 60px;background:#111418;color:#e7edf3;
     font:15px/1.5 -apple-system,system-ui,"PingFang SC","Microsoft YaHei",sans-serif}
h1{font-size:17px;margin:0 0 12px;font-weight:600;letter-spacing:.3px}
.card{background:#1a1f26;border:1px solid #262d36;border-radius:12px;padding:12px;margin-bottom:12px}
label{display:block;font-size:12px;color:#8b98a5;margin:0 0 6px}
input{width:100%;padding:11px 12px;border-radius:9px;border:1px solid #2c343e;
      background:#0e1116;color:#e7edf3;font-size:15px}
button{padding:11px 14px;border-radius:9px;border:0;background:#2f6df6;color:#fff;
       font-size:15px;font-weight:600;width:100%;margin-top:10px}
button:disabled{opacity:.45}
button.ghost{background:#232a33;color:#c9d4df;font-weight:500}
button.mini{width:auto;padding:6px 10px;font-size:13px;margin:0;background:#232a33;color:#c9d4df}
.row{display:flex;gap:8px;align-items:center}
.row input{flex:1}
#af_on{appearance:none;-webkit-appearance:none;flex:0 0 44px!important;width:44px!important;height:26px;padding:0;border:0;border-radius:20px;background:#b0b6bf;position:relative;cursor:pointer;vertical-align:middle}
#af_on:checked{background:#2979ff}
#af_on:after{content:"";position:absolute;width:22px;height:22px;left:2px;top:2px;border-radius:50%;background:#fff;transition:left .15s}
#af_on:checked:after{left:20px}
.mono{font-family:ui-monospace,Menlo,Consolas,monospace;letter-spacing:.4px}
.code{display:flex;justify-content:space-between;align-items:center;gap:8px;
      padding:9px 10px;border-radius:9px;background:#0e1116;border:1px solid #262d36;margin-top:8px}
.code .t{font-size:15px}
.tag{font-size:12px;padding:2px 8px;border-radius:999px;background:#1d2a1f;color:#63d47f}
.tag.used{background:#2a2320;color:#e0a76a}
.muted{color:#8b98a5;font-size:12px}
.hide{display:none}
.ok{color:#63d47f}.err{color:#ff7b72}
</style></head><body>
<h1>微信模块 · 激活码管理台</h1>

<div class="card" id="login">
  <label>管理员令牌</label>
  <input id="tok" type="password" placeholder="admin.token 里的内容" autocomplete="off">
  <button onclick="login()">登录</button>
  <div id="lmsg" class="muted" style="margin-top:8px">令牌正确后会在这台浏览器存一个 90 天的登录态（HttpOnly，脚本读不到）。</div>
</div>

<div id="panel" class="hide">
  <div class="card">
    <div class="row">
      <input id="n" type="number" value="5" min="1" max="200" style="max-width:90px">
      <select id="tier" style="max-width:180px">
        <option value="">激活码（无功能包）</option>
        <option value="free">免费版</option>
        <option value="plus">高级版</option>
        <option value="vip">VIP 版</option>
      </select>
      <button onclick="gen()">生成</button>
      <button class="ghost" onclick="refresh()">刷新列表</button>
    </div>
    <div id="msg" class="muted" style="margin-top:8px"></div>
    <div id="new"></div>
  </div>

  <div class="card">
    <div class="row">
      <label style="margin:0">新用户自动激活</label>
      <label style="margin:0"><input id="af_on" type="checkbox" style="width:auto"> 开启</label>
      <label style="margin:0" class="muted">日上限</label>
      <input id="af_daily" type="number" min="0" max="10000" style="max-width:90px">
      <label style="margin:0" class="muted">每 IP</label>
      <input id="af_ip" type="number" min="0" max="1000" style="max-width:80px">
      <button onclick="afSave()">保存</button>
    </div>
    <div id="afmsg" class="muted" style="margin-top:8px"></div>
  </div>

  <div class="card">
    <div class="row" style="justify-content:space-between">
      <label style="margin:0">全部激活码 <span id="stat" class="muted"></span></label>
      <button class="mini" onclick="logout()">退出</button>
    </div>
    <div id="list"></div>
  </div>

  <div class="card">
    <div class="row" style="justify-content:space-between">
      <label style="margin:0">功能许愿 <span id="wstat" class="muted"></span></label>
      <button class="mini" onclick="wishes()">刷新</button>
    </div>
    <div id="wish"></div>
  </div>

  <div class="card">
    <label>功能包（一行一个功能 key，例如 realname.pro）</label>
    <div class="muted">用户功能码的等级决定用哪一包；手动授权（entitlements）可额外追加。留空 = 该等级无额外功能。</div>
    <div class="row"><label style="width:64px">免费版</label><input id="pk_free" placeholder="feat.a, feat.b"></div>
    <div class="row"><label style="width:64px">专业版</label><input id="pk_pro" placeholder="feat.a, feat.b"></div>
    <div class="row"><label style="width:64px">高级版</label><input id="pk_plus" placeholder="feat.a, feat.b"></div>
    <div class="row"><label style="width:64px">VIP 版</label><input id="pk_vip" placeholder="feat.a, feat.b"></div>
    <div class="row"><button onclick="savePacks()">保存功能包</button><button class="ghost" onclick="loadPacks()">刷新</button></div>
    <div id="pkmsg" class="muted"></div>
  </div>

  <div class="card">
    <label>停用 / 恢复（停用=整个模块不可用）</label>
    <div class="row">
      <input id="dwxid" placeholder="wxid_xxx" autocomplete="off">
      <input id="dreason" placeholder="停用原因（会原样显示给用户）" autocomplete="off">
      <button onclick="disableUser()">停用</button>
      <button class="ghost" onclick="refreshDisabled()">刷新</button>
    </div>
    <div class="muted" id="dstat" style="margin-top:6px"></div>
    <div id="dlist"></div>
  </div>
</div>

<script>
const $=id=>document.getElementById(id);
let API=location.pathname;
while(API.endsWith('/')) API=API.slice(0,-1);
API+='/api';

async function api(payload){
  const r=await fetch(API,{method:'POST',credentials:'same-origin',
    headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
  if(r.status===404) return {__denied:true};
  if(r.status===429) throw new Error('失败次数过多，10 分钟后再试');
  return r.json();
}
function say(t,cls){ const m=$('msg'); m.className=cls||'muted'; m.textContent=t; }
function esc(s){ return (s||'').replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c])); }
function showLogin(){ $('login').classList.remove('hide'); $('panel').classList.add('hide'); }
function showPanel(){ $('login').classList.add('hide'); $('panel').classList.remove('hide'); }

async function login(){
  const t=$('tok').value.trim();
  if(!t) return;
  let r;
  try{
    r=await fetch(API,{method:'POST',credentials:'same-origin',
      headers:{'Content-Type':'application/json','X-Admin-Token':t},
      body:JSON.stringify({a:'login'})});
  }catch(e){ $('lmsg').textContent='网络错误'; $('lmsg').className='err'; return; }
  if(r.status===404){ $('lmsg').textContent='令牌不对，或管理台已关闭'; $('lmsg').className='err'; return; }
  if(r.status===429){ $('lmsg').textContent='失败次数过多，10 分钟后再试'; $('lmsg').className='err'; return; }
  $('tok').value='';
  refresh();
}
async function logout(){ try{ await api({a:'logout'}); }catch(e){} location.reload(); }

function bind(){
  Array.from(document.querySelectorAll('button[data-c]')).forEach(function(b){
    b.onclick=function(){
      const c=this.getAttribute('data-c');
      if(this.classList.contains('act')) revoke(c); else copy(c,this);
    };
  });
}

async function refresh(){
  let j;
  try{ j=await api({a:'list'}); }catch(e){ say(e.message,'err'); return; }
  if(j.__denied){ showLogin(); return; }
  showPanel();
  if(!j.ok){ say(j.msg||'失败','err'); return; }
  $('stat').textContent='（共 '+j.total+'，可用 '+j.free+'）';
  $('list').innerHTML=j.codes.map(function(r){
    const used=!!r.wxid;
    const when=r.ts? new Date(r.ts*1000).toLocaleDateString('zh-CN'):'';
    const tn=r.tier?({free:'免费版',plus:'高级版',vip:'VIP 版'}[r.tier]||r.tier):'';
    return '<div class="code"><span class="t mono">'+esc(r.code)+'</span>'+
      (tn?'<span class="tag" style="background:#1b2733;color:#7cc4ff">'+esc(tn)+'</span>':'')+
      '<span class="tag'+(used?' used':'')+'">'+(used?('已绑 '+esc(r.wxid)+' '+when):'可用')+'</span>'+
      (used?'':'<button class="mini act" data-c="'+esc(r.code)+'">作废</button>')+'</div>';
  }).join('');
  bind();
  wishes();
  refreshDisabled();
  loadPacks();
  afLoad();
}
async function afLoad(){
  let j;
  try{ j=await api({a:'autofree'}); }catch(e){ return; }
  if(!j||!j.ok) return;
  const p=j.policy||{};
  if($('af_on')) $('af_on').checked=!!p.on;
  if($('af_daily')) $('af_daily').value=p.daily;
  if($('af_ip')) $('af_ip').value=p.ip_daily;
  if($('afmsg')) $('afmsg').textContent='今天已自动发出 '+j.count+' 个'+(p.daily?('，日上限 '+p.daily):'，不设日上限');
}
async function afSave(){
  const p={a:'autofree',on:($('af_on')&&$('af_on').checked)?true:false,
           daily:parseInt(($('af_daily')&&$('af_daily').value)||'0',10),
           ip_daily:parseInt(($('af_ip')&&$('af_ip').value)||'0',10)};
  try{
    const j=await api(p);
    if(j.__denied){ showLogin(); return; }
    $('afmsg').className=j.ok?'ok':'err';
    $('afmsg').textContent=j.ok?('已保存：'+(j.policy.on?'开启':'关闭')+'，日上限 '+j.policy.daily+'，每 IP '+j.policy.ip_daily):(j.msg||'失败');
    if(j.ok) afLoad();
  }catch(e){ if($('afmsg')){ $('afmsg').className='err'; $('afmsg').textContent=e.message; } }
}
async function gen(){
  const n=parseInt($('n').value||'0',10);
  const t=$('tier')?$('tier').value:'';
  try{
    const j=await api({a:'gen',n:n,tier:t});
    if(j.__denied){ showLogin(); return; }
    if(!j.ok){ say(j.msg||'失败','err'); return; }
    say('已生成 '+j.new.length+' 个，已写入服务器并立即生效','ok');
    $('new').innerHTML=j.new.map(function(c){
      return '<div class="code"><span class="t mono">'+esc(c)+'</span>'+
        '<button class="mini" data-c="'+esc(c)+'">复制</button></div>';
    }).join('');
    bind();
    refresh();
  }catch(e){ say(e.message,'err'); }
}
async function revoke(c){
  if(!confirm('作废 '+c+' ？\n已激活的设备仍可用，只影响新激活/换机。')) return;
  try{
    const j=await api({a:'revoke',code:c});
    if(j.__denied){ showLogin(); return; }
    say(j.ok?('已作废 '+c):(j.msg||'失败'), j.ok?'ok':'err');
    refresh();
  }catch(e){ say(e.message,'err'); }
}
async function wishes(){
  let j;
  try{ j=await api({a:'wishes'}); }catch(e){ say(e.message,'err'); return; }
  if(j.__denied){ showLogin(); return; }
  if(!j.ok){ say(j.msg||'失败','err'); return; }
  $('wstat').textContent='（共 '+j.total+' 条）';
  $('wish').innerHTML=(j.wishes||[]).slice().reverse().map(function(w){
    const d=new Date(w.ts*1000).toLocaleString('zh-CN');
    return '<div class="code" style="display:block">'+
      '<div class="muted" style="font-size:12px">'+esc(w.wxid)+' · '+esc(d)+'</div>'+
      '<div style="white-space:pre-wrap;margin-top:4px">'+esc(w.text)+'</div>'+
      (w.reply?('<div class="muted" style="margin-top:4px;color:#63d47f">回复：'+esc(w.reply)+'</div>'):'')+
      '<div style="margin-top:6px"><button class="mini" data-r="'+esc(w.wxid)+'">回复</button></div></div>';
  }).join('');
  Array.from(document.querySelectorAll('button[data-r]')).forEach(function(b){
    b.onclick=function(){ replyWish(this.getAttribute('data-r')); };
  });
}
async function loadPacks(){
  try{
    const j=await api({a:'packs'});
    if(j.__denied){ showLogin(); return; }
    const pk=j.packs||{};
    ['free','plus','vip'].forEach(function(t){
      const el=$('pk_'+t); if(el) el.value=(pk[t]||[]).join(', ');
    });
  }catch(e){}
}
async function savePacks(){
  const pk={};
  ['free','plus','vip'].forEach(function(t){
    const v=($('pk_'+t)||{}).value||'';
    pk[t]=v.split(/[,\s]+/).filter(Boolean);
  });
  try{
    const j=await api({a:'packs',set:1,packs:pk});
    if(j.__denied){ showLogin(); return; }
    $('pkmsg').textContent=j.ok?'已保存':'保存失败';
    $('pkmsg').className=j.ok?'ok':'err';
  }catch(e){ $('pkmsg').textContent=e.message; $('pkmsg').className='err'; }
}
async function refreshDisabled(){
  let j;
  try{ j=await api({a:'disabled'}); }catch(e){ return; }
  if(j.__denied){ showLogin(); return; }
  const d=j.disabled||{}; const ks=Object.keys(d);
  $('dstat').textContent='（已停用 '+ks.length+' 个）';
  $('dlist').innerHTML=ks.map(function(w){
    const it=d[w]||{};
    const t=new Date((it.at||0)*1000).toLocaleString('zh-CN');
    return '<div class="code"><span class="t mono">'+esc(w)+'</span>'+
      '<span class="muted" style="flex:1;margin-left:8px">'+esc(it.reason||'')+' · '+esc(t)+'</span>'+
      '<button class="mini" data-w="'+esc(w)+'">恢复</button></div>';
  }).join('');
  Array.from(document.querySelectorAll('button[data-w]')).forEach(function(b){
    b.onclick=function(){ enableUser(this.getAttribute('data-w')); };
  });
}
async function disableUser(){
  const w=$('dwxid').value.trim(), r=$('dreason').value.trim();
  if(!w||!r){ say('wxid 和停用原因都要填','err'); return; }
  if(!confirm('停用 '+w+' ？\n对方下一次检查（≤30 分钟）后整个模块不可用。')) return;
  try{
    const j=await api({a:'disable',wxid:w,reason:r});
    if(j.__denied){ showLogin(); return; }
    say(j.ok?('已停用 '+w):(j.msg||'失败'), j.ok?'ok':'err');
    if(j.ok){ $('dwxid').value=''; $('dreason').value=''; refreshDisabled(); }
  }catch(e){ say(e.message,'err'); }
}
async function enableUser(w){
  if(!confirm('恢复 '+w+' ？')) return;
  try{
    const j=await api({a:'enable',wxid:w});
    if(j.__denied){ showLogin(); return; }
    say(j.ok?('已恢复 '+w):(j.msg||'失败'), j.ok?'ok':'err');
    refreshDisabled();
  }catch(e){ say(e.message,'err'); }
}
async function replyWish(w){
  const t=prompt('回复给 '+w+'（留空 = 删除回复）','');
  if(t===null) return;
  try{
    const j=await api({a:'wishreply',wxid:w,text:t});
    if(j.__denied){ showLogin(); return; }
    say(j.ok?'已保存，客户端下一次检查后显示':(j.msg||'失败'), j.ok?'ok':'err');
    wishes();
  }catch(e){ say(e.message,'err'); }
}
function copy(t,btn){
  navigator.clipboard.writeText(t).then(function(){ btn.textContent='已复制';
    setTimeout(function(){ btn.textContent='复制'; },1200); });
}
refresh();
</script></body></html>"""



_active = 0
_active_lock = threading.Lock()


class Server(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def process_request(self, request, client_address):
        global _active
        with _active_lock:
            if _active >= MAX_CONCURRENT:
                busy = True
            else:
                _active += 1
                busy = False
        if busy:
            try:
                request.sendall(b'HTTP/1.0 503 Service Unavailable\r\n'
                                b'Content-Length: 0\r\nConnection: close\r\n\r\n')
            except Exception:
                pass
            try:
                request.close()
            except Exception:
                pass
            return
        super().process_request(request, client_address)

    def shutdown_request(self, request):
        global _active
        try:
            super().shutdown_request(request)
        finally:
            with _active_lock:
                if _active > 0:
                    _active -= 1


if __name__ == '__main__':
    srv = Server(('127.0.0.1', PORT), Handler)
    sys.stderr.write('wxentry api listening 127.0.0.1:%d, dir=%s\n' % (PORT, BASE))
    srv.serve_forever()
