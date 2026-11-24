# shellcheck shell=bash
# ——————————————————————————————————————————————————————————————————————————————
# 10.1.0 — دليل إصلاح المكتبات العلمية (numpy وما شابه) في بيئة Mahdi-Editor
#
# خلفية القرار، لأن النصيحة الشائعة على الإنترنت **خاطئة هنا**:
#
#   التطبيق لا يستخدم Termux. يستخدم جذر أوبنتو مضمّنًا يعمل تحت PRoot
#   (‏`sandboxDir()/usr/bin` مع apt). لذلك:
#
#     ✗ `pkg install python-numpy`        ← pkg خاصّ بـTermux، غير موجود هنا
#     ✗ /data/data/com.termux/files/...   ← مسار Termux، لا معنى له داخل PRoot
#     ✗ موزّع Termux يحلّ numpy عبر apt    ← ما يفعله التطبيق أصلًا في المحاولة ٧
#
#   الأسماء الصحيحة داخل أوبنتو (apt) هي:
#     python3-numpy · python3-scipy · python3-pandas · python3-matplotlib …
#   وهي **حزم مترجمة مسبقًا** لمعمارية الجهاز، فلا بناء من المصدر ولا انتظار.
#
# وجود هذه الدالّة ليس إعادة اختراع لسلسلة التثبيت القائمة (rescue.sh تتكفّل
# بسبع محاولات)، بل إصلاح ثغرتين:
#
#   ١) **الوقت**: البناء من المصدر آخر المحاولات، فينتظر المستخدم دقائق ثم يفشل.
#      للمكتبات العلمية نجرّب حزمة النظام **أولًا** لأنها الأنسب تقنيًا هنا.
#   ٢) **الصمت**: عند فشل كل شيء كانت الرسالة عامّة. الآن نقول السبب الحقيقي
#      ونعطي الإجراء الصحيح، ونشرح لماذا نصيحة Termux لا تنطبق.
# ——————————————————————————————————————————————————————————————————————————————

# المكتبات التي تُترجم بطيئًا أو تفشل على أندرويد، ولها حزمة نظام جاهزة.
# المفتاح = اسم حزمة PyPI، القيمة = اسم حزمة apt المكافئة.
MAHDI_SCIENTIFIC_APT=""
mahdi_scientific_apt_map() {
  [ -n "$MAHDI_SCIENTIFIC_APT" ] && return 0
  MAHDI_SCIENTIFIC_APT="
numpy:python3-numpy
scipy:python3-scipy
pandas:python3-pandas
matplotlib:python3-matplotlib
pillow:python3-pil
Pillow:python3-pil
opencv-python:python3-opencv
opencv-python-headless:python3-opencv
scikit-learn:python3-sklearn
sklearn:python3-sklearn
sympy:python3-sympy
lxml:python3-lxml
pyyaml:python3-yaml
PyYAML:python3-yaml
psutil:python3-psutil
requests:python3-requests
urllib3:python3-urllib3
cryptography:python3-cryptography
cffi:python3-cffi
bcrypt:python3-bcrypt
pycryptodome:python3-crypto
Pillow-SIMD:python3-pil
numba:python3-numba
h5py:python3-h5py
netCDF4:python3-netcdf4
sqlalchemy:python3-sqlalchemy
SQLAlchemy:python3-sqlalchemy
psycopg2:python3-psycopg2
psycopg2-binary:python3-psycopg2
mysqlclient:python3-mysqldb
redis:python3-redis
beautifulsoup4:python3-bs4
bs4:python3-bs4
"
}

# اسم حزمة apt المقابلة لحزمة PyPI — أو فشل.
# $1 اسم الحزمة على PyPI (قد يحمل إصدارًا أو إضافات: numpy==1.26 · requests[socks])
mahdi_scientific_apt_for() {
  local pkg="$1"
  [ -z "$pkg" ] && return 1
  mahdi_scientific_apt_map
  # إسقاط الإضافات والقيود: requests[socks]==2.31 ← requests
  pkg="${pkg%%\[*}"
  pkg="${pkg%%[<>=!~]*}"
  pkg="${pkg%% *}"
  local line key val
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    key="${line%%:*}"
    val="${line#*:}"
    # مطابقة تامّة غير حسّاسة لحالة الأحرف (numpy · Numpy · NUMPY سواء)
    if [ "${key,,}" = "${pkg,,}" ]; then
      printf '%s' "$val"
      return 0
    fi
  done <<<"$MAHDI_SCIENTIFIC_APT"
  return 1
}

# هل الحزمة «علمية/ثقيلة» أي يُستحسن فيها حزمة النظام قبل pip؟
mahdi_is_native_heavy() {
  mahdi_scientific_apt_for "$1" >/dev/null 2>&1
}

# تثبيت حزمة علمية عبر apt، مع التحقّق بالاستيراد الفعلي.
# $1 = حزمة PyPI · $2 = اسم الوحدة للتحقّق (اختياري)
# يعيد 0 إن نجح الاستيراد، 1 إن فشلت كل الطرق.
mahdi_scientific_install() {
  local pkg="$1" module="${2:-}" apt_pkg
  apt_pkg="$(mahdi_scientific_apt_for "$pkg")" || return 1

  if declare -f _mahdi_note >/dev/null 2>&1; then
    _mahdi_note "$pkg: حزمة النظام $apt_pkg (مترجمة مسبقًا)"
  fi

  if declare -f mahdi_apt_install_soft >/dev/null 2>&1; then
    mahdi_apt_install_soft "$apt_pkg" >/dev/null 2>&1 || true
  else
    apt-get install -y "$apt_pkg" >/dev/null 2>&1 || true
  fi

  # التحقّق بالاستيراد لا بحالة apt: قد يكون apt نجح والحزمة غير مرئية
  # للمفسّر الحالي (بيئة venv أو مفسّر مختلف) — وهذا فرق حقيقي.
  if declare -f _mahdi_py_import_ok >/dev/null 2>&1; then
    _mahdi_py_import_ok "${module:-$(_mahdi_scientific_module "$pkg")}" && return 0
  fi
  return 1
}

# اسم الوحدة القابل للاستيراد من اسم حزمة PyPI (‏pyyaml ← yaml).
_mahdi_scientific_module() {
  local pkg="$1"
  case "${pkg,,}" in
    pyyaml) printf 'yaml' ;;
    pillow) printf 'PIL' ;;
    scikit-learn | sklearn) printf 'sklearn' ;;
    opencv-python | opencv-python-headless) printf 'cv2' ;;
    pycryptodome) printf 'Crypto' ;;
    beautifulsoup4 | bs4) printf 'bs4' ;;
    mysqlclient) printf 'MySQLdb' ;;
    psycopg2 | psycopg2-binary) printf 'psycopg2' ;;
    sqlalchemy) printf 'sqlalchemy' ;;
    *) printf '%s' "${pkg%%\[*}" | tr '-' '_' ;;
  esac
}

# ——————————————————————————————————————————————————————————————————————————————
# رسالة الفشل: ماذا جرى فعلًا، وما الإجراء الصحيح **هنا**.
#
# لا نقول «ثبّتها بـpkg» لأن ذلك لا يعمل في هذا التطبيق، ويترك المستخدم
# يدور في حلقة لا تنتهي. نقول الحقيقة: البيئة أوبنتو تحت PRoot، والحلّ apt.
# ——————————————————————————————————————————————————————————————————————————————
mahdi_scientific_advice() {
  local pkg="$1" apt_pkg
  apt_pkg="$(mahdi_scientific_apt_for "$pkg" 2>/dev/null || true)"
  if [ -z "$apt_pkg" ]; then
    apt_pkg="python3-$pkg"
  fi
  printf '\n'
  printf '\033[1m\033[93m⚠ تعذّر تجهيز %s\033[0m\n' "$pkg"
  printf '\033[2m  لماذا: هذه الحزمة تحتاج ترجمة أصلية (C/C++/Fortran)،\n\033[0m'
  printf '\033[2m  وبناءها داخل أندرويد يفشل غالبًا أو يستغرق دقائق ثم يفشل.\n\033[0m'
  printf '\n'
  printf '\033[1m  الحلّ الصحيح داخل هذا التطبيق:\033[0m\n'
  printf '    \033[96mmh pkg install %s\033[0m\n' "$apt_pkg"
  printf '\033[2m  (‏apt داخل بيئة أوبنتو المضمّنة — نسخة مترجمة مسبقًا لمعمارية جهازك)\033[0m\n'
  printf '\033[2m  تنبيه: إن وجدت على الإنترنت أوامر Termux أو مسار\n\033[0m'
  printf '\033[2m  /data/data/com.termux/… فتجاهلها — هذا التطبيق يعمل في\n\033[0m'
  printf '\033[2m  بيئة أوبنتو خاصة به، لا داخل Termux.\n\033[0m'
  printf '\n'
}

# هل هذه أول مرة يفشل فيها هذا التعبير؟ يمنع تكرار نفس النصيحة كل تشغيل.
_mahdi_advised_file="${HOME:-/root}/.mahdi/advised.list"
mahdi_scientific_advise_once() {
  local pkg="$1" key
  key="$(printf '%s' "$pkg" | tr -c '[:alnum:]._-' '_')"
  mkdir -p "$(dirname "$_mahdi_advised_file")" 2>/dev/null || true
  if [ -f "$_mahdi_advised_file" ] && grep -qxF "$key" "$_mahdi_advised_file" 2>/dev/null; then
    return 0
  fi
  printf '%s\n' "$key" >>"$_mahdi_advised_file" 2>/dev/null || true
  mahdi_scientific_advice "$pkg"
}
