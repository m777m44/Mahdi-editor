# shellcheck shell=bash
# ——————————————————————————————————————————————————————————————————————————————
# Mahdi-Editor — التعرّف التلقائي على المكتبات وتثبيتها قبل تشغيل السكربت
#
# يستخدمه universal_runner قبل تشغيل ملفات Python وJavaScript/TypeScript وPHP:
#   1. يقرأ السكربت ويستخرج كل المكتبات المستوردة (import / from / require).
#   2. يستخرج أيضًا أوامر `pip install …` و`npm install …` المكتوبة داخل السكربت.
#   3. يستبعد مكتبات بايثون القياسية والمكتبات المحلية الموجودة بجانب الملف.
#   4. يثبّت الناقص فقط، بآخر إصدار، على دفعات متوازية (أسرع)، مع إعادة المحاولة.
#   5. يعيد التحقق؛ وإن فشل شيء يثبّت أدوات البناء ثم يعيد المحاولة مرة واحدة.
#
# متغيرات يمكن ضبطها من ~/.bashrc أو من الطرفية:
#   MAHDI_AUTO_DEPS=0        تعطيل الميزة كاملة
#   MAHDI_PIP_INDEX=…        مرآة PyPI (مثال: https://mirror.example/simple)
#   MAHDI_NPM_REGISTRY=…     مرآة npm
#   MAHDI_DEP_JOBS=4         عدد التثبيتات المتوازية
#   MAHDI_CACHE_DIR=…        مجلد المخزن الدائم (افتراضيًا ~/.mahdi/cache)
# ——————————————————————————————————————————————————————————————————————————————

MAHDI_AUTO_DEPS="${MAHDI_AUTO_DEPS:-1}"
MAHDI_DEP_JOBS="${MAHDI_DEP_JOBS:-4}"
MAHDI_PIP_INDEX="${MAHDI_PIP_INDEX:-}"
MAHDI_NPM_REGISTRY="${MAHDI_NPM_REGISTRY:-}"

# في حال تشغيل الملف مستقلة دون utils
if ! declare -f info >/dev/null 2>&1; then
  info() { printf '\n[INFO] %s\n' "$*"; }
fi
if ! declare -f warn >/dev/null 2>&1; then
  warn() { printf '\n[WARN] %s\n' "$*"; }
fi

mahdi_have() { command -v "$1" >/dev/null 2>&1; }

# ————————————————————————————— أدوات مساعدة —————————————————————————————

# تنزيل سريع: aria2c بثمانية اتصالات إن وُجد، وإلا curl مع استئناف وإعادة محاولة
mahdi_fetch() {
  local url="$1" out="$2"
  mkdir -p "$(dirname "$out")" 2>/dev/null || true
  if mahdi_have aria2c; then
    aria2c -x8 -s8 -k1M --allow-overwrite=true --auto-file-renaming=false \
      --console-log-level=warn --summary-interval=0 \
      -d "$(dirname "$out")" -o "$(basename "$out")" "$url" && return 0
  fi
  if mahdi_have curl; then
    curl -fL --retry 5 --retry-delay 2 --connect-timeout 20 -C - -o "$out" "$url" && return 0
  fi
  wget -c -O "$out" "$url"
}

# يثبّت aria2 عند الحاجة فقط (تنزيل متعدد الاتصالات = أسرع بكثير للملفات الكبيرة)
mahdi_ensure_aria2() {
  [ "${MAHDI_FAST_DOWNLOADS:-1}" = "0" ] && return 1
  mahdi_have aria2c && return 0
  mahdi_apt_install aria2 >/dev/null 2>&1 || return 1
  mahdi_have aria2c
}

# تنزيل «سريع»: يضمن aria2 أولًا ثم ينزّل بعدة اتصالات
mahdi_fetch_fast() {
  mahdi_ensure_aria2 || true
  mahdi_fetch "$1" "$2"
}

# يكسر أقفال apt الميتة (عملية انتهت وبقي ملف القفل) حتى لا ينتظر التثبيت للأبد
mahdi_break_stale_apt_locks() {
  local holder_alive=0
  if command -v pgrep >/dev/null 2>&1; then
    pgrep -x apt-get >/dev/null 2>&1 || pgrep -x apt >/dev/null 2>&1 || pgrep -x dpkg >/dev/null 2>&1 || pgrep -x apt-helper >/dev/null 2>&1
    holder_alive=$?
  else
    ps -e 2>/dev/null | grep -Eq '([ /])(apt-get|apt|dpkg)( |$)' && holder_alive=0 || holder_alive=1
  fi
  [ "$holder_alive" -eq 0 ] && return 0
  local lock
  for lock in /var/lib/dpkg/lock-frontend /var/lib/dpkg/lock /var/cache/apt/archives/lock /var/lib/apt/lists/lock; do
    [ -e "$lock" ] && rm -f "$lock" 2>/dev/null
  done
  return 0
}

# تثبيت حزم النظام: بلا انتظار لا نهائي على القفل، وبلا حزم اختيارية، وبمهلة قصيرة
mahdi_apt_install() {
  export DEBIAN_FRONTEND=noninteractive
  mahdi_break_stale_apt_locks
  local lock_file="${TMPDIR:-/tmp}/mahdi-apt.lock"
  if command -v flock >/dev/null 2>&1; then
    (
      # عملية واحدة فقط من التطبيق تستخدم apt في وقت واحد
      flock -w 60 9 || { warn "apt مشغول بعملية أخرى — تجاوزناه"; exit 0; }
      if [ ! -f /.cache/mahdi_apt_updated ]; then
        apt-get -o DPkg::Lock::Timeout=60 -o Acquire::Retries=3 update -qq >/dev/null 2>&1 || true
        mkdir -p /.cache 2>/dev/null || true
        touch /.cache/mahdi_apt_updated 2>/dev/null || true
      fi
      apt-get -o DPkg::Lock::Timeout=60 -o Acquire::Retries=3 install -y \
        --no-install-recommends "$@" </dev/null
    ) 9>"$lock_file"
    return $?
  fi
  if [ ! -f /.cache/mahdi_apt_updated ]; then
    apt-get -o DPkg::Lock::Timeout=60 -o Acquire::Retries=3 update -qq >/dev/null 2>&1 || true
    mkdir -p /.cache 2>/dev/null || true
    touch /.cache/mahdi_apt_updated 2>/dev/null || true
  fi
  apt-get -o DPkg::Lock::Timeout=60 -o Acquire::Retries=3 install -y --no-install-recommends "$@" </dev/null
}

# تثبيت pip في مجلد المستخدم بلا apt وبلا أقفال (الأسرع والأكثر أمانًا)
mahdi_bootstrap_pip() {
  python3 -m pip --version >/dev/null 2>&1 && return 0

  python3 -m ensurepip --upgrade --user >/dev/null 2>&1 && python3 -m pip --version >/dev/null 2>&1 && return 0
  python3 -m ensurepip --default-pip >/dev/null 2>&1 && python3 -m pip --version >/dev/null 2>&1 && return 0

  info "تنزيل get-pip.py (بلا apt)…"
  if mahdi_have curl || mahdi_have wget; then
    if mahdi_have curl; then
      curl -fsSL --retry 3 --connect-timeout 20 --max-time 120 \
        https://bootstrap.pypa.io/get-pip.py -o "${TMPDIR:-/tmp}/get-pip.py" || true
    else
      wget -q -T 120 -O "${TMPDIR:-/tmp}/get-pip.py" https://bootstrap.pypa.io/get-pip.py || true
    fi
    if [ -s "${TMPDIR:-/tmp}/get-pip.py" ]; then
      python3 "${TMPDIR:-/tmp}/get-pip.py" --user --no-warn-script-location >/dev/null 2>&1 || true
      python3 -m pip --version >/dev/null 2>&1 && return 0
    fi
  fi

  # آخر خيار: apt بمهلة محدودة
  mahdi_apt_install python3-pip >/dev/null 2>&1 || true
  python3 -m pip --version >/dev/null 2>&1
}

mahdi_pip_env() {
  # مخزن دائم داخل التطبيق: الحزمة المنزَّلة تُحفظ فلا تُنزَّل مرة ثانية
  MAHDI_CACHE_DIR="${MAHDI_CACHE_DIR:-$HOME/.mahdi/cache}"
  mkdir -p "$MAHDI_CACHE_DIR/pip" "$MAHDI_CACHE_DIR/npm" "$MAHDI_CACHE_DIR/wheels" 2>/dev/null || true
  export PIP_CACHE_DIR="${PIP_CACHE_DIR:-$MAHDI_CACHE_DIR/pip}"
  export npm_config_cache="${npm_config_cache:-$MAHDI_CACHE_DIR/npm}"
  export PIP_ROOT_USER_ACTION=ignore
  export PIP_DISABLE_PIP_VERSION_CHECK=1
  export PIP_NO_INPUT=1
  export PIP_RETRIES=5
  export PIP_TIMEOUT=30
  # يتجاوز حماية PEP 668 في Ubuntu الحديثة التي تُفشل «pip install» خارج بيئة معزولة
  export PIP_BREAK_SYSTEM_PACKAGES=1
  export PYTHONUNBUFFERED=1
  [ -n "$MAHDI_PIP_INDEX" ] && export PIP_INDEX_URL="$MAHDI_PIP_INDEX"
  return 0
}

# يضمن وجود pip ويعيد بناءه كأمر مستقل (pip وpip3) داخل ~/.local/bin
mahdi_ensure_pip() {
  mahdi_pip_env
  mkdir -p "$HOME/.local/bin" 2>/dev/null || true
  case ":$PATH:" in
    *":$HOME/.local/bin:"*) ;;
    *) export PATH="$HOME/.local/bin:$PATH" ;;
  esac

  if ! python3 -m pip --version >/dev/null 2>&1; then
    info "pip غير مثبّت — جاري تجهيزه بلا انتظار أقفال النظام…"
    mahdi_bootstrap_pip || true
  fi

  # بعض السكربتات تستدعي «python» بدل «python3»، وبعضها «pip» بدل «pip3»
  if ! mahdi_have python; then
    printf '#!/bin/sh\nexec python3 "$@"\n' > "$HOME/.local/bin/python" 2>/dev/null || true
    chmod +x "$HOME/.local/bin/python" 2>/dev/null || true
  fi

  local shim
  for shim in pip pip3; do
    if ! mahdi_have "$shim"; then
      printf '#!/bin/sh\nexec python3 -m pip "$@"\n' > "$HOME/.local/bin/$shim" 2>/dev/null || true
      chmod +x "$HOME/.local/bin/$shim" 2>/dev/null || true
    fi
  done
  hash -r 2>/dev/null || true
  python3 -m pip --version >/dev/null 2>&1
}

# ————————————————————————— تثبيت حزم بايثون (متوازٍ) —————————————————————————

# مسار مثبّت الحزم الذكي (يعرض نسبة تقدّم حقيقية في التطبيق)
mahdi_pip_runner() {
  local candidate="${LOCAL:-/home}/bin/mahdi_pip.py"
  if [ ! -f "$candidate" ]; then
    candidate="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)/mahdi_pip.py"
  fi
  printf '%s' "$candidate"
}

# تثبيت معروض بنسبة 0→100: mahdi_pip_run "العرض" pkg1 pkg2…
mahdi_pip_run() {
  local name="$1"
  shift
  local script
  script="$(mahdi_pip_runner)"
  if [ -f "$script" ]; then
    if python3 "$script" --name "$name" "$@"; then
      return 0
    fi
    # خطة بديلة صامتة إن فشل المثبّت الذكي لأي سبب
  fi
  if declare -f mahdi_prog_start >/dev/null 2>&1; then
    mahdi_prog_start "$name" "تثبيت مباشر بـ pip…"
    mahdi_prog 40 "تثبيت المكتبة" "$name"
  fi
  python3 -m pip install --upgrade --prefer-binary --disable-pip-version-check \
    --quiet --no-input --retries 5 --timeout 30 "$@" || return 1
  declare -f mahdi_prog_done >/dev/null 2>&1 && mahdi_prog_done "تم التثبيت" "$name"
  return 0
}

mahdi_pip_one() {
  local pkg="$1"
  mahdi_pip_run "$pkg" "$pkg" && return 0
  # إعادة محاولة عبر المصدر الرسمي
  python3 -m pip install --upgrade --prefer-binary --no-input --retries 3 --timeout 30 "$pkg" && return 0
  python3 -m pip install --upgrade --prefer-binary --no-input --retries 3 --timeout 30 \
    -i "https://pypi.org/simple" "$pkg" && return 0
  warn "تعذّر تثبيت $pkg"
  return 1
}

mahdi_pip_install_many() {
  local pkgs=("$@")
  local total=${#pkgs[@]}
  [ "$total" -eq 0 ] && return 0

  local label="${pkgs[*]}"
  if mahdi_pip_run "$label" "${pkgs[@]}"; then
    declare -f mahdi_prog_done >/dev/null 2>&1 && mahdi_prog_done "تم تثبيت المكتبات" "$label"
    return 0
  fi

  local p
  for p in "${pkgs[@]}"; do mahdi_pip_one "$p" || true; done
  return 0
}

# ————————————————————————— تحليل سكربتات بايثون —————————————————————————

mahdi_py_plan_write() {
  cat > /tmp/mahdi_py_plan.py <<'PYEOF'
"""يستخرج حزم PyPI الناقصة من سكربت بايثون (impolve: imports + أوامر pip النصية)."""
import ast
import importlib.metadata
import importlib.util
import os
import re
import sys

# أسماء الوحدات المشهورة التي تختلف عن اسم الحزمة على PyPI
PACKAGE_MAP = {
    "PIL": "pillow",
    "cv2": "opencv-python",
    "sklearn": "scikit-learn",
    "skimage": "scikit-image",
    "bs4": "beautifulsoup4",
    "yaml": "PyYAML",
    "dotenv": "python-dotenv",
    "Crypto": "pycryptodome",
    "Cryptodome": "pycryptodome",
    "dateutil": "python-dateutil",
    "jwt": "PyJWT",
    "serial": "pyserial",
    "MySQLdb": "mysqlclient",
    "psycopg2": "psycopg2-binary",
    "fitz": "PyMuPDF",
    "docx": "python-docx",
    "pptx": "python-pptx",
    "magic": "python-magic",
    "slugify": "python-slugify",
    "Levenshtein": "python-Levenshtein",
    "telegram": "python-telegram-bot",
    "telebot": "pyTelegramBotAPI",
    "pkg_resources": "setuptools",
    "OpenSSL": "pyopenssl",
    "win32com": "pywin32",
    "win32api": "pywin32",
    "socketio": "python-socketio",
    "engineio": "python-engineio",
    "OpenGL": "PyOpenGL",
    "gi": "PyGObject",
    "wx": "wxPython",
    "usb": "pyusb",
    "nanoid": "nanoid",
    "websocket": "websocket-client",
    "pyaudio": "PyAudio",
    "speech_recognition": "SpeechRecognition",
    "uno": None,
}

# وحدات تُشحن مع بايثون أو لا تُثبّت من PyPI بهذا الاسم
IGNORED = {
    "__future__", "site", "sitecustomize", "typing_extensions", "pip", "setuptools",
    "wheel", "venv", "virtualenv", "pkgutil", "distutils", "test", "ensurepip",
}


def add_module(mods, name):
    if not name:
        return
    top = name.split(".")[0]
    if top and not top.startswith("_"):
        mods.add(top)


def parse_imports(src, mods):
    try:
        tree = ast.parse(src)
    except SyntaxError:
        # ملف غير مكتمل أو يحتوي تعبيرات خاصة: نسقط إلى تعبير نمطي
        for m in re.finditer(r"^[ \t]*(?:from|import)[ \t]+([A-Za-z_][A-Za-z0-9_.]*)", src, re.M):
            add_module(mods, m.group(1))
        return
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                add_module(mods, alias.name)
        elif isinstance(node, ast.ImportFrom):
            if node.level == 0 and node.module:
                add_module(mods, node.module)


PIP_ALIASES = {
    "request": "requests",
    "bs4": "beautifulsoup4",
    "sklearn": "scikit-learn",
    "cv2": "opencv-python",
    "opencv": "opencv-python",
    "pil": "pillow",
    "PIL": "pillow",
    "yaml": "PyYAML",
    "dotenv": "python-dotenv",
    "mysql": "mysqlclient",
    "jwt": "PyJWT",
    "serial": "pyserial",
    "win32": "pywin32",
    "telegram-bot": "python-telegram-bot",
    "speechrecognition": "SpeechRecognition",
}


def _add_tokens(tokens, extra):
    skip_next = False
    for token in tokens:
        token = token.strip().strip("'\"")
        if not token:
            continue
        if skip_next:
            skip_next = False
            continue
        if token.startswith("-"):
            # الخيارات التي تأخذ قيمة بعدها
            if token in ("-i", "--index-url", "--extra-index-url", "-r", "--requirement",
                         "-c", "--constraint", "-t", "--target", "-f", "--find-links",
                         "--upgrade-strategy", "--timeout", "--retries"):
                skip_next = True
            continue
        if token in ("install", "&&", "||", "python", "python3", "pip", "pip3", "-m"):
            continue
        clean = re.split(r"[=<>!~\[]", token)[0].strip()
        if clean and re.match(r"^[A-Za-z0-9_.-]+$", clean):
            clean = PIP_ALIASES.get(clean, PIP_ALIASES.get(clean.lower(), clean))
            extra.add(clean)


def parse_pip_lines(src, extra):
    """أوامر pip المكتوبة داخل السكربت: نصية (os.system) أو على شكل قائمة (subprocess)."""
    # 1) «pip install …» نصًّا داخل الاقتباسات أو التعليقات
    for m in re.finditer(r"\bpip3?\s+install\b([^\n\"'`|;&]*)", src):
        _add_tokens(m.group(1).replace("\\\n", " ").split(), extra)

    # 2) subprocess.run(["pip", "install", "x"]) أو ["python", "-m", "pip", "install", …]
    list_patterns = [
        r"\[\s*['\"]pip3?['\"]\s*,\s*['\"]install['\"]\s*,([^\]]*)\]",
        r"\[\s*['\"]python3?['\"]\s*,\s*['\"]-m['\"]\s*,\s*['\"]pip['\"]\s*,\s*['\"]install['\"]\s*,([^\]]*)\]",
        r"\[\s*['\"]pip3?['\"]\s*,\s*['\"]install['\"]\s*\]\s*\+\s*\[([^\]]*)\]",
    ]
    for pattern in list_patterns:
        for m in re.finditer(pattern, src):
            _add_tokens(re.findall(r"['\"]([^'\"]+)['\"]", m.group(1)), extra)


def is_installed(module):
    try:
        return importlib.util.find_spec(module) is not None
    except (ImportError, ValueError, AttributeError):
        return False


def canonical(name):
    """اسم الحزمة بصيغة PEP 503 (حروف صغيرة، الشرطات)."""
    return re.sub(r"[-_.]+", "-", name).strip().lower()


def distribution_installed(spec):
    """هل الحزمة مثبّتة فعلًا؟ (يتجاهل قيود الإصدار وأقواس الميزات)."""
    name = spec.split("[")[0]
    name = re.split(r"[<>=!~; ]", name, 1)[0]
    if not name:
        return False
    try:
        importlib.metadata.version(name)
        return True
    except Exception:
        pass
    try:
        importlib.metadata.version(canonical(name))
        return True
    except Exception:
        return False


def is_local(module, directory):
    if not directory:
        return False
    candidates = [
        os.path.join(directory, module + ".py"),
        os.path.join(directory, module, "__init__.py"),
    ]
    return any(os.path.exists(p) for p in candidates)


def main():
    path = sys.argv[1]
    try:
        with open(path, encoding="utf-8", errors="ignore") as fh:
            src = fh.read()
    except OSError:
        return 0

    stdlib = set(getattr(sys, "stdlib_module_names", ()))
    stdlib.update(sys.builtin_module_names)
    if not stdlib:
        # بايثون أقدم من 3.10: قائمة أساسية تغطي الواردات الشائعة
        stdlib = {
            "abc", "argparse", "array", "ast", "asyncio", "base64", "binascii", "bisect",
            "builtins", "bz2", "calendar", "codecs", "collections", "colorsys", "concurrent",
            "configparser", "contextlib", "copy", "csv", "ctypes", "dataclasses", "datetime",
            "decimal", "difflib", "dis", "email", "enum", "errno", "faulthandler", "filecmp",
            "fileinput", "fnmatch", "fractions", "ftplib", "functools", "gc", "getopt",
            "getpass", "gettext", "glob", "gzip", "hashlib", "heapq", "hmac", "html", "http",
            "imaplib", "imp", "importlib", "inspect", "io", "ipaddress", "itertools", "json",
            "keyword", "linecache", "locale", "logging", "lzma", "mailbox", "math", "mimetypes",
            "mmap", "multiprocessing", "netrc", "numbers", "operator", "optparse", "os",
            "pathlib", "pdb", "pickle", "pipes", "pkgutil", "platform", "plistlib", "pprint",
            "profile", "pstats", "queue", "quopri", "random", "re", "readline", "reprlib",
            "sched", "secrets", "select", "selectors", "shelve", "shlex", "shutil", "signal",
            "site", "smtplib", "socket", "socketserver", "sqlite3", "ssl", "stat", "statistics",
            "string", "stringprep", "struct", "subprocess", "sys", "sysconfig", "tarfile",
            "tempfile", "textwrap", "threading", "time", "timeit", "token", "tokenize",
            "traceback", "tracemalloc", "types", "typing", "unicodedata", "unittest", "urllib",
            "uuid", "venv", "warnings", "wave", "weakref", "webbrowser", "xml", "xmlrpc",
            "zipapp", "zipfile", "zipimport", "zlib", "zoneinfo",
        }

    mods, extra = set(), set()
    parse_imports(src, mods)
    parse_pip_lines(src, extra)

    directory = os.path.dirname(os.path.abspath(path))
    needed = []

    for mod in sorted(mods):
        if mod in stdlib or mod in IGNORED or is_local(mod, directory):
            continue
        if is_installed(mod):
            continue
        pkg = PACKAGE_MAP.get(mod, mod)
        if pkg:
            needed.append(pkg)

    for pkg in sorted(extra):
        if pkg in stdlib or pkg in IGNORED:
            continue
        # الحزمة المثبّتة مسبقًا لا تُعاد: يبقى التثبيت للمكتبات الناقصة فقط
        if distribution_installed(pkg):
            continue
        if is_installed(pkg.split("[")[0].split("=")[0].split(">")[0].split("<")[0].strip()):
            continue
        if pkg not in needed:
            needed.append(pkg)

    for name in needed:
        print(name)
    return 0


if __name__ == "__main__":
    sys.exit(main())
PYEOF
}

mahdi_py_deps() {
  local file="$1"
  [ "$MAHDI_AUTO_DEPS" = "0" ] && return 0
  [ -z "$file" ] && return 0

  mahdi_ensure_pip || {
    warn "pip غير متاح — سيُشغَّل السكربت كما هو"
    return 0
  }

  local dir
  dir=$(cd "$(dirname "$file")" 2>/dev/null && pwd) || dir="$(dirname "$file")"

  if [ -f "$dir/requirements.txt" ]; then
    info "متطلبات requirements.txt…"
    mahdi_pip_run "requirements.txt" -r "$dir/requirements.txt" ||
      warn "بعض متطلبات requirements.txt لم تُثبَّت"
  fi

  mahdi_py_plan_write
  local -a missing=()
  while IFS= read -r line; do
    [ -n "$line" ] && missing+=("$line")
  done < <(python3 /tmp/mahdi_py_plan.py "$file" 2>/dev/null)

  if [ ${#missing[@]} -eq 0 ]; then
    # 3.0.0: صمت تام — لا تُطبع أي رسالة إذا كانت كل المكتبات موجودة
    return 0
  fi

  info "سيُثبَّت: ${missing[*]}"
  mahdi_pip_install_many "${missing[@]}"

  local -a still=()
  while IFS= read -r line; do
    [ -n "$line" ] && still+=("$line")
  done < <(python3 /tmp/mahdi_py_plan.py "$file" 2>/dev/null)

  if [ ${#still[@]} -gt 0 ]; then
    info "إعادة المحاولة: ${still[*]}"
    mahdi_apt_install build-essential python3-dev >/dev/null 2>&1 || true
    mahdi_pip_install_many "${still[@]}"
  fi

  local -a final=()
  while IFS= read -r line; do
    [ -n "$line" ] && final+=("$line")
  done < <(python3 /tmp/mahdi_py_plan.py "$file" 2>/dev/null)

  if [ ${#final[@]} -gt 0 ]; then
    warn "لم تُثبَّت: ${final[*]}"
  elif declare -f success >/dev/null 2>&1; then
    success "المكتبات جاهزة — بدء التشغيل"
  else
    info "المكتبات جاهزة — بدء التشغيل"
  fi
  return 0
}

# ————————————————————————— تحليل سكربتات Node —————————————————————————

mahdi_node_scan_write() {
  cat > /tmp/mahdi_node_scan.js <<'JSEOF'
const fs = require('fs');
const builtins = new Set(require('module').builtinModules);
const src = fs.readFileSync(process.argv[2], 'utf8');
const names = new Set();
const patterns = [
  /require\(\s*['"]([^'"]+)['"]\s*\)/g,
  /\bfrom\s+['"]([^'"]+)['"]\s*/g,
  /\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g,
  /\bimport\s+['"]([^'"]+)['"]\s*;?/g,
];
for (const re of patterns) {
  for (const m of src.matchAll(re)) {
    const spec = m[1];
    if (!spec) continue;
    if (spec.startsWith('.') || spec.startsWith('/') || spec.startsWith('node:')) continue;
    const parts = spec.split('/');
    const name = spec.startsWith('@') ? parts.slice(0, 2).join('/') : parts[0];
    if (!name || builtins.has(name)) continue;
    names.add(name);
  }
}
const list = [...names];
if (list.length) console.log(list.join('\n'));
JSEOF
}

mahdi_node_installed() {
  local name="$1" dir="$2"
  (cd "$dir" 2>/dev/null || cd /) &&
    node -e "try{require.resolve(process.argv[1]);process.exit(0)}catch(e){process.exit(1)}" "$name" >/dev/null 2>&1
}

mahdi_node_deps() {
  local file="$1"
  [ "$MAHDI_AUTO_DEPS" = "0" ] && return 0
  [ -z "$file" ] && return 0
  mahdi_have node || return 0

  local dir
  dir=$(cd "$(dirname "$file")" 2>/dev/null && pwd) || dir="$(dirname "$file")"

  if [ -f "$dir/package.json" ] && mahdi_have npm; then
    info "تثبيت متطلبات package.json…"
    (cd "$dir" && npm install --no-fund --no-audit --loglevel=error) || warn "تعذّر تثبيت package.json كاملًا"
  fi

  mahdi_node_scan_write
  local -a missing=()
  while IFS= read -r name; do
    [ -z "$name" ] && continue
    mahdi_node_installed "$name" "$dir" && continue
    mahdi_node_installed "$name" "$HOME" && continue
    # الاعتماد العالمي يظهر عبر NODE_PATH فقط
    missing+=("$name")
  done < <(node /tmp/mahdi_node_scan.js "$file" 2>/dev/null)

  if [ ${#missing[@]} -eq 0 ]; then
    # 3.0.0: صمت تام عند عدم وجود نواقص
    return 0
  fi

  info "سيُثبَّت (Node): ${missing[*]}"
  local registry_args=()
  [ -n "$MAHDI_NPM_REGISTRY" ] && registry_args=(--registry "$MAHDI_NPM_REGISTRY")

  if [ -w "$dir" ]; then
    (cd "$dir" && npm install --no-fund --no-audit --loglevel=error "${registry_args[@]}" "${missing[@]}") || {
      npm install -g --no-fund --no-audit --loglevel=error "${registry_args[@]}" "${missing[@]}" ||
        warn "تعذّر تثبيت بعض مكتبات Node"
      export NODE_PATH="$(npm root -g 2>/dev/null):${NODE_PATH:-}"
    }
  else
    npm install -g --no-fund --no-audit --loglevel=error "${registry_args[@]}" "${missing[@]}" ||
      warn "تعذّر تثبيت بعض مكتبات Node"
    export NODE_PATH="$(npm root -g 2>/dev/null):${NODE_PATH:-}"
  fi

  local -a left=()
  local name
  for name in "${missing[@]}"; do
    mahdi_node_installed "$name" "$dir" || left+=("$name")
  done
  if [ ${#left[@]} -gt 0 ]; then
    warn "لم تُثبَّت: ${left[*]}"
  elif declare -f success >/dev/null 2>&1; then
    success "مكتبات Node جاهزة — بدء التشغيل"
  fi
  return 0
}

# ————————————————————————— تحليل سكربتات PHP —————————————————————————

mahdi_php_deps() {
  local file="$1"
  [ "$MAHDI_AUTO_DEPS" = "0" ] && return 0
  [ -z "$file" ] && return 0

  local dir
  dir=$(cd "$(dirname "$file")" 2>/dev/null && pwd) || dir="$(dirname "$file")"
  [ -f "$dir/composer.json" ] || return 0

  if ! mahdi_have composer; then
    info "تنزيل Composer…"
    mahdi_fetch "https://getcomposer.org/composer-stable.phar" "$HOME/.local/bin/composer" >/dev/null 2>&1 || true
    chmod +x "$HOME/.local/bin/composer" 2>/dev/null || true
    case ":$PATH:" in
      *":$HOME/.local/bin:"*) ;;
      *) export PATH="$HOME/.local/bin:$PATH" ;;
    esac
  fi

  if mahdi_have composer; then
    info "تثبيت حزم Composer…"
    (cd "$dir" && composer install --no-interaction --prefer-dist --no-progress) ||
      warn "تعذّر تثبيت حزم Composer"
  else
    warn "لم يُعثر على Composer — سيُشغَّل السكربت كما هو"
  fi
  return 0
}

# ————————————————————————— نقطة الدخول —————————————————————————

mahdi_auto_deps() {
  local file="$1"
  [ "$MAHDI_AUTO_DEPS" = "0" ] && return 0
  case "$file" in
    *.py) mahdi_py_deps "$file" ;;
    *.js | *.mjs | *.cjs | *.ts | *.tsx) mahdi_node_deps "$file" ;;
    *.php) mahdi_php_deps "$file" ;;
  esac
  return 0
}

# تشغيل مباشر:  bash auto_deps.sh /path/to/script.py
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  mahdi_auto_deps "$1"
fi
