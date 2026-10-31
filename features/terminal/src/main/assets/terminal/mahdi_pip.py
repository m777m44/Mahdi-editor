#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""تثبيت حزم بايثون مع نسبة تقدّم حقيقية من 0 إلى 100.

الطريقة:
  1. ``pip install --dry-run --report`` لحلّ الاعتماديات فقط (بلا تنزيل).
  2. تنزيل كل ملف (wheel/sdist) بالتوازي، ونحن نعرف حجم كل ملف من ترويسة
     ``Content-Length``، فتصبح النسبة نسبة بايتات حقيقية لا تخمينًا.
  3. ``pip install --find-links`` من الملفات المنزَّلة (تثبيت فوري بلا شبكة).

إن فشل أي طور (إصدار pip قديم، اعتمادية VCS، شبكة…) نرجع إلى ``pip install``
العادي مع رسائل أطوار، فلا يتعطّل شيء.

يكتب الحالة في ``~/.mahdi/progress.json`` ليقرأها التطبيق ويعرضها في بطاقة أنيقة.
"""

import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

# يتجاوز حماية PEP 668 (Ubuntu الحديثة) حتى يعمل التثبيت داخل الطرفية كما في السابق
os.environ.setdefault("PIP_BREAK_SYSTEM_PACKAGES", "1")
os.environ.setdefault("PIP_DISABLE_PIP_VERSION_CHECK", "1")
os.environ.setdefault("PIP_NO_INPUT", "1")
os.environ.setdefault("PYTHONUNBUFFERED", "1")
# إخفاء تحذير «Running pip as the 'root' user» الذي يزحم الطرفية بلا فائدة
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

_lock = threading.Lock()
_state = {
    "total": 0,      # مجموع أحجام ملفات التنزيل (بايت)
    "downloaded": 0,  # ما نُزّل حتى الآن
    "files": 0,
    "files_done": 0,
    "sized": False,   # هل عُرفت الأحجام مسبقًا (طلب HEAD)؟
}
_last_emit = [0.0]

# ترجمة أسماء الأطوار للتطبيق ثنائي اللغة
STAGE_EN = {
    "جارٍ البدء…": "Starting…",
    "التحقق من الإصدارات": "Resolving versions",
    "تنزيل الحزم": "Downloading packages",
    "تنزيل الحزمة": "Downloading package",
    "تثبيت المكتبة": "Installing library",
    "تثبيت مباشر بـ pip…": "Installing with pip…",
    "تم التثبيت بنجاح": "Installed successfully",
    "تعذّر تثبيت المكتبة": "Installation failed",
    "إعادة المحاولة بمصدر بديل": "Retrying with a mirror",
    "%d ملف": "files",
    "تثبيت مباشر": "Direct install",
    "التحقق من المخزن": "Checking local cache",
    "مثبّتة مسبقًا — من المخزن": "Already installed — from cache",
}


def emit(percent, stage, detail="", done=False):
    """يكتب حالة التقدّم بشكل ذرّي حتى لا يقرأ التطبيق ملفًا نصف مكتوب."""
    try:
        os.makedirs(PROG_DIR, exist_ok=True)
        payload = {
            "id": SESSION_ID,
            "name": NAME,
            "stage": stage,
            "stage_en": STAGE_EN.get(stage, stage),
            "detail": detail,
            "percent": int(max(0, min(100, round(percent)))),
            "done": bool(done),
            "pid": os.getpid(),
            "ts": int(time.time()),
        }
        tmp = "%s.tmp%d" % (PROG_FILE, os.getpid())
        with open(tmp, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, ensure_ascii=False)
        os.replace(tmp, PROG_FILE)
    except Exception:
        pass


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


def _report_download(force=False):
    now = time.time()
    with _lock:
        total = _state["total"]
        got = _state["downloaded"]
        files = _state["files"]
        done_files = _state["files_done"]
    if not force and now - _last_emit[0] < 0.25:
        return
    _last_emit[0] = now
    if total > 0:
        ratio = min(1.0, got / float(total))
        percent = 5 + 80 * ratio
        detail = "%s / %s · %d/%d ملف" % (human(got), human(total), done_files, files)
    else:
        percent = 5
        detail = "%d/%d ملف" % (done_files, files)
    emit(percent, "تنزيل الحزم", detail)


def _content_length(url):
    """حجم الملف من ترويسة HEAD (يُستخدم لجمع المقام الكلي قبل التنزيل)."""
    try:
        req = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "pip/24"})
        with urllib.request.urlopen(req, timeout=20) as resp:
            return int(resp.headers.get("Content-Length") or 0)
    except Exception:
        return 0


def _download_one(url, dest_dir):
    name = os.path.basename(url.split("?")[0]) or "package"
    dest = os.path.join(dest_dir, name)
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "pip/24"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            length = int(resp.headers.get("Content-Length") or 0)
            with _lock:
                if length and not _state["sized"]:
                    # احتياط: لم تُعرف الأحجام مسبقًا
                    _state["total"] += length
            with open(dest, "wb") as out:
                while True:
                    chunk = resp.read(262144)
                    if not chunk:
                        break
                    out.write(chunk)
                    with _lock:
                        _state["downloaded"] += len(chunk)
                    _report_download()
        with _lock:
            _state["files_done"] += 1
        _report_download(force=True)
        return dest
    except Exception:
        with _lock:
            _state["files_done"] += 1
        return None


def file_name(url):
    """اسم ملف الحزمة من رابط التنزيل (يُستخدم مفتاحًا في المخزن)."""
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


def pip_cmd(*args):
    return [sys.executable, "-m", "pip", "--disable-pip-version-check", "--no-input"] + list(args)


def resolve(targets):
    """يعيد قائمة (اسم، إصدار، رابط) لكل ما ينبغي تثبيته، أو None عند الفشل."""
    report = os.path.join(tempfile.mkdtemp(prefix="mahdi-report-"), "report.json")
    cmd = pip_cmd(
        "install", "--dry-run", "--ignore-installed", "--quiet",
        "--report", report, *targets,
    )
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
    specs = ["%s==%s" % (n, v) if v else n for n, v, _ in items]
    cmd = pip_cmd(
        "install", "--upgrade", "--no-deps", "--find-links", directory,
        "--prefer-binary", "--retries", "3", "--timeout", "30",
        "--root-user-action", "ignore", "--quiet", *specs,
    )
    try:
        proc = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception:
        return False
    return proc.returncode == 0


def plain_install(targets):
    """الطريق التقليدي: pip يتولّى كل شيء (تقدّم بالمراحل)."""
    emit(10, "تثبيت المكتبة", NAME)
    cmd = pip_cmd("install", "--upgrade", "--prefer-binary", "--progress-bar", "off",
                  "--retries", "5", "--timeout", "30", *targets)
    try:
        proc = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception:
        return False
    if proc.returncode == 0:
        return True
    emit(60, "إعادة المحاولة بمصدر بديل", NAME)
    cmd = pip_cmd("install", "--upgrade", "--prefer-binary", "--progress-bar", "off",
                  "--retries", "3", "--timeout", "30", "-i", "https://pypi.org/simple", *targets)
    try:
        return subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
    except Exception:
        return False


def try_offline(targets, label):
    """تثبيت من المخزن الداخلي فقط: بلا شبكة، وبلا تنزيل، وسريع جدًا."""
    os.makedirs(CACHE_DIR, exist_ok=True)
    if not os.listdir(CACHE_DIR):
        return False
    cmd = pip_cmd(
        "install", "--upgrade", "--no-index", "--find-links", CACHE_DIR,
        "--disable-pip-version-check", "--root-user-action", "ignore",
        "--retries", "1", "--timeout", "10", "--quiet", *targets,
    )
    try:
        proc = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=600)
    except Exception:
        return False
    return proc.returncode == 0


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

    # ٠) المخزن الداخلي أولًا: المكتبة المثبّتة سابقًا لا تُنزَّل مرة أخرى
    emit(2, "التحقق من المخزن", NAME)
    if try_offline(targets, NAME):
        emit(100, "مثبّتة مسبقًا — من المخزن", NAME, done=True)
        return 0

    emit(4, "التحقق من الإصدارات", NAME)
    items = resolve(targets)

    if items:
        try:
            os.makedirs(CACHE_DIR, exist_ok=True)
        except Exception:
            pass

        with _lock:
            _state.update(
                {"total": 0, "downloaded": 0, "files": len(items), "files_done": 0, "sized": False}
            )

        pending = [item for item in items if not cached_path(item[2])]
        cached_count = len(items) - len(pending)
        emit(
            5,
            "تنزيل الحزم",
            "%d ملف · %d من المخزن" % (len(items), cached_count) if cached_count else "%d ملف" % len(items),
        )

        if pending:
            with ThreadPoolExecutor(max_workers=8) as pool:
                sizes = list(pool.map(lambda item: _content_length(item[2]), pending))
            with _lock:
                _state["total"] = sum(sizes)
                _state["sized"] = True

            with ThreadPoolExecutor(max_workers=6) as pool:
                results = list(pool.map(lambda item: _download_one(item[2], CACHE_DIR), pending))
            downloaded_ok = all(results)
        else:
            downloaded_ok = True

        if downloaded_ok:
            emit(97, "تثبيت المكتبة", NAME)
            if install_from_dir(CACHE_DIR, items):
                remember_installed(items)
                emit(100, "تم التثبيت بنجاح", NAME, done=True)
                return 0

    if plain_install(targets):
        emit(100, "تم التثبيت بنجاح", NAME, done=True)
        return 0

    emit(100, "تعذّر تثبيت المكتبة", NAME, done=True)
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
