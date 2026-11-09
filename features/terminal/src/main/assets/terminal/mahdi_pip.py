#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""تثبيت حزم بايثون مع نسبة تقدّم حقيقية من 0 إلى 100 — بلا تجمّد أبدًا (8.0.0).

لماذا كان الشريط يتوقّف عند 40٪؟

  ``pip`` لا يرسل أي نسبة أثناء فكّ الضغط أو بناء الـ wheel: يُنزّل الحزمة (فيصل
  إلى رقم ما) ثم يصمت دقائق وهو يبني، فنظنّ أنّ التنزيل توقّف. وكانت هناك مشكلة
  ثانية أسوأ: عند فشل الطريق الذكي كان السكربت يكتب **ثابتًا 40٪** وينتظر pip،
  فيبقى الشريط جامدًا على 40 حتى ينتهي التثبيت — وهو بالضبط ما ظهر في اللقطة.

الحلّ في هذه النسخة:

  1. **مراحل بأوزان صريحة**: فحص المخزن 0‑3، حلّ الإصدارات 4‑12، التنزيل 12‑82
     (بايتات حقيقية)، البناء والتثبيت 82‑99، النهاية 100.
  2. **نبضة (heartbeat)**: خيط خلفي يحرّك النسبة كل ٠٫٤ ثانية عندما تصمت الطور
     الحالي، لكن بحدّ أقصى «آخر نسبة حقيقية + ١٥» — فالرقم يتحرّك دائمًا ولا يكذب.
  3. **النسبة لا تعود للخلف أبدًا** (monotonic).
  4. **اكتشاف التوقّف**: إن مرّت ٦ ثوانٍ بلا بايت جديد تُعلَّم الحالة ``stalled``
     فتعرض البطاقة «شبكة بطيئة…» بدل أن تبدو ميّتة.
  5. **مرايا**: إن فشل حلّ الإصدارات من المصدر الأساسي نعيد المحاولة على مرايا
     PyPI (تُجرَّب بالترتيب، لا تُفرض).

يكتب الحالة في ``~/.mahdi/progress.json`` لتقرأها بطاقة التطبيق.
"""

import json
import os
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

# يتجاوز حماية PEP 668 (Ubuntu الحديثة) حتى يعمل التثبيت داخل الطرفية
os.environ.setdefault("PIP_BREAK_SYSTEM_PACKAGES", "1")
os.environ.setdefault("PIP_DISABLE_PIP_VERSION_CHECK", "1")
os.environ.setdefault("PIP_NO_INPUT", "1")
os.environ.setdefault("PIP_PROGRESS_BAR", "off")
os.environ.setdefault("PYTHONUNBUFFERED", "1")
os.environ.setdefault("PIP_ROOT_USER_ACTION", "ignore")

HOME = os.path.expanduser("~")
PROG_DIR = os.path.join(HOME, ".mahdi")
PROG_FILE = os.path.join(PROG_DIR, "progress.json")

# مخزن دائم داخل التطبيق: الحزم المنزَّلة تبقى هنا ولا تُنزَّل مرة ثانية
CACHE_DIR = os.path.join(HOME, ".mahdi", "cache", "wheels")
MANIFEST = os.path.join(HOME, ".mahdi", "cache", "installed.json")
os.environ.setdefault("PIP_CACHE_DIR", os.path.join(HOME, ".mahdi", "cache", "pip"))

SESSION_ID = "%d-%d" % (int(time.time()), os.getpid())
NAME = ""

# مرايا تُجرَّب فقط عند فشل المصدر الأساسي — لا تُفرض على المستخدم
MIRRORS = [
    "https://pypi.org/simple",
    "https://pypi.tuna.tsinghua.edu.cn/simple",
    "https://mirrors.aliyun.com/pypi/simple",
]

# ————————————————————————————— الترجمة الإنجليزية —————————————————————————————

STAGE_EN = {
    "جارٍ البدء…": "Starting…",
    "التحقق من المخزن": "Checking local cache",
    "مثبّتة مسبقًا — من المخزن": "Already installed — from cache",
    "التحقق من الإصدارات": "Resolving versions",
    "تنزيل الحزم": "Downloading packages",
    "تثبيت المكتبة": "Installing library",
    "بناء المكتبة": "Building package",
    "تثبيت مباشر بـ pip…": "Installing with pip…",
    "تجربة مرآة أسرع": "Trying a mirror",
    "تم التثبيت بنجاح": "Installed successfully",
    "تعذّر تثبيت المكتبة": "Installation failed",
    "شبكة بطيئة…": "Slow network…",
}


def human(size):
    try:
        size = float(size)
    except Exception:
        return ""
    for unit in ("B", "KB", "MB", "GB"):
        if size < 1024 or unit == "GB":
            return ("%.0f %s" % (size, unit)) if unit == "B" else ("%.1f %s" % (size, unit))
        size /= 1024.0
    return ""


# ———————————————————————————————— متتبّع التقدّم ————————————————————————————————

# حدود كل طور: (أرضية، سقف)
PHASE_RANGES = {
    "cache": (0, 3),
    "resolve": (4, 12),
    "download": (12, 82),
    "install": (82, 99),
}


class Tracker:
    """نسبة تقدّم واحدة لا تتوقّف ولا ترتدّ.

    - ``real(percent, stage)`` تحديث حقيقي مبني على بايتات/خطوات فعلية.
    - نبضة خلفية تحرّك النسبة بين التحديثات الحقيقية بحدّ ``CREEP_AHEAD`` فقط.
    """

    CREEP_AHEAD = 15      # أقصى تقدّم «تخميني» فوق آخر نسبة حقيقية
    CREEP_INTERVAL = 0.9  # شريحة التخمين: ١٪ كل ٠٫٩ ثانية
    TICK = 0.4            # دقّة النبضة
    STALL_AFTER = 6.0     # ثوانٍ بلا تقدّم حقيقي ⇒ «شبكة بطيئة»

    def __init__(self):
        self._lock = threading.Lock()
        self._percent = 0.0
        self._floor = 0
        self._ceiling = 3
        self._stage = "جارٍ البدء…"
        self._detail = ""
        self._started = time.time()
        self._real_ts = time.time()
        self._real_percent = 0.0
        self._last_creep = time.time()
        self._bytes = 0
        self._total = 0
        self._done = False
        self._last_flush = 0.0
        self._last_pct = -1
        self._rate_ts = time.time()
        self._rate_bytes = 0
        self._rate = 0.0
        self._thread = threading.Thread(target=self._beat, daemon=True)
        self._thread.start()

    # —— واجهة الاستخدام ——

    def phase(self, name, stage=None, detail=None):
        with self._lock:
            floor, ceiling = PHASE_RANGES.get(name, (self._floor, self._ceiling))
            self._floor, self._ceiling = floor, ceiling
            if stage:
                self._stage = stage
            if detail is not None:
                self._detail = detail
            if self._percent < floor:
                self._percent = floor
            if self._real_percent < floor:
                self._real_percent = floor
        self._flush()

    def real(self, percent, stage=None, detail=None, floor=None, ceiling=None):
        now = time.time()
        with self._lock:
            if floor is not None:
                self._floor = floor
            if ceiling is not None:
                self._ceiling = ceiling
            if stage:
                self._stage = stage
            if detail is not None:
                self._detail = detail
            target = max(self._floor, min(self._ceiling, float(percent)))
            if target > self._percent:
                self._percent = target
            if target > self._real_percent:
                self._real_percent = target
            self._real_ts = now
            self._last_creep = now
        self._flush()

    def bytes(self, got, total):
        """يحدّث عدّاد البايتات ويحوّله إلى نسبة داخل طور التنزيل."""
        now = time.time()
        with self._lock:
            self._bytes, self._total = got, total
            elapsed = now - self._rate_ts
            if elapsed >= 1.0:
                delta = got - self._rate_bytes
                if delta >= 0:
                    self._rate = 0.7 * self._rate + 0.3 * (delta / elapsed)
                self._rate_ts, self._rate_bytes = now, got
        if total > 0:
            floor, ceiling = PHASE_RANGES["download"]
            pct = floor + (ceiling - floor) * min(1.0, got / float(total))
            detail = "%s / %s" % (human(got), human(total))
        else:
            pct = PHASE_RANGES["download"][0]
            detail = human(got)
        # السرعة والوقت المتبقي: معلومتان تخفّفان الإحساس بالانتظار (8.1.0)
        if self._rate > 2048:
            detail += " · %s/s" % human(self._rate)
            if total > got:
                detail += " · %ds" % int((total - got) / self._rate)
        self.real(pct, "تنزيل الحزم", detail)

    def finish(self, stage="تم التثبيت بنجاح", detail="", ok=True):
        with self._lock:
            self._done = True
            self._percent = 100.0
            self._floor, self._ceiling = 100, 100
            self._stage = stage
            if detail:
                self._detail = detail
        self._flush(force=True, ok=ok)

    # —— الداخل ——

    def _beat(self):
        while True:
            time.sleep(self.TICK)
            with self._lock:
                if self._done:
                    return
                now = time.time()
                if now - self._real_ts < self.CREEP_INTERVAL:
                    continue
                # السقف محسوب على آخر نسبة **حقيقية** لا على الحالية، وإلا لتسلّق
                # التخمين نفسه إلى ما لا نهاية وصار الرقم كذبًا صريحًا.
                cap = min(self._ceiling - 1, self._real_percent + self.CREEP_AHEAD)
                if self._percent >= cap:
                    continue
                steps = max(1, int((now - self._last_creep) / self.CREEP_INTERVAL))
                self._percent = min(cap, self._percent + steps)
                self._last_creep = now
            self._flush()

    def _flush(self, force=False, ok=True):
        # لا نكتب الملف مع كل قطعة 128KB: عشر مرات في الثانية تكفي، والبطاقة
        # تقرأ كل ٢٦٠ مللي ثانية أصلًا.
        now = time.time()
        pct_now = int(round(self._percent))
        if not force and now - self._last_flush < 0.2 and pct_now == self._last_pct:
            return
        self._last_flush, self._last_pct = now, pct_now
        try:
            os.makedirs(PROG_DIR, exist_ok=True)
            with self._lock:
                percent = int(max(0, min(100, round(self._percent))))
                stalled = (not self._done) and (time.time() - self._real_ts) > self.STALL_AFTER
                stage = "شبكة بطيئة…" if (stalled and self._stage in ("تنزيل الحزم", "التحقق من الإصدارات")) else self._stage
                payload = {
                    "id": SESSION_ID,
                    "name": NAME,
                    "stage": stage,
                    "stage_en": STAGE_EN.get(stage, stage),
                    "detail": self._detail,
                    "percent": percent,
                    "done": bool(self._done),
                    "pid": os.getpid(),
                    "ts": int(time.time()),
                    "phase": self._stage,
                    "bytes": self._bytes,
                    "total_bytes": self._total,
                    "stalled": bool(stalled),
                    "eta": int((self._total - self._bytes) / self._rate) if (self._rate > 1024 and self._total > self._bytes) else 0,
                }
            tmp = "%s.tmp%d" % (PROG_FILE, os.getpid())
            with open(tmp, "w", encoding="utf-8") as fh:
                json.dump(payload, fh, ensure_ascii=False)
            os.replace(tmp, PROG_FILE)
        except Exception:
            pass


TRACKER = None


def tracker():
    global TRACKER
    if TRACKER is None:
        TRACKER = Tracker()
    return TRACKER


# ————————————————————————————— أدوات مساعدة —————————————————————————————


def net_ok(host="pypi.org", timeout=6.0):
    """هل يوجد اتصال حقيقي (DNS) قبل إزعاج المستخدم بمحاولات pip فاشلة؟"""
    if os.environ.get("MAHDI_NET_CHECK", "1") == "0":
        return True
    try:
        socket.setdefaulttimeout(timeout)
        return bool(socket.getaddrinfo(host, 443, proto=socket.IPPROTO_TCP))
    except Exception:
        return False


def file_name(url):
    return os.path.basename(url.split("?")[0]) or "package"


def cached_path(url):
    candidate = os.path.join(CACHE_DIR, file_name(url))
    return candidate if os.path.exists(candidate) else None


def cached_versions():
    try:
        with open(MANIFEST, encoding="utf-8") as fh:
            return json.load(fh)
    except Exception:
        return {}


def remember_installed(items):
    """يحفظ سجلًّا بما ثُبِّت ليُعرف في المرات القادمة بلا شبكة."""
    try:
        os.makedirs(os.path.dirname(MANIFEST), exist_ok=True)
        data = cached_versions()
        now = int(time.time())
        for name, version, _ in items:
            data[name.lower()] = {"version": version, "ts": now}
        tmp = MANIFEST + ".tmp"
        with open(tmp, "w", encoding="utf-8") as fh:
            json.dump(data, fh, ensure_ascii=False, indent=0)
        os.replace(tmp, MANIFEST)
    except Exception:
        pass


_ROOT_FLAG = None


def root_flag():
    """pip الأقدم من 22.1 لا يعرف --root-user-action، فنفحص الإصدار مرة واحدة."""
    global _ROOT_FLAG
    if _ROOT_FLAG is None:
        _ROOT_FLAG = []
        try:
            out = subprocess.run(
                [sys.executable, "-m", "pip", "--version"],
                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=20,
            ).stdout.decode("utf-8", "ignore")
            version = out.split(" ")[1] if " " in out else ""
            parts = version.split(".")
            if len(parts) >= 2 and (int(parts[0]), int(parts[1])) >= (22, 1):
                _ROOT_FLAG = ["--root-user-action", "ignore"]
        except Exception:
            pass
    return _ROOT_FLAG


def pip_cmd(*args):
    return [sys.executable, "-m", "pip", "--disable-pip-version-check", "--no-input"] + list(args)


# ————————————————————————————— التنزيل المعروض —————————————————————————————


def content_length(url):
    """حجم الملف من ترويسة HEAD (0 إن مُنع)."""
    try:
        req = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "pip/24"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            return int(resp.headers.get("Content-Length") or 0)
    except Exception:
        return 0


class Counter:
    """عدّاد بايتات مشترك بين خيوط التنزيل.

    كل ملف يساهم بمقدار مستقل (``set_file`` للملفات التي نقيسها من حجمها على
    القرص، و``add`` للملفات التي نستقبل بايتاتها مباشرة) — فتبقى النسبة صحيحة
    حتى لو نُزّلت بعض الملفات بـaria2 بثمانية اتصالات.
    """

    def __init__(self, total):
        self.lock = threading.Lock()
        self.total = total
        self.contrib = {}

    def add(self, key, size):
        with self.lock:
            self.contrib[key] = self.contrib.get(key, 0) + size
            got, total = sum(self.contrib.values()), self.total
        tracker().bytes(got, total)

    def set_file(self, key, size):
        with self.lock:
            self.contrib[key] = max(self.contrib.get(key, 0), size)
            got, total = sum(self.contrib.values()), self.total
        tracker().bytes(got, total)


# الملفات الأكبر من هذا الحجم تُنزَّل بـaria2 (ثمانية اتصالات) — أسرع بكثير على
# شبكات الهاتف التي تُحدّد سرعة كل اتصال على حدة. الأصغر تبقى بـurllib (أخفّ).
ARIA_MIN_BYTES = 4 * 1024 * 1024


def aria2_available():
    try:
        return subprocess.run(["which", "aria2c"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
    except Exception:
        return False


def aria2_download(url, dest, counter, key):
    """تنزيل متعدد الاتصالات مع متابعة الحجم على القرص (نسبة حقيقية)."""
    name = os.path.basename(dest)
    directory = os.path.dirname(dest)
    try:
        proc = subprocess.Popen(
            ["aria2c", "-x8", "-s8", "-k1M", "--allow-overwrite=true",
             "--auto-file-renaming=false", "--console-log-level=error",
             "--summary-interval=0", "-d", directory, "-o", name, url],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
    except Exception:
        return False
    while proc.poll() is None:
        try:
            counter.set_file(key, os.path.getsize(dest))
        except Exception:
            pass
        time.sleep(0.35)
    try:
        if proc.returncode == 0 and os.path.getsize(dest) > 0:
            counter.set_file(key, os.path.getsize(dest))
            return True
    except Exception:
        pass
    return False


def download_one(url, dest_dir, counter, key, expected=0, attempts=3):
    """ينزّل ملفًا واحدًا مع إعادة محاولة، ويحدّث العدّاد (بايتات حقيقية)."""
    dest = os.path.join(dest_dir, file_name(url))

    if (expected or 0) >= ARIA_MIN_BYTES and aria2_available():
        if aria2_download(url, dest, counter, key):
            return dest

    for attempt in range(attempts):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "pip/24"})
            with urllib.request.urlopen(req, timeout=25) as resp:
                with open(dest, "wb") as out:
                    while True:
                        chunk = resp.read(131072)
                        if not chunk:
                            break
                        out.write(chunk)
                        counter.add(key, len(chunk))
            if os.path.getsize(dest) > 0:
                return dest
        except Exception:
            # لا نضاعف العدّاد عند إعادة المحاولة: نعيد ضبط مساهمة هذا الملف
            counter.set_file(key, 0)
            if attempt + 1 < attempts:
                time.sleep(1.2 * (attempt + 1))
    return None


def plan_downloads(items, counter):
    """يحسب الحجم الكلي قبل بدء التنزيل حتى تكون النسبة صادقة من أول بايت.

    يعيد [(url, size)] للملفات الناقصة فقط (المخزن الداخلي لا يُنزَّل مرّتين).
    """
    pending = [item for item in items if not cached_path(item[2])]
    if not pending:
        return []
    with ThreadPoolExecutor(max_workers=8) as pool:
        sizes = list(pool.map(lambda item: content_length(item[2]), pending))
    known = [s for s in sizes if s > 0]
    fallback = int(sum(known) / len(known)) if known else 2 * 1024 * 1024
    resolved = [(item[2], s if s > 0 else fallback) for item, s in zip(pending, sizes)]
    counter.total = sum(size for _, size in resolved)
    tracker().bytes(0, counter.total)
    return resolved


def resolve(targets, index=None):
    """يعيد [(اسم، إصدار، رابط)] لكل ما ينبغي تثبيته، أو None عند الفشل."""
    report = os.path.join(tempfile.mkdtemp(prefix="mahdi-report-"), "report.json")
    cmd = pip_cmd("install", "--dry-run", "--ignore-installed", "--quiet", "--report", report, *targets)
    if index:
        cmd += ["-i", index]
    try:
        proc = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=600)
    except Exception:
        return None
    if proc.returncode != 0 or not os.path.exists(report):
        return None
    try:
        with open(report, encoding="utf-8") as fh:
            data = json.load(fh)
    except Exception:
        return None
    items = []
    for entry in data.get("install", []):
        meta = entry.get("metadata") or {}
        name = meta.get("name")
        version = meta.get("version")
        url = (entry.get("download_info") or {}).get("url")
        if name and url:
            items.append((name, version or "", url))
    return items or None


def install_from_dir(directory, items):
    """تثبيت من الملفات المنزَّلة فقط: بلا شبكة، وسريع (هنا يحدث البناء/فكّ الضغط)."""
    specs = ["%s==%s" % (n, v) if v else n for n, v, _ in items]
    cmd = pip_cmd(
        "install", "--upgrade", "--no-deps", "--find-links", directory,
        "--prefer-binary", "--retries", "3", "--timeout", "30", "--quiet",
        *root_flag(), *specs,
    )
    try:
        return subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
    except Exception:
        return False


def plain_install(targets, index=None):
    """الطريق التقليدي — والنبضة تُحرّك النسبة أثناءه فلا تتوقّف عند رقم ثابت."""
    tracker().phase("install", "تثبيت المكتبة", NAME)
    cmd = pip_cmd("install", "--upgrade", "--prefer-binary", "--progress-bar", "off",
                  "--retries", "5", "--timeout", "30", *targets)
    if index:
        cmd += ["-i", index]
    try:
        return subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
    except Exception:
        return False


def try_offline(targets):
    """تثبيت من المخزن الداخلي فقط: بلا شبكة، وبلا تنزيل، وسريع جدًا."""
    os.makedirs(CACHE_DIR, exist_ok=True)
    if not os.listdir(CACHE_DIR):
        return False
    cmd = pip_cmd(
        "install", "--upgrade", "--no-index", "--find-links", CACHE_DIR,
        "--retries", "1", "--timeout", "10", "--quiet", *root_flag(), *targets,
    )
    try:
        return subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=600).returncode == 0
    except Exception:
        return False


def main(argv):
    global NAME
    if not argv:
        return 0

    targets = []
    it = iter(argv)
    for arg in it:
        if arg == "--name":
            NAME = next(it, "") or ""
        else:
            targets.append(arg)
    if not targets:
        return 0
    if not NAME:
        NAME = targets[0].replace("-r", "").strip() or "Python"

    t = tracker()

    # ٠) المخزن الداخلي أولًا: المكتبة المثبّتة سابقًا لا تُنزَّل مرة أخرى
    t.phase("cache", "التحقق من المخزن", NAME)
    if try_offline(targets):
        t.finish("مثبّتة مسبقًا — من المخزن")
        return 0

    # ٠٫٥) لا شبكة؟ سطر عربي واحد واضح، بلا عشر محاولات pip فاشلة
    if not net_ok():
        sys.stdout.write("⚠ لا يوجد اتصال بالإنترنت — لم تُثبَّت: %s\n" % (NAME or targets[0]))
        sys.stdout.flush()
        t.finish("تعذّر تثبيت المكتبة")
        return 2

    # ١) حلّ الإصدارات — ثم مرايا عند الفشل
    t.phase("resolve", "التحقق من الإصدارات", NAME)
    items = resolve(targets)
    index_used = None
    if not items:
        for mirror in MIRRORS[1:]:
            if not net_ok(mirror.split("//")[-1].split("/")[0]):
                continue
            t.phase("resolve", "تجربة مرآة أسرع", mirror.split("//")[-1].split("/")[0])
            items = resolve(targets, mirror)
            if items:
                index_used = mirror
                break

    if items:
        try:
            os.makedirs(CACHE_DIR, exist_ok=True)
        except Exception:
            pass

        t.phase("download", "تنزيل الحزم", "%d ملف" % len(items))
        counter = Counter(0)
        pending = plan_downloads(items, counter)
        if pending:
            with ThreadPoolExecutor(max_workers=6) as pool:
                results = list(
                    pool.map(
                        lambda job: download_one(job[0], CACHE_DIR, counter, job[0], job[1]),
                        pending,
                    )
                )
            ok = all(results)
        else:
            ok = True
            t.real(80, "تنزيل الحزم", "من المخزن")

        if ok:
            # ٢) البناء والتثبيت: النبضة تُحرّك 82→99 أثناء بناء الـ wheel
            t.phase("install", "بناء المكتبة", NAME)
            if install_from_dir(CACHE_DIR, items):
                remember_installed(items)
                t.finish("تم التثبيت بنجاح")
                return 0

    # ٣) الطريق التقليدي (أو مرآة إن نجحت)
    if plain_install(targets, index_used):
        t.finish("تم التثبيت بنجاح")
        return 0

    t.finish("تعذّر تثبيت المكتبة")
    return 1


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except KeyboardInterrupt:
        sys.exit(130)
