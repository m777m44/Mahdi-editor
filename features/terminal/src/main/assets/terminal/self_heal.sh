# shellcheck shell=bash
# ——————————————————————————————————————————————————————————————————————————————
# Mahdi-Editor — التثبيت التلقائي بعد الفشل (5.5.0)
#
# الفكرة: قبل التشغيل نفحص السكربت (auto_deps) — لكن بعض المكتبات لا تظهر إلا
# وقت التنفيذ (استيراد داخل دالة، أو مكتبة تُنزّل نفسها، أو استيراد ديناميكي).
# هنا نمسك الحالة الأخرى:
#
#   1. نُشغّل السكربت ونحتفظ بـ stderr في ملف مؤقت (stdout يظهر مباشرة).
#   2. إن فشل، نحلّل stderr: `ModuleNotFoundError: No module named 'X'`،
#      `Cannot find module 'x'`، `Class "…" not found`.
#   3. نثبّت الناقص فقط: pip / npm / composer — بشريط تقدّم **داخل الطرفية**:
#      سطر واحد، ٢٠ خلية، يُعاد رسمه بـ \\r عند تغيّر النسبة فقط.
#   4. نُعيد تشغيل السكربت — بحدّ أقصى ٣ محاولات، ثم نتوقّف ونُظهر سبب الفشل.
#
# كل شيء قابل للتعطيل: MAHDI_AUTO_DEPS=0 أو MAHDI_HEAL=0
# ——————————————————————————————————————————————————————————————————————————————

MAHDI_HEAL="${MAHDI_HEAL:-1}"

if ! declare -f mahdi_bar_start >/dev/null 2>&1; then
  mahdi_bar_start() { :; }
  mahdi_bar_set() { :; }
  mahdi_bar_done() { :; }
  mahdi_bar_abort() { :; }
fi

# مسار مساعد التحليل (بايثون)
mahdi_heal_helper() {
  local candidate="${LOCAL:-/home}/bin/mahdi_heal.py"
  if [ -f "$candidate" ]; then
    printf '%s' "$candidate"
    return 0
  fi
  candidate="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)/mahdi_heal.py"
  [ -f "$candidate" ] && printf '%s' "$candidate"
}

# يحلّل ملف stderr ويطبع أسماء الحزم (سطر لكل حزمة)
mahdi_heal_detect() {
  local lang="$1" errfile="$2" script="$3"
  local helper
  helper="$(mahdi_heal_helper)"
  [ -n "$helper" ] && [ -f "$helper" ] || return 1
  command -v python3 >/dev/null 2>&1 || return 1
  python3 "$helper" --lang "$lang" --stderr "$errfile" --file "$script" 2>/dev/null
}

# تثبيت حزمة واحدة حسب اللغة، بشريط تقدّم داخل الطرفية
mahdi_heal_install() {
  local lang="$1" pkg="$2"
  local label

  case "$lang" in
    python)
      label="pip install $pkg"
      if declare -f mahdi_pip_run >/dev/null 2>&1; then
        # المثبّت الذكي يرسم النسبة الحقيقية بنفسه
        mahdi_pip_run "$label" "$pkg"
        return $?
      fi
      mahdi_bar_start "$label"
      python3 -m pip install --upgrade --prefer-binary --disable-pip-version-check \
        --quiet --no-input --retries 3 --timeout 30 "$pkg" 2>/dev/null
      local rc=$?
      if [ "$rc" -eq 0 ]; then
        mahdi_bar_done "$pkg ✓"
      else
        mahdi_bar_abort
      fi
      return "$rc"
      ;;

    node)
      label="npm install $pkg"
      mahdi_bar_start "$label"
      if command -v npm >/dev/null 2>&1; then
        npm install --no-fund --no-audit --loglevel=error "$pkg" >/dev/null 2>&1
        local rc=$?
        if [ "$rc" -ne 0 ]; then
          npm install -g --no-fund --no-audit --loglevel=error "$pkg" >/dev/null 2>&1
          rc=$?
        fi
        if [ "$rc" -eq 0 ]; then
          mahdi_bar_done "$pkg ✓"
        else
          mahdi_bar_abort
        fi
        return "$rc"
      fi
      mahdi_bar_abort
      return 1
      ;;

    php)
      label="composer require $pkg"
      if ! command -v composer >/dev/null 2>&1; then
        return 1
      fi
      mahdi_bar_start "$label"
      COMPOSER_ALLOW_SUPERUSER=1 composer require --no-interaction --quiet "$pkg" >/dev/null 2>&1
      local rc=$?
      if [ "$rc" -eq 0 ]; then
        mahdi_bar_done "$pkg ✓"
      else
        mahdi_bar_abort
      fi
      return "$rc"
      ;;

    *)
      return 1
      ;;
  esac
}

# ——————————————————————————————————————————————————————————————————————————————
# mahdi_run_heal <lang> <script> <command…>
#
# يُشغّل الأمر، وإن فشل بسبب مكتبة ناقصة يثبّتها ويعيد المحاولة (٣ محاولات كحد أقصى).
# يعيد رمز خروج آخر تشغيل، ومعه صفر إن نجح في أي محاولة.
# ——————————————————————————————————————————————————————————————————————————————
mahdi_run_heal() {
  local lang="$1"
  local script="$2"
  shift 2

  local max_attempts=3
  local attempt=1
  local errfile
  errfile="$(mktemp "/tmp/mahdi-heal-XXXXXX.log" 2>/dev/null || echo "/tmp/mahdi-heal-$$.log")"

  local rc=1
  while [ "$attempt" -le "$max_attempts" ]; do
    : >"$errfile"

    set +e
    "$@" 2>"$errfile"
    rc=$?
    set -e

    # أظهر ما كتبه السكربت على stderr كما هو (بلا تكرار ولا تلوين إضافي)
    if [ -s "$errfile" ]; then
      sed -e 's/\r$//' "$errfile" >&2 || true
    fi

    [ "$rc" -eq 0 ] && break
    [ "$MAHDI_HEAL" = "0" ] && break
    [ "$MAHDI_AUTO_DEPS" = "0" ] && break

    local pkgs
    pkgs="$(mahdi_heal_detect "$lang" "$errfile" "$script" || true)"
    [ -z "$pkgs" ] && break

    printf '\033[33m⟳ مكتبات ناقصة: %s\033[0m\n' "$(printf '%s' "$pkgs" | tr '\n' ' ')"

    local failed=0
    local pkg
    while IFS= read -r pkg; do
      [ -z "$pkg" ] && continue
      if ! mahdi_heal_install "$lang" "$pkg"; then
        printf '\033[31m✖ تعذّر تثبيت %s\033[0m\n' "$pkg"
        failed=1
        break
      fi
    done <<<"$pkgs"

    [ "$failed" -eq 1 ] && break

    attempt=$((attempt + 1))
    if [ "$attempt" -le "$max_attempts" ]; then
      printf '\033[36m↻ إعادة تشغيل السكربت (محاولة %d من %d)\033[0m\n' "$attempt" "$max_attempts"
    fi
  done

  rm -f "$errfile" 2>/dev/null || true
  return "$rc"
}

# تشغيل مستقل للاختبار: bash self_heal.sh python /path/script.py python3 /path/script.py
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  lang="$1"
  file="$2"
  shift 2
  mahdi_run_heal "$lang" "$file" "$@"
fi
