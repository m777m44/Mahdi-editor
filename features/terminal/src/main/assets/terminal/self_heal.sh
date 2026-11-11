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

# —————————————————————— عرض الأخطاء بلغة عربية (6.1.0) ——————————————————————
# قبل 6.1.0 كان stderr يُنسخ حرفيًا إلى الطرفية: عشرات أسطر pip
# («WARNING: Retrying…») ثم Traceback كامل ينتهي بـ ModuleNotFoundError.
# الآن: نُسقط الضجيج، ونحوّل أي أثر بايثون إلى سطر عربي واحد مفيد.

# اسم أول وحدة بايثون ناقصة في ملف stderr (فارغ إن لم توجد)
mahdi_heal_missing_module() {
  grep -oE "No module named '[^']+'" "$1" 2>/dev/null | tail -n 1 | cut -d"'" -f2
}

# سطور لا تفيد المستخدم إطلاقًا (pip / urllib3 / npm)
mahdi_heal_strip_noise() {
  grep -viE '^(WARNING: Retrying|WARNING: The repository|WARNING: You are using pip|WARNING: Value for|WARNING: There was an error|Looking in indexes|Collecting |  Downloading |  Using cached|  Preparing metadata|  Getting requirements|  Building wheel|  Created wheel|  Stored in|Requirement already satisfied|  Attempting uninstall|  Found existing installation|Successfully installed|Successfully built|note:|hint:|  error: subprocess-exited-with-error|Traceback \(most recent call last\)|  File "|npm ERR!|npm WARN)' "$1" 2>/dev/null |
    tr -d '\r' | grep -v '^[[:space:]]*$'
}

# يعرض خطأ التشغيل النهائي بشكل مفهوم ومختصر
#
# 8.3.0: قبل الآن كان `stderr` يُخزَّن في ملف ولا يُعرض إلا في حالتين، فإن
# فشل تثبيت مكتبة ناقصة بقي المستخدم أمام «✖ توقّف السكربت (الرمز 1)»
# بلا أي سبب. الآن: سبب واحد واضح دائمًا، حتى لو لم يكتب السكربت شيئًا.
mahdi_heal_report() {
  local lang="$1" errfile="$2" rc="${3:-1}"
  local mod=""

  if [ -s "$errfile" ]; then
    mod="$(mahdi_heal_missing_module "$errfile")"
  fi

  if [ -n "$mod" ]; then
    MAHDI_FAIL_REASON="مكتبة ناقصة: $mod"
    printf "${M_CORAL}✖ المكتبة «%s» غير مثبّتة — لم يعمل السكربت.${M_RESET}\n" "$mod" >&2
    # لا نطبع «pip install <module>»: اسم الوحدة يختلف عن اسم الحزمة في PyPI
    printf "${M_MUTED}  وصّل الإنترنت ثم أعد التشغيل، أو ثبّتها من: اللغات ← المشغّلات${M_RESET}\n" >&2
    return 0
  fi

  if [ -s "$errfile" ] && grep -q '^Traceback (most recent call last)' "$errfile"; then
    MAHDI_FAIL_REASON="خطأ في السكربت"
    printf "${M_CORAL}✖ توقّف السكربت بخطأ:${M_RESET}\n" >&2
    grep -E '^[A-Za-z_][A-Za-z0-9_.]*(Error|Exception):' "$errfile" | tail -n 2 >&2
    return 0
  fi

  if [ -s "$errfile" ]; then
    local lines
    lines="$(mahdi_heal_strip_noise "$errfile" | tail -n 12)"
    if [ -n "$lines" ]; then
      MAHDI_FAIL_REASON="خطأ في السكربت"
      printf '%s\n' "$lines" >&2
      return 0
    fi
  fi

  # stderr فارغ تمامًا: لا نترك المستخدم بلا تفسير
  MAHDI_FAIL_REASON="أنهاه السكربت بالرمز $rc"
  printf "${M_MUTED}  أنهاه السكربت بالرمز %s دون رسالة — افتح الملف وتحقّق من أوامره.${M_RESET}\n" "$rc" >&2
  return 0
}

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

  # 8.3.0: محرّك الاعتماديات جرّب هذه الحزمة وفشل (غالبًا بلا اتصال)؛
  # إعادة المحاولة هنا كانت رابع محاولة وتُضيف ثوانٍ بلا فائدة.
  if declare -f mahdi_deps_failed >/dev/null 2>&1 && mahdi_deps_failed "$pkg"; then
    return 1
  fi

  case "$lang" in
    python)
      label="pip install $pkg"
      if declare -f mahdi_pip_run >/dev/null 2>&1; then
        # المثبّت الذكي يرسم النسبة الحقيقية بنفسه
        local rc=0
        mahdi_pip_run "$label" "$pkg" || rc=$?
        # ٢ = لا يوجد اتصال (طُبعت الرسالة العربية من المثبّت) — لا نكرّرها
        [ "$rc" -eq 2 ] && return 2
        return "$rc"
      fi
      mahdi_bar_start "$label"
      python3 -m pip install --upgrade --prefer-binary --disable-pip-version-check \
        --quiet --no-input --retries 3 --timeout 30 "$pkg" 2>/dev/null
      local rc=$?
      if [ "$rc" -eq 0 ]; then
        mahdi_bar_done "$pkg ✓"
      else
        mahdi_bar_abort "تعذّر تثبيت $pkg"
      fi
      return "$rc"
      ;;

    node)
      label="npm install $pkg"
      mahdi_bar_start "$label"
      # 8.0.0: البطاقة وحدها تعرض التقدّم — نبدأ عملية ونُشغّل النبضة
      if declare -f mahdi_prog_start >/dev/null 2>&1; then
        mahdi_prog_start "$pkg" "تنزيل الحزمة…"
        mahdi_prog 8 "تنزيل الحزمة من npm" "$pkg"
        mahdi_prog_creep_start 94
      fi
      if command -v npm >/dev/null 2>&1; then
        npm install --no-fund --no-audit --loglevel=error "$pkg" >/dev/null 2>&1
        local rc=$?
        if [ "$rc" -ne 0 ]; then
          npm install -g --no-fund --no-audit --loglevel=error "$pkg" >/dev/null 2>&1
          rc=$?
        fi
        declare -f mahdi_prog_creep_stop >/dev/null 2>&1 && mahdi_prog_creep_stop
        if [ "$rc" -eq 0 ]; then
          mahdi_bar_done "$pkg ✓"
          declare -f mahdi_prog_done >/dev/null 2>&1 && mahdi_prog_done "تم تثبيت الحزمة" "$pkg"
        else
          mahdi_bar_abort
          declare -f mahdi_prog_fail >/dev/null 2>&1 && mahdi_prog_fail "تعذّر تثبيت المكتبة" "$pkg"
        fi
        return "$rc"
      fi
      declare -f mahdi_prog_creep_stop >/dev/null 2>&1 && mahdi_prog_creep_stop
      mahdi_bar_abort
      return 1
      ;;

    php)
      label="composer require $pkg"
      if ! command -v composer >/dev/null 2>&1; then
        return 1
      fi
      mahdi_bar_start "$label"
      if declare -f mahdi_prog_start >/dev/null 2>&1; then
        mahdi_prog_start "$pkg" "تنزيل الحزمة…"
        mahdi_prog 8 "تنزيل الحزمة من Composer" "$pkg"
        mahdi_prog_creep_start 94
      fi
      COMPOSER_ALLOW_SUPERUSER=1 composer require --no-interaction --quiet "$pkg" >/dev/null 2>&1
      local rc=$?
      declare -f mahdi_prog_creep_stop >/dev/null 2>&1 && mahdi_prog_creep_stop
      if [ "$rc" -eq 0 ]; then
        mahdi_bar_done "$pkg ✓"
        declare -f mahdi_prog_done >/dev/null 2>&1 && mahdi_prog_done "تم تثبيت الحزمة" "$pkg"
      else
        mahdi_bar_abort
        declare -f mahdi_prog_fail >/dev/null 2>&1 && mahdi_prog_fail "تعذّر تثبيت المكتبة" "$pkg"
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

  local max_attempts=2
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

    if [ "$rc" -eq 0 ]; then
      rm -f "$errfile" 2>/dev/null || true
      return 0
    fi
    [ "$MAHDI_HEAL" = "0" ] && { mahdi_heal_report "$lang" "$errfile" "$rc"; break; }
    [ "$MAHDI_AUTO_DEPS" = "0" ] && { mahdi_heal_report "$lang" "$errfile" "$rc"; break; }

    local pkgs
    pkgs="$(mahdi_heal_detect "$lang" "$errfile" "$script" || true)"

    # لا مكتبة ناقصة ⇒ الخطأ من السكربت نفسه: نعرضه ونقف.
    if [ -z "$pkgs" ]; then
      mahdi_heal_report "$lang" "$errfile" "$rc"
      break
    fi

    # كل الحزم المكتشفة سبق أن فشل تثبيتها ⇒ لا محاولة رابعة، نعرض السبب الحقيقي.
    local all_failed=1 pkg
    while IFS= read -r pkg; do
      [ -z "$pkg" ] && continue
      if declare -f mahdi_deps_failed >/dev/null 2>&1 && mahdi_deps_failed "$pkg"; then
        continue
      fi
      all_failed=0
    done <<<"$pkgs"
    if [ "$all_failed" -eq 1 ]; then
      mahdi_heal_report "$lang" "$errfile" "$rc"
      break
    fi

    printf "${M_GOLD}⟳ مكتبة ناقصة — جاري تثبيتها: %s${M_RESET}\n" "$(printf '%s' "$pkgs" | tr '\n' ' ')"

    local failed=0
    while IFS= read -r pkg; do
      [ -z "$pkg" ] && continue
      # لا نُعيد حزمة سُجّل فشلها قبل قليل
      if declare -f mahdi_deps_failed >/dev/null 2>&1 && mahdi_deps_failed "$pkg"; then
        continue
      fi
      local install_rc=0
      mahdi_heal_install "$lang" "$pkg" || install_rc=$?
      if [ "$install_rc" -ne 0 ]; then
        declare -f mahdi_deps_mark_failed >/dev/null 2>&1 && mahdi_deps_mark_failed "$pkg"
        if [ "$install_rc" -eq 2 ]; then
          printf "${M_GOLD}⚠ لا يوجد اتصال بالإنترنت — تعذّر تجهيز المكتبة.${M_RESET}\n" >&2
        else
          printf "${M_CORAL}✖ تعذّر تثبيت %s${M_RESET}\n" "$pkg" >&2
        fi
        failed=1
        break
      fi
    done <<<"$pkgs"

    if [ "$failed" -eq 1 ]; then
      # 8.3.0: كان يكتفي بسطر «تحقّق من الاتصال» ويُخفي خطأ السكربت الأصلي.
      mahdi_heal_report "$lang" "$errfile" "$rc"
      break
    fi

    attempt=$((attempt + 1))
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
