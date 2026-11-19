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
# مرآة احتياطية تُجرَّب فقط عند فشل السجل الرسمي
MAHDI_NPM_MIRROR="${MAHDI_NPM_MIRROR:-https://registry.npmmirror.com}"

# ——————————————————————————————————————————————————————————————————————————————
# 8.3.0 — «الرمز 1 بعد ٢٣ ثانية» عند تشغيل أي سكربت بايثون
#
# السبب الحقيقي لم يكن في السكربت: عند غياب مكتبة واحدة كانت تُجرى **أربع**
# محاولات تثبيت متتابعة لنفس الحزمة (الدفعة ← كل حزمة على حدة ← إعادة الفحص
# مع apt ← ثم self_heal يعيد الكرّة)، كل محاولة تنتظر الشبكة، ثم يُخفى stderr
# الأصلي فلا يبقى للمستخدم سوى «✖ توقّف السكربت (الرمز 1) — 23s» بلا سبب.
#
# الآن: فحص اتصال سريع مرة واحدة، وسجلّ للحزم التي فشل تثبيتها في هذا التشغيل،
# فلا تُعاد أي محاولة، ويظهر سبب الفشل الحقيقي من أول سطر.
# ——————————————————————————————————————————————————————————————————————————————
MAHDI_DEPS_FAILED=" "
MAHDI_NET_STATE=""
export MAHDI_DEPS_FAILED

# 9.5.1 — بيئة pip صالحة لكل عملية تثبيت، حتى التي يكتبها المستخدم بنفسه.
# Ubuntu الحديثة ترفض `pip install` بحماية PEP 668، فيظهر الفشل كأنه عطل شبكة.
export PIP_BREAK_SYSTEM_PACKAGES="${PIP_BREAK_SYSTEM_PACKAGES:-1}"
export PIP_ROOT_USER_ACTION="${PIP_ROOT_USER_ACTION:-ignore}"
export PIP_DISABLE_PIP_VERSION_CHECK=1
export PIP_NO_INPUT=1

mahdi_deps_mark_failed() {
  local p
  for p in "$@"; do
    [ -z "$p" ] && continue
    # توحيد الحالة وإسقاط أقواس الإضافات: «requests[socks]» ⇒ «requests»
    p="${p%%\[*}"
    p="${p,,}"
    case " $MAHDI_DEPS_FAILED " in
      *" $p "*) ;;
      *) MAHDI_DEPS_FAILED="$MAHDI_DEPS_FAILED$p " ;;
    esac
  done
}

mahdi_deps_failed() {
  local p="${1%%\[*}"
  p="${p,,}"
  case " $MAHDI_DEPS_FAILED " in
    *" $p "*) return 0 ;;
    *) return 1 ;;
  esac
}

# 9.5.1: يمحي علامة «فشل سابق» حتى تُجرَّب الحزمة من جديد بكل الطرق.
#
# كان فشل واحد (بسبب فحص شبكة خاطئ) يسجّل الحزمة «فاشلة» فيمنع self_heal من
# إعادة المحاولة، فينتهي السكربت بمكتبة ناقصة مع أن شبكة المستخدم سليمة.
mahdi_deps_clear() {
  local p out=" "
  for p in "$@"; do
    [ -z "$p" ] && continue
    p="${p%%\[*}"
    p="${p,,}"
    case " $MAHDI_DEPS_FAILED " in
      *" $p "*) ;;
      *) out="$out$p " ;;
    esac
  done
  MAHDI_DEPS_FAILED="$out"
}

# 9.5.1 — «هذه الحزمة غير موجودة»: ذاكرة يوم واحد يكتبها المثبّت الذكي.
# تمنع إعادة المحاولة ببناء من المصدر وapt ومرايا لحزمة اسمها خاطئ.
mahdi_nodist_known() {
  local name="${1:-}"
  [ -n "$name" ] || return 1
  name="$(printf '%s' "$name" | tr '[:upper:]' '[:lower:]')"
  local file="${HOME:-/root}/.mahdi/nodist/$name"
  [ -f "$file" ] || return 1
  # العمر بأداة محمولة (‏find في toybox لا يعرف -quit دائمًا)
  local age
  age="$("${MAHDI_PY:-python3}" - "$file" 2>/dev/null <<'PYAGE'
import os, sys, time
try:
    print(int(time.time() - os.path.getmtime(sys.argv[1])))
except Exception:
    print(10 ** 9)
PYAGE
)"
  case "${age:-0}" in
    '' | *[!0-9]*) age=0 ;;
  esac
  # الذاكرة صالحة ليوم واحد فقط: قد تُنشر الحزمة لاحقًا أو يُصحَّح الاسم
  [ "$age" -lt 86400 ]
}

# ——————————————————————————————————————————————————————————————————————————————
# 9.5.1 — طابور «مكتبات مؤجّلة»
#
# عندما يتعذّر تجهيز مكتبة (شبكة متقطّعة مثلًا) لا نُطالب المستخدم بأي خطوة:
# يُسجَّل الاسم هنا، وعند أول تشغيل تالٍ — أو أول مرة تعود فيها الشبكة — يُثبَّت
# في الخلفية قبل تشغيل السكربت، فيعمل المشروع بلا أن يلمس المستخدم شيئًا.
# ——————————————————————————————————————————————————————————————————————————————
mahdi_pending_file() {
  printf '%s' "${HOME:-/root}/.mahdi/pending_deps"
}

mahdi_pending_add() {
  local file pkg
  file="$(mahdi_pending_file)"
  mkdir -p "$(dirname "$file")" 2>/dev/null || true
  for pkg in "$@"; do
    [ -z "$pkg" ] && continue
    grep -qxF "$pkg" "$file" 2>/dev/null && continue
    printf '%s\n' "$pkg" >>"$file" 2>/dev/null || true
  done
  # سقف بسيط كي لا ينمو الملف بلا حدّ
  if [ -f "$file" ] && [ "$(wc -l <"$file" 2>/dev/null || echo 0)" -gt 40 ]; then
    tail -n 40 "$file" >"$file.tmp" 2>/dev/null && mv "$file.tmp" "$file" 2>/dev/null || true
  fi
  return 0
}

# يثبّت ما تأجّل عند توفّر الشبكة. صامت تمامًا عند الفشل، وسطر واحد عند النجاح.
mahdi_pending_flush() {
  local file pkg left=() done_count=0
  file="$(mahdi_pending_file)"
  [ -s "$file" ] || return 0
  mahdi_deps_net_ok || return 0

  while IFS= read -r pkg; do
    [ -z "$pkg" ] && continue
    if "${MAHDI_PY:-python3}" -m pip install --upgrade --prefer-binary --no-input --quiet \
      --disable-pip-version-check --root-user-action ignore --retries 1 --timeout 20 \
      "$pkg" >/dev/null 2>&1; then
      done_count=$((done_count + 1))
    else
      left+=("$pkg")
    fi
  done <"$file"

  if [ ${#left[@]} -eq 0 ]; then
    rm -f "$file" 2>/dev/null || true
  else
    printf '%s\n' "${left[@]}" >"$file" 2>/dev/null || true
  fi

  if [ "$done_count" -gt 0 ]; then
    printf '\033[32m✔\033[0m  \033[2mأُكمل تجهيز %s مكتبة مؤجّلة\033[0m\n' "$done_count" || true
  fi
  return 0
}

# فحص اتصال حقيقي يُخزَّن للجلسة، فلا يُعاد مع كل حزمة.
#
# 9.5.1 — سبب «المكتبة غير مثبّتة» بلا أي محاولة: كان الفحص يعتمد على مصدر واحد
# (‏curl إلى pypi.org/simple بمهلة ٣ ثوانٍ). فإن تعثّر DNS لثانية، أو غاب curl من
# الصورة، أو حُجب pypi وحده في بلد المستخدم، يُعلَن «لا اتصال» وتُتجاهل كل المرايا
# — ثم يظهر تلميح «أعد التشغيل بعد وصل الإنترنت» ولم يُجرَّب تثبيت واحد.
#
# الآن: اتصال TCP حقيقي لكل المضيفين المعروفين، والنجاح في **أي** واحد منهم يعني
# أن الشبكة تعمل. وإذا فشلت كلها لا نمنع المحاولة: المثبّت الذكي يجرّب بنفسه.
_mahdi_tcp_ok() {
  local host="$1"
  "${MAHDI_PY:-python3}" - "$host" <<'PROBE' >/dev/null 2>&1
import socket, sys
socket.setdefaulttimeout(1.5)
try:
    socket.create_connection((sys.argv[1], 443), timeout=1.5).close()
except Exception:
    sys.exit(1)
PROBE
}

mahdi_deps_net_ok() {
  [ "$MAHDI_NET_STATE" = "ok" ] && return 0
  [ "$MAHDI_NET_STATE" = "off" ] && return 1

  local host
  local -a hosts=()
  if [ -n "$MAHDI_PIP_INDEX" ]; then
    host="${MAHDI_PIP_INDEX#*://}"; host="${host%%/*}"; host="${host%%:*}"
    [ -n "$host" ] && hosts+=("$host")
  fi
  hosts+=(pypi.org pypi.tuna.tsinghua.edu.cn mirrors.aliyun.com)

  for host in "${hosts[@]}"; do
    if _mahdi_tcp_ok "$host"; then
      MAHDI_NET_STATE=ok
      return 0
    fi
  done

  # احتياط أخير: curl أو wget (إن وُجدا) — قد ينجحان حيث يفشل اتصال بايثون
  if mahdi_have curl; then
    curl -fsS --connect-timeout 3 --max-time 5 -o /dev/null "https://pypi.org/simple/" >/dev/null 2>&1 &&
      { MAHDI_NET_STATE=ok; return 0; }
  elif mahdi_have wget; then
    wget -q --spider --timeout=5 "https://pypi.org/simple/" >/dev/null 2>&1 &&
      { MAHDI_NET_STATE=ok; return 0; }
  fi

  MAHDI_NET_STATE=off
  return 1
}

# 9.3.0: محرّك التثبيت الشامل (سلاسل بدائل لكل مكتبة)
if [ -f "${LOCAL:-/home}/bin/rescue" ]; then
  # shellcheck disable=SC1091
  . "${LOCAL:-/home}/bin/rescue"
fi

# في حال تشغيل الملف مستقلة دون utils
if ! declare -f info >/dev/null 2>&1; then
  info() { printf '\n[INFO] %s\n' "$*"; }
fi
if ! declare -f warn >/dev/null 2>&1; then
  warn() { printf '\n[WARN] %s\n' "$*"; }
fi

mahdi_have() { command -v "$1" >/dev/null 2>&1; }

# —————————————————————— رسائل الفشل بلغة عربية هادئة (6.1.0) ——————————————————————
# قبل 6.1.0 كان المستخدم يرى عشرات أسطر:
#   WARNING: Retrying (Retry(total=4, ...)) after connection broken ...
#   ...
#   ModuleNotFoundError: No module named 'telegram'
# الآن: سطر أو سطران، بلا English ولا traceback.

mahdi_deps_offline() {
  printf '\033[33m⚠ لا يوجد اتصال بالإنترنت الآن — تعذّر تجهيز: %s\033[0m\n' "$1" >&2
  printf '\033[2m  سيكتمل التجهيز تلقائيًا عند عودة الشبكة — لا إعداد مطلوب منك\033[0m\n' >&2
}

mahdi_deps_missing() {
  # 9.5.1: صامت افتراضيًا. كان يطبع ⚠ ثم يُطبع سبب آخر من الإصلاح الذاتي ثم ✖
  # النتيجة — ثلاث رسائل لسبب واحد. الآن يتولّى محرّك الإصلاح الذاتي العرض
  # (سطر ⚙ أثناء المحاولة، ثم ⚠ سبب واحد، ثم ✖ نتيجة واحدة، ثم ↳ إجراء واحد).
  [ "${MAHDI_DEPS_VERBOSE:-0}" = "1" ] || return 0
  printf '\033[33m⚠ تعذّر تجهيز المكتبات: %s\033[0m\n' "$1" >&2
}

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
  "${MAHDI_PY:-python3}" -m pip --version >/dev/null 2>&1 && return 0

  "${MAHDI_PY:-python3}" -m ensurepip --upgrade --user >/dev/null 2>&1 && "${MAHDI_PY:-python3}" -m pip --version >/dev/null 2>&1 && return 0
  "${MAHDI_PY:-python3}" -m ensurepip --default-pip >/dev/null 2>&1 && "${MAHDI_PY:-python3}" -m pip --version >/dev/null 2>&1 && return 0

  info "تنزيل get-pip.py (بلا apt)…"
  if mahdi_have curl || mahdi_have wget; then
    if mahdi_have curl; then
      curl -fsSL --retry 3 --connect-timeout 20 --max-time 120 \
        https://bootstrap.pypa.io/get-pip.py -o "${TMPDIR:-/tmp}/get-pip.py" || true
    else
      wget -q -T 120 -O "${TMPDIR:-/tmp}/get-pip.py" https://bootstrap.pypa.io/get-pip.py || true
    fi
    if [ -s "${TMPDIR:-/tmp}/get-pip.py" ]; then
      "${MAHDI_PY:-python3}" "${TMPDIR:-/tmp}/get-pip.py" --user --no-warn-script-location >/dev/null 2>&1 || true
      "${MAHDI_PY:-python3}" -m pip --version >/dev/null 2>&1 && return 0
    fi
  fi

  # آخر خيار: apt بمهلة محدودة
  mahdi_apt_install python3-pip >/dev/null 2>&1 || true
  "${MAHDI_PY:-python3}" -m pip --version >/dev/null 2>&1
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

  if ! "${MAHDI_PY:-python3}" -m pip --version >/dev/null 2>&1; then
    info "pip غير مثبّت — جاري تجهيزه بلا انتظار أقفال النظام…"
    mahdi_bootstrap_pip || true
  fi

  # بعض السكربتات تستدعي «python» بدل «python3»، وبعضها «pip» بدل «pip3»
  if ! mahdi_have python; then
    printf '#!/bin/sh\nexec %s "$@"\n' "${MAHDI_PY:-python3}" > "$HOME/.local/bin/python" 2>/dev/null || true
    chmod +x "$HOME/.local/bin/python" 2>/dev/null || true
  fi

  local shim
  for shim in pip pip3; do
    if ! mahdi_have "$shim"; then
      printf '#!/bin/sh\nexec %s -m pip "$@"\n' "${MAHDI_PY:-python3}" > "$HOME/.local/bin/$shim" 2>/dev/null || true
      chmod +x "$HOME/.local/bin/$shim" 2>/dev/null || true
    fi
  done
  hash -r 2>/dev/null || true
  "${MAHDI_PY:-python3}" -m pip --version >/dev/null 2>&1
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
    local rc=0
    # || rc=$? : المشغّل يعمل بـ set -e، فأي رمز فشل غير مُختبَر يُنهي الجلسة
    "${MAHDI_PY:-python3}" "$script" --name "$name" "$@" || rc=$?
    [ "$rc" -eq 0 ] && return 0
    # ٤ = الحزمة غير موجودة أصلًا ⇒ لا فائدة من أي محاولة أخرى
    [ "$rc" -eq 4 ] && return 4
    # 9.5.1: لم نعد نتوقّف عند رمز «لا اتصال» — المثبّت نفسه صار يجرب كل
    # المصادر، وإن عاد برمز الانقطاع فمعناه أنه جرّبها فعلًا وفشلت كلها.
  fi
  if declare -f mahdi_prog_start >/dev/null 2>&1; then
    mahdi_prog_start "$name" "تثبيت مباشر بـ pip…"
    # 8.0.0: كان هنا «mahdi_prog 40» ثابتًا — فيبقى الشريط جامدًا على 40٪ حتى
    # ينتهي pip (وهو ما شكا منه المستخدم في اللقطة). الآن نبضة تُحرّك النسبة.
    mahdi_prog 8 "تثبيت المكتبة" "$name"
  fi
  declare -f mahdi_prog_creep_start >/dev/null 2>&1 && mahdi_prog_creep_start 94
  # 6.1.0: كل ضجيج pip إلى ملف مؤقت — الطرفية تعرض سطرًا واحدًا فقط عند الفشل
  local log="${TMPDIR:-/tmp}/mahdi-pip-$$.log"
  local _prc=0
  # 9.5.1: محاولة واحدة فقط هنا. المثبّت الذكي جرّب خمس مرايا بالفعل، وتكرارها
  # في هذه الطبقة كان يضاعف زمن الفشل بلا أي احتمال جديد للنجاح.
  "${MAHDI_PY:-python3}" -m pip install --upgrade --prefer-binary --disable-pip-version-check \
    --quiet --no-input --retries 1 --timeout 15 "$@" >"$log" 2>&1 || _prc=$?
  declare -f mahdi_prog_creep_stop >/dev/null 2>&1 && mahdi_prog_creep_stop
  if [ "$_prc" -eq 0 ]; then
    rm -f "$log" 2>/dev/null || true
    declare -f mahdi_prog_done >/dev/null 2>&1 && mahdi_prog_done "تم التثبيت" "$name"
    return 0
  fi
  rm -f "$log" 2>/dev/null || true
  # رمز ٢ = كل المصادر غير قابلة للوصول (يفحص الشبكة نفسها، لا الفحص القديم)
  mahdi_deps_net_ok || return 2
  return 1
}

mahdi_pip_one() {
  local pkg="$1"
  local log="${TMPDIR:-/tmp}/mahdi-pip-one-$$.log"
  # 9.5.1: الدفعة سبق أن مرّت على المثبّت الذكي؛ هنا نجرّب المصادر مباشرة
  # (بدل إعادة تشغيل المثبّت نفسه لحزمة واحدة = عمل مكرّر وزمن مضاعف).
  if declare -f mahdi_nodist_known >/dev/null 2>&1 && mahdi_nodist_known "$pkg"; then
    mahdi_deps_mark_failed "$pkg"
    return 3
  fi
  local idx
  for idx in "https://pypi.org/simple" \
    "https://pypi.tuna.tsinghua.edu.cn/simple" \
    "https://mirrors.aliyun.com/pypi/simple" \
    "https://pypi.mirrors.ustc.edu.cn/simple"; do
    "${MAHDI_PY:-python3}" -m pip install --upgrade --prefer-binary --no-input --quiet \
      --disable-pip-version-check --retries 2 --timeout 20 -i "$idx" "$pkg" >"$log" 2>&1 && {
      rm -f "$log" 2>/dev/null || true
      return 0
    }
  done
  rm -f "$log" 2>/dev/null || true
  mahdi_deps_mark_failed "$pkg"
  # 9.5.1: لا رسالة هنا — الإصلاح الذاتي هو من يعرض السبب النهائي مرة واحدة.
  return 1
}

mahdi_pip_install_many() {
  local pkgs=("$@")
  local total=${#pkgs[@]}
  [ "$total" -eq 0 ] && return 0

  local label="${pkgs[*]}"
  local batch_rc=0
  if mahdi_pip_run "$label" "${pkgs[@]}"; then
    declare -f mahdi_prog_done >/dev/null 2>&1 && mahdi_prog_done "تم تثبيت المكتبات" "$label"
    return 0
  else
    batch_rc=$?
  fi

  # 8.3.0: الدفعة فشلت بلا اتصال ⇒ إعادة كل حزمة على حدة كانت تُضاعف
  # الانتظار أربع مرات بلا أي فائدة. نُسجّل الفشل ونعود فورًا.
  if [ "$batch_rc" -eq 2 ]; then
    mahdi_deps_mark_failed "${pkgs[@]}"
    return 2
  fi
  # 9.5.1: «لا وجود للحزمة» ليس فشلًا شبكيًا، ولا يُصلحه أي إصدار أو بناء.
  if [ "$batch_rc" -eq 4 ]; then
    mahdi_deps_mark_failed "${pkgs[@]}"
    return 3
  fi

  local p rc=0 succeeded=0
  for p in "${pkgs[@]}"; do
    rc=0
    mahdi_pip_one "$p" || rc=$?
    [ "$rc" -eq 2 ] && { mahdi_deps_mark_failed "${pkgs[@]}"; return 2; }
    [ "$rc" -eq 0 ] && succeeded=$((succeeded + 1))
  done
  # 8.3.0: كان يعيد 0 حتى لو فشلت كل الحزم، فيُطبع «المكتبات جاهزة»
  # كذبًا ثم يُعاد التشغيل في self_heal. الآن الفشل يعود كما هو.
  [ "$succeeded" -eq 0 ] && return 1
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


# 9.5.1 — «هل هذا مجلد مشروع أم مجلّد تنزيلات؟»
#
# كان الفحص يقرأ **٦٠ ملفًا** من مجلد السكربت أيًّا كان. فإن نُزِّل سكربت من
# تليجرام إلى Download (وفيه عشرات السكربتات الأخرى)، تُجمَع وارداتها كلها
# وتُثبَّت حزم لا علاقة لها بالملف — انتظار طويل وأخطاء تثبيت لا معنى لها.
# الآن الفحص الواسع يقتصر على مجلدات المشاريع الحقيقية (فيها ملف متطلبات)،
# ونقرأ الملف نفسه دائمًا.
PROJECT_MARKERS = (
    "requirements.txt", "pyproject.toml", "setup.py", "setup.cfg",
    "package.json", "Pipfile", "environment.yml", "poetry.lock",
)


def looks_like_project(directory):
    if not directory or not os.path.isdir(directory):
        return False
    try:
        entries = set(os.listdir(directory))
    except OSError:
        return False
    return any(marker in entries for marker in PROJECT_MARKERS)


# 9.3.1 — أسماء تتكرّر في كل مشروع ولا تكون حزمة PyPI (‏`core` · `utils` …).
# كانت تُرسَل إلى pip فتُنتج «المكتبة ”core“ غير مثبّتة» وهي وحدة داخل المشروع.
LOCAL_NAMES = {
    "core", "utils", "util", "helpers", "helper", "config", "configs", "settings",
    "models", "model", "views", "controllers", "controller", "routes", "router",
    "routers", "handlers", "handler", "services", "service", "api", "apis", "app",
    "main", "run", "bot", "client", "server", "database", "db", "dao", "schemas",
    "schema", "serializers", "forms", "admin", "auth", "lib", "libs", "src",
    "source", "sources", "tests", "test", "scripts", "static", "templates",
    "migrations", "plugins", "plugin", "modules", "module", "components",
    "component", "engine", "engines", "agents", "agent", "tools", "tool",
    "crawler", "scraper", "scrapers", "workers", "worker", "tasks", "task",
    "jobs", "job", "pipelines", "pipeline", "storage", "cache", "logger",
    "logging", "exceptions", "errors", "constants", "enums", "types", "shared",
    "common", "middleware", "security", "session", "sessions", "user", "users",
    "data", "dataset", "datasets", "train", "training", "inference", "predict",
}


def is_local(module, directory):
    if not module:
        return False
    name = module.split(".")[0]
    if name.lower() in LOCAL_NAMES:
        return True
    if not directory:
        return False
    # نفحص مجلد السكربت ثم مجلدين أعلى منه (‏src/ · project/ · …)
    probe = os.path.abspath(directory)
    for _ in range(3):
        for candidate in (
            os.path.join(probe, name + ".py"),
            os.path.join(probe, name, "__init__.py"),
            os.path.join(probe, name),
        ):
            if os.path.exists(candidate):
                return True
        parent = os.path.dirname(probe)
        if not parent or parent == probe:
            break
        probe = parent
    return False


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

    # 9.3.1 — نفحص المشروع كله لا الملف وحده.
    # مكتبة يستوردها `core/db.py` أو `handlers/start.py` لم تكن تُكتشف إطلاقًا
    # (كان الفحص يقرأ الملف المُشغَّل فقط)، فينهار السكربت عند أول استيراد
    # داخلي. الآن نقرأ ملفات المشروع حتى ٦٠ ملفًا وبحدّ حجم آمن.
    if os.path.isdir(directory) and looks_like_project(directory):
        skip_dirs = {
            "node_modules", ".git", "__pycache__", ".venv", "venv", "env",
            "build", "dist", ".gradle", ".idea", ".vscode", "target", ".next",
        }
        scanned, budget = 0, 1_500_000
        for dirpath, dirnames, filenames in os.walk(directory):
            dirnames[:] = [
                d for d in dirnames if d not in skip_dirs and not d.startswith(".")
            ]
            for fname in filenames:
                if not fname.endswith(".py"):
                    continue
                full = os.path.join(dirpath, fname)
                if full == os.path.abspath(path):
                    continue
                # ملفات مساعدة داخلية ليست جزءًا من مشروع المستخدم
                if fname.startswith("mahdi_"):
                    continue
                try:
                    size = os.path.getsize(full)
                except OSError:
                    continue
                if size > 400_000 or budget <= 0:
                    continue
                try:
                    with open(full, encoding="utf-8", errors="ignore") as fh:
                        other = fh.read()
                except OSError:
                    continue
                budget -= size
                scanned += 1
                parse_imports(other, mods)
                parse_pip_lines(other, extra)
                if scanned >= 60:
                    break
            if scanned >= 60:
                break

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

  # 8.4.0: فحص وقائي — مكتبة موجودة لكنها تفتقر ميزة يستخدمها السكربت
  # (مثل telebot بلا AsyncTeleBot). نُصلحها قبل أول تشغيل فلا يظهر الخطأ أصلًا.
  local preflight_helper="${LOCAL:-/home}/bin/mahdi_heal.py"
  if [ -f "$preflight_helper" ] && declare -f mahdi_heal_repair >/dev/null 2>&1; then
    local preflight
    preflight="$("${MAHDI_PY:-python3}" "$preflight_helper" --preflight --file "$file" 2>/dev/null || true)"
    if [ -n "$preflight" ]; then
      mahdi_heal_repair "$preflight" || true
    fi
  fi

  local dir
  dir=$(cd "$(dirname "$file")" 2>/dev/null && pwd) || dir="$(dirname "$file")"

  # 8.5.0: ملف متطلبات فارغ أو معلّقات فقط لا يستحق سطرًا ولا عملية pip.
  if [ -f "$dir/requirements.txt" ] &&
    grep -qEv '^[[:space:]]*(#|$)' "$dir/requirements.txt" 2>/dev/null; then
    info "متطلبات requirements.txt…"
    mahdi_pip_run "requirements.txt" -r "$dir/requirements.txt" ||
      warn "بعض متطلبات requirements.txt لم تُثبَّت"
  fi

  mahdi_py_plan_write
  local -a missing=()
  while IFS= read -r line; do
    [ -n "$line" ] && missing+=("$line")
  done < <("${MAHDI_PY:-python3}" /tmp/mahdi_py_plan.py "$file" 2>/dev/null)

  # 3.0.0: صمت تام — لا تُطبع أي رسالة إذا كانت كل المكتبات موجودة
  if [ ${#missing[@]} -eq 0 ]; then
    return 0
  fi

  # 9.5.1: الفحص إرشادي. إن نجح نجرّب كل شيء، وإن فشل لا نُعلن الفشل مسبقًا —
  # المثبّت الذكي يحاول المخزن ثم المصادر البعيدة بنفسه، وهناك شبكات تُفشل
  # الفحص وتنجح معها pip (‏IPv6 · اختلاف SNI · وسيط شفّاف).
  #
  # ونتيجة الفحص تُحسب **قبل** تصفية «الحزم الفاشلة»: كانت التصفية تسبقه، فإن
  # كانت كل الحزم مُعلَّمة بالفشل يعود الأمر قبل أن تُمسح العلامات أصلًا.
  if mahdi_deps_net_ok; then
    MAHDI_OFFLINE=0
    # علامة «فشل سابق» لا تعني فشلًا حقيقيًا (قد تكون من فحص متشائم أو انقطاع
    # عابر). ما دامت الشبكة تعمل، كل شيء يُعاد فحصه وتثبيته من جديد.
    MAHDI_DEPS_FAILED=" "
  else
    MAHDI_OFFLINE=1
  fi

  # حزم سبق أن فشل تثبيتها في هذه الجلسة: لا نُعيد انتظار الشبكة عبثًا
  local -a fresh=() m
  for m in "${missing[@]}"; do
    mahdi_deps_failed "$m" || fresh+=("$m")
  done
  if [ ${#fresh[@]} -eq 0 ]; then
    mahdi_deps_missing "${missing[*]}"
    return 3
  fi

  local pip_rc=0
  mahdi_pip_install_many "${fresh[@]}" || pip_rc=$?

  # 9.3.0: «إن فشل إصدار ثبّته بغيره». كل حزمة فشلت في الدفعة تُمرَّر على سلسلة
  # البدائل وحدها: ويل فقط ← إصدارات أقدم ← بناء من المصدر ← حزمة النظام ←
  # مصدر بديل. كانت الدفعة الفاشلة تُعلَن فشلًا نهائيًا بلا محاولة أخرى.
  if [ "$pip_rc" -eq 1 ] && declare -f mahdi_py_install_any >/dev/null 2>&1; then
    local pkg_name rescued=0 still=()
    for pkg_name in "${fresh[@]}"; do
      if mahdi_py_install_any "$pkg_name" >/dev/null 2>&1; then
        rescued=$((rescued + 1))
      else
        still+=("$pkg_name")
      fi
    done
    if [ "${#still[@]}" -eq 0 ]; then
      pip_rc=0
    else
      fresh=("${still[@]}")
    fi
    [ "$rescued" -gt 0 ] && [ "${#still[@]}" -eq 0 ] && success "أُعيد تجهيز المكتبات بطريقة أخرى" 2>/dev/null || true
  fi

  if [ "$pip_rc" -ne 0 ]; then
    # 9.5.1: الانقطاع ليس «فشلًا» للحزمة — لا نُسجّله كعلامة دائمة، فربما كان
    # الفحص متشائمًا. الرسالة العربية طُبعت من المثبّت، لا نكرّرها.
    if [ "$pip_rc" -eq 2 ]; then
      declare -f mahdi_pending_add >/dev/null 2>&1 && mahdi_pending_add "${fresh[@]}"
      return 2
    fi
    mahdi_deps_mark_failed "${fresh[@]}"
    mahdi_deps_missing "${fresh[*]}"
    return 3
  fi

  if declare -f success >/dev/null 2>&1; then
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

  # 9.3.0: كان السطر `mahdi_have node || return 0` يترك كل مشروع Node على
  # جهاز بلا Node بلا أي محاولة تثبيت — فيفشل السكربت بـ«الأمر غير موجود».
  if ! mahdi_have node; then
    if declare -f mahdi_node_ensure >/dev/null 2>&1; then
      mahdi_node_ensure >/dev/null 2>&1 || true
    fi
  fi
  mahdi_have node || return 0

  local dir
  dir=$(cd "$(dirname "$file")" 2>/dev/null && pwd) || dir="$(dirname "$file")"

  # 8.5.0: لا `npm install` لمشروع بلا اعتماديات ولا بعد تثبيتها سابقًا
  if [ -f "$dir/package.json" ] && mahdi_have npm &&
    grep -qE '"(dependencies|devDependencies)"[[:space:]]*:' "$dir/package.json" 2>/dev/null &&
    [ ! -d "$dir/node_modules" ]; then
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

  # 8.0.0: مرآة npm احتياطية — إن فشل السجل الرسمي (بطء/حجب) نعيد المحاولة على
  # مرآة سريعة مرة واحدة، بدل أن يفشل التثبيت ويترك المستخدم بلا شيء.
  if [ -w "$dir" ]; then
    (cd "$dir" && npm install --no-fund --no-audit --loglevel=error "${registry_args[@]}" "${missing[@]}") || {
      (cd "$dir" && npm install --no-fund --no-audit --loglevel=error --registry "$MAHDI_NPM_MIRROR" "${missing[@]}") ||
        npm install -g --no-fund --no-audit --loglevel=error "${registry_args[@]}" "${missing[@]}" ||
        warn "تعذّر تثبيت بعض مكتبات Node"
      export NODE_PATH="$(npm root -g 2>/dev/null):${NODE_PATH:-}"
    }
  else
    npm install -g --no-fund --no-audit --loglevel=error "${registry_args[@]}" "${missing[@]}" ||
      npm install -g --no-fund --no-audit --loglevel=error --registry "$MAHDI_NPM_MIRROR" "${missing[@]}" ||
      warn "تعذّر تثبيت بعض مكتبات Node"
    export NODE_PATH="$(npm root -g 2>/dev/null):${NODE_PATH:-}"
  fi

  # 9.3.0: أي حزمة بقيت غير مثبَّتة تُمرَّر على السلسلة البديلة وحدها
  local name
  for name in "${missing[@]}"; do
    mahdi_node_installed "$name" "$dir" && continue
    if declare -f mahdi_npm_install_any >/dev/null 2>&1; then
      mahdi_npm_install_any "$name" "$dir" >/dev/null 2>&1 || true
    fi
  done

  local -a left=()
  for name in "${missing[@]}"; do
    mahdi_node_installed "$name" "$dir" || left+=("$name")
  done
  if [ ${#left[@]} -gt 0 ]; then
    mahdi_deps_missing "${left[*]}"
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
  # ملاحظة: الفشل لا يُوقف التشغيل — سكربت قد يعمل بلا المكتبة الناقصة،
  # والمشغّل يعرض رسالة عربية واحدة ويكمل (6.1.0).
  case "$file" in
    *.py) mahdi_py_deps "$file" || true ;;
    *.js | *.mjs | *.cjs | *.ts | *.tsx) mahdi_node_deps "$file" || true ;;
    *.php) mahdi_php_deps "$file" || true ;;
  esac
  return 0
}

# تشغيل مباشر:  bash auto_deps.sh /path/to/script.py
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  mahdi_auto_deps "$1"
fi
