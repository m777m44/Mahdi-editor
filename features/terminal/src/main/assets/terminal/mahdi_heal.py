#!/usr/bin/env python3
"""مساعد «التثبيت بعد الفشل» (5.5.0).

يقرأ ملف stderr ناتجًا عن تشغيل سكربت فاشل، ويستخرج منه أسماء المكتبات الناقصة
ويحوّلها إلى أسماء حزم قابلة للتثبيت فعليًا:

    python3 mahdi_heal.py --lang python --stderr /tmp/err.log
    → يطبع اسم حزمة واحدة في كل سطر (فارغ إن لم يوجد شيء)

لا يطبع أي شيء إلى stderr إلا عند خطأ داخلي، ولا يعتمد على أي مكتبة خارجية.
"""
from __future__ import annotations

import argparse
import importlib.metadata
import os
import re
import sys

# ————————————————————————— بايثون —————————————————————————

# أنماط الخطأ الحقيقية التي يطبعها المفسّر عند غياب وحدة
PY_PATTERNS = (
    re.compile(r"ModuleNotFoundError: No module named ['\"]([\w.\-]+)['\"]"),
    re.compile(r"ImportError: No module named ['\"]?([\w.\-]+)['\"]?"),
    re.compile(r"ModuleNotFoundError: No module named ['\"]?([\w.\-]+)"),
)

# 8.4.0 — «مكتبة موجودة لكنها خاطئة/قديمة»
# أشهر مثال: `AttributeError: module 'telebot' has no attribute 'AsyncTeleBot'`.
# الوحدة تُستورد بنجاح فلا يظهر ModuleNotFoundError، لكن الحزمة المثبّتة إمّا
# نسخة قديمة من pyTelegramBotAPI (قبل 4.x) أو حزمة PyPI أخرى اسمها `telebot`
# تحجب الحزمة الصحيحة. لا يُصلحهما إلا ترقية/إزالة، لا «تثبيت» عادي.
PY_BROKEN_PATTERNS = (
    re.compile(r"AttributeError: module ['\"]([\w.\-]+)['\"] has no attribute ['\"]([\w]+)['\"]"),
    re.compile(r"ImportError: cannot import name ['\"][^'\"]+['\"] from ['\"]([\w.\-]+)['\"]"),
    re.compile(r"TypeError: ['\"]module['\"] object is not callable"),
    re.compile(r"AttributeError: partially initialized module ['\"]([\w.\-]+)['\"]"),
)

# فحوص وقائية: إن استخدم السكربت ميزة لا توفّرها النسخة القديمة، نُصلح قبل التشغيل
FEATURE_PROBES = {
    "telebot": ("AsyncTeleBot", "pytelegrambotapi"),
    "telegram": ("ext", "python-telegram-bot"),
    "PIL": ("ImageOps", "pillow"),
    "cv2": ("data", "opencv-python"),
}

# اسم الوحدة → اسم حزمة PyPI (الاستيراد غالبًا لا يطابق اسم الحزمة)
PY_DIST = {
    "cv2": "opencv-python",
    "PIL": "pillow",
    "sklearn": "scikit-learn",
    "skimage": "scikit-image",
    "yaml": "pyyaml",
    "bs4": "beautifulsoup4",
    "docx": "python-docx",
    "pptx": "python-pptx",
    "openpyxl": "openpyxl",
    "dotenv": "python-dotenv",
    "dateutil": "python-dateutil",
    "Crypto": "pycryptodome",
    "Cryptodome": "pycryptodomex",
    "OpenSSL": "pyopenssl",
    "serial": "pyserial",
    "MySQLdb": "mysqlclient",
    "psycopg2": "psycopg2-binary",
    "fitz": "pymupdf",
    "attr": "attrs",
    "jwt": "pyjwt",
    "gi": "pygobject",
    "wx": "wxpython",
    "usb": "pyusb",
    "magic": "python-magic",
    "telebot": "pytelegrambotapi",
    "telegram": "python-telegram-bot",
    "pkg_resources": "setuptools",
    "setuptools": "setuptools",
    "requests_cache": "requests-cache",
    "numpy": "numpy",
    "pandas": "pandas",
    "matplotlib": "matplotlib",
    "flask": "flask",
    "django": "django",
    "fastapi": "fastapi",
    "uvicorn": "uvicorn",
    "aiohttp": "aiohttp",
    "httpx": "httpx",
    "pydantic": "pydantic",
    "sqlalchemy": "sqlalchemy",
    "qdrant_client": "qdrant-client",
    "google": "google-api-python-client",
    "win32com": "pywin32",
    "tkinter": None,  # جزء من النظام، لا يُثبَّت بـ pip
}


def python_stdlib() -> set[str]:
    names = set(getattr(sys, "stdlib_module_names", ()) or ())
    # احتياط لنسخ بايثون الأقدم من 3.10
    names.update(
        {
            "os",
            "sys",
            "re",
            "json",
            "math",
            "time",
            "random",
            "typing",
            "pathlib",
            "subprocess",
            "threading",
            "socket",
            "sqlite3",
            "csv",
            "datetime",
            "collections",
            "itertools",
            "functools",
            "hashlib",
            "logging",
            "unittest",
            "asyncio",
            "uuid",
            "base64",
            "shutil",
            "tempfile",
            "textwrap",
            "urllib",
            "http",
            "email",
            "html",
            "xml",
            "zipfile",
            "tarfile",
            "gzip",
            "argparse",
            "copy",
            "glob",
            "io",
            "abc",
            "enum",
            "dataclasses",
            "struct",
            "traceback",
            "warnings",
            "weakref",
            "contextlib",
            "inspect",
            "importlib",
            "statistics",
            "secrets",
            "platform",
            "signal",
            "queue",
            "heapq",
            "bisect",
            "calendar",
            "decimal",
            "fractions",
            "numbers",
            "pprint",
            "string",
            "operator",
            "pickle",
            "shelve",
            "dbm",
            "crypt",
            "getpass",
            "gettext",
            "locale",
            "codecs",
            "unicodedata",
            "venv",
            "ctypes",
            "multiprocessing",
            "concurrent",
            "sched",
            "select",
            "selectors",
            "ssl",
            "socketserver",
            "http",
            "ftplib",
            "smtplib",
            "imaplib",
            "poplib",
            "telnetlib",
            "uuid",
            "graphlib",
            "tomllib",
            "zoneinfo",
        }
    )
    return names


def find_python_modules(text: str, script: str | None) -> list[str]:
    found: list[str] = []
    for pattern in PY_PATTERNS:
        for match in pattern.finditer(text):
            name = match.group(1).strip()
            if not name:
                continue
            top = name.split(".")[0]
            if not top or not re.match(r"^[A-Za-z_][\w]*$", top):
                continue
            if top in found:
                continue
            found.append(top)
    return found


def local_modules(script: str | None) -> set[str]:
    if not script:
        return set()
    root = os.path.dirname(os.path.abspath(script))
    names = set()
    try:
        for entry in os.listdir(root):
            if entry.endswith(".py"):
                names.add(entry[:-3])
            elif os.path.isdir(os.path.join(root, entry)):
                names.add(entry)
    except OSError:
        pass
    return names


def to_packages(modules: list[str], script: str | None) -> list[str]:
    stdlib = python_stdlib()
    locals_ = local_modules(script)
    packages: list[str] = []
    for module in modules:
        if module in stdlib or module in locals_ or module.startswith("_"):
            continue
        dist = PY_DIST.get(module, module)
        if not dist:
            continue
        dist = dist.strip()
        if dist and dist not in packages:
            packages.append(dist)
    return packages


def broken_actions(text: str, script: str | None) -> list[str]:
    """أوامر إصلاح لمكتبة مستوردة لكنها خاطئة: remove: / upgrade: / shadow:.

    `remove:` فقط إن كانت هناك توزيعة **أخرى** تحمل اسم الوحدة نفسها مثبّتة
    فعلًا (مثل حزمة `telebot` الصغيرة التي تحجب pyTelegramBotAPI).
    `shadow:` إن وُجد ملف محلي `<module>.py` بجانب السكربت يحجب المكتبة.
    """
    modules: list[str] = []
    for pattern in PY_BROKEN_PATTERNS:
        for match in pattern.finditer(text):
            if match.lastindex is None:
                continue
            name = match.group(1).strip().split(".")[0]
            if name and re.match(r"^[A-Za-z_][\w]*$", name) and name not in modules:
                modules.append(name)

    stdlib = python_stdlib()
    locals_ = local_modules(script)
    actions: list[str] = []
    for module in modules:
        if module in stdlib or module.startswith("_"):
            continue
        if module in locals_:
            actions.append(f"shadow:{module}.py")
            continue
        dist = PY_DIST.get(module, module)
        if not dist:
            continue
        bare = dist.lower().replace("-", "_")
        if bare != module.lower():
            try:
                importlib.metadata.distribution(module)
                actions.append(f"remove:{module}")
            except Exception:
                pass
        actions.append(f"upgrade:{dist}")
    # بلا تكرار مع الحفاظ على الترتيب
    seen: set[str] = set()
    return [a for a in actions if not (a in seen or seen.add(a))]


def preflight_repairs(script: str | None) -> list[str]:
    """فحص وقائي قبل أول تشغيل: هل يستورد السكربت ميزة غير موجودة في النسخة؟"""
    if not script or not os.path.isfile(script):
        return []
    try:
        with open(script, "r", encoding="utf-8", errors="replace") as handle:
            src = handle.read(400_000)
    except OSError:
        return []
    actions: list[str] = []
    for module, (attr, dist) in FEATURE_PROBES.items():
        if not re.search(r"\b" + re.escape(module) + r"\s*\.\s*" + re.escape(attr) + r"\b", src):
            continue
        try:
            mod = __import__(module)
            if hasattr(mod, attr):
                continue
        except Exception:
            continue  # الوحدة ناقصة أصلًا: يتولّاها auto_deps
        try:
            importlib.metadata.distribution(module)
            actions.append(f"remove:{module}")
        except Exception:
            pass
        actions.append(f"upgrade:{dist}")
    seen: set[str] = set()
    return [a for a in actions if not (a in seen or seen.add(a))]


# ————————————————————————— Node —————————————————————————

NODE_PATTERNS = (
    re.compile(r"Cannot find module ['\"]([^'\"]+)['\"]"),
    re.compile(r"Error: Cannot find module ['\"]?([\w@/.\-]+)"),
)


def find_node_packages(text: str) -> list[str]:
    packages: list[str] = []
    for pattern in NODE_PATTERNS:
        for match in pattern.finditer(text):
            spec = match.group(1).strip()
            if not spec or spec.startswith(".") or spec.startswith("/"):
                continue
            # @scope/name/sub → @scope/name ؛ name/sub → name
            parts = spec.split("/")
            name = "/".join(parts[:2]) if spec.startswith("@") and len(parts) > 1 else parts[0]
            if name and name not in packages:
                packages.append(name)
    return packages


# ————————————————————————— PHP —————————————————————————

PHP_PATTERNS = (
    re.compile(r"Class ['\"]([A-Za-z0-9_\\]+)['\"] not found"),
    re.compile(r"Interface ['\"]([A-Za-z0-9_\\]+)['\"] not found"),
)


def find_php_packages(text: str) -> list[str]:
    """PHP: نطبع أسماء الفئات الناقصة؛ التثبيت يتم بـ composer إن توفّر."""
    packages: list[str] = []
    for pattern in PHP_PATTERNS:
        for match in pattern.finditer(text):
            cls = match.group(1)
            if "\\" in cls:
                # Vendor\\Package\\Class → vendor/package
                parts = cls.split("\\")
                if len(parts) >= 2:
                    name = f"{parts[0].lower()}/{parts[1].lower()}"
                    if name not in packages:
                        packages.append(name)
    return packages


def main() -> int:
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--lang", default="python")
    parser.add_argument("--stderr", dest="err", default="")
    parser.add_argument("--file", dest="script", default="")
    parser.add_argument("--broken", action="store_true")
    parser.add_argument("--preflight", action="store_true")
    args = parser.parse_args()

    text = ""
    if args.err and os.path.isfile(args.err):
        try:
            with open(args.err, "r", encoding="utf-8", errors="replace") as handle:
                text = handle.read()[-200_000:]
        except OSError:
            return 0

    if args.preflight:
        for action in preflight_repairs(args.script):
            print(action)
        return 0

    if args.broken:
        for action in broken_actions(text, args.script):
            print(action)
        return 0

    if not text:
        return 0

    lang = (args.lang or "python").lower()
    if lang == "python":
        for pkg in to_packages(find_python_modules(text, args.script), args.script):
            print(pkg)
    elif lang == "node":
        for pkg in find_node_packages(text):
            print(pkg)
    elif lang == "php":
        for pkg in find_php_packages(text):
            print(pkg)
    return 0


if __name__ == "__main__":
    sys.exit(main())
