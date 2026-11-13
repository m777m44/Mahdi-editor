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

# 9.3.0: سلاسل التثبيت البديلة (نفس المحرّك المستخدم في auto_deps وinit)
if [ -f "${LOCAL:-/home}/bin/rescue" ]; then
  # shellcheck disable=SC1091
  . "${LOCAL:-/home}/bin/rescue"
fi

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

# ——————————————————————————————————————————————————————————————————————————————
# 8.4.0 — تقرير أخطاء ملوّن ومنظّم
#
# قبلها كان أي خطأ يُعرض كسطر أحمر واحد «توقّف السكربت بخطأ:» ثم آخر سطر
# خام، بلا تصنيف ولا تلميح. الآن لكل نوع خطأ لونٌ وعنوانٌ عربي وإجراءٌ واحد
# واضح: مكتبة قديمة، صياغة، ملف مفقود، صلاحية، شبكة، إلخ.
# MAHDI_TRACE=1 يعرض أثر بايثون كاملًا بعد منقي الضجيج (للمطوّرين).
# ——————————————————————————————————————————————————————————————————————————————
# ——————————————————————————————————————————————————————————————————————————————
# 9.2.0 — سياق خطأ الصياغة: السطر المذنب نفسه، لا اسم الخطأ وحده.
#
# كانت اللقطة تُظهر:
#     ✖ خطأ في صياغة الملف
#     SyntaxError: invalid decimal literal
#     ↳ خطأ في الصياغة
# ولا سطر ولا رقم — فيبقى المستخدم يفتح الملف ويبحث بعينه عن الخطأ. بايثون
# يُرسل السياق كاملًا في stderr:
#       File "x.py", line 3
#         x = 09abc
#             ^
# نستخرجه هنا (رقم السطر + نصّ السطر + مؤشّر ^) ونعرضه عربيًّا مرتّبًا.
# يضع النتيجة في MAHDI_SYN_LINE / _SOURCE / _CARET / _FILE ويعيد 0 عند النجاح.
# ——————————————————————————————————————————————————————————————————————————————
mahdi_heal_syntax_ctx() {
  local errfile="$1"
  MAHDI_SYN_LINE="" MAHDI_SYN_SOURCE="" MAHDI_SYN_CARET="" MAHDI_SYN_FILE=""
  [ -s "$errfile" ] || return 1

  local parsed
  parsed="$(
    awk '
      /^[[:space:]]*File ".*", (physical )?line [0-9]+/ {
        loc = NR
        line = $0
        sub(/.*, (physical )?line /, "", line)
        sub(/[^0-9].*$/, "", line)
        if (match($0, /File "[^"]*"/)) {
          name = substr($0, RSTART + 6, RLENGTH - 7)
        }
      }
      { rows[NR] = $0 }
      END {
        if (loc == 0) exit
        i = loc + 1
        while (i <= NR && rows[i] ~ /^[[:space:]]*$/) i++
        src = rows[i]
        i++
        while (i <= NR && rows[i] ~ /^[[:space:]]*$/) i++
        car = rows[i]
        # بايثون يُزيح السطر والمؤشّر بأربع مسافات لكلّيهما؛ نُزيلها من الاثنين
        # معًا فيبقى المؤشّر محاذيًا تمامًا للحرف المقصود.
        sub(/^    /, "", src)
        sub(/^    /, "", car)
        printf "%s\t%s\t%s\t%s", line, name, src, car
      }
    ' "$errfile" 2>/dev/null
  )"
  [ -n "$parsed" ] || return 1

  local _line _name _src _car
  IFS=$'\t' read -r _line _name _src _car <<<"$parsed" || true
  [ -n "$_line" ] || return 1

  MAHDI_SYN_LINE="$_line"
  MAHDI_SYN_FILE="${_name##*/}"
  MAHDI_SYN_SOURCE="$_src"
  # المؤشّر ^ يُقصّ إلى بداية السطر كي يبقى محاذيًا للحرف المقصود
  MAHDI_SYN_CARET="$(printf '%s' "$_car" | sed -e 's/[^[:space:]^].*$//' -e 's/^[[:space:]]\{0,2\}//')"
  return 0
}

mahdi_heal_report() {
  local lang="$1" errfile="$2" rc="${3:-1}"
  local mod="" line="" tone="" title="" hint=""

  # 9.2.0: تصفير سياق الصياغة — لا تتسرّب بيانات خطأ سابق إلى الخطأ الحالي
  MAHDI_SYN_LINE="" MAHDI_SYN_SOURCE="" MAHDI_SYN_CARET="" MAHDI_SYN_FILE=""

  if [ -s "$errfile" ]; then
    mod="$(mahdi_heal_missing_module "$errfile")"
    line="$(grep -E '^[A-Za-z_][A-Za-z0-9_.]*(Error|Exception|Interrupt):' "$errfile" | tail -n 1)"
  fi

  if [ -n "$mod" ]; then
    tone="$M_CORAL"
    title="المكتبة «$mod» غير مثبّتة"
    hint="سيُعاد التثبيت تلقائيًا بطرق أخرى — أعد التشغيل بعد وصل الإنترنت"
    MAHDI_FAIL_REASON="مكتبة ناقصة: $mod"
  else
    case "$line" in
      AttributeError:*)
        tone="$M_VIOLET"; title="مكتبة قديمة أو ملف يحجبها"
        hint="حدّث المكتبة، أو أعد تسمية أي ملف بنفس اسمها بجانب السكربت"
        MAHDI_FAIL_REASON="مكتبة غير متوافقة" ;;
      SyntaxError:*|IndentationError:*|TabError:*)
        tone="$M_GOLD"; title="خطأ في صياغة الملف"
        if mahdi_heal_syntax_ctx "$errfile"; then
          hint="صحّح السطر $MAHDI_SYN_LINE ثم أعد التشغيل (الملف مفتوح في المحرّر)"
          MAHDI_FAIL_REASON="خطأ في الصياغة — السطر $MAHDI_SYN_LINE"
        else
          hint="افتح الملف وصحّح السطر المشار إليه"
          MAHDI_FAIL_REASON="خطأ في الصياغة"
        fi ;;
      FileNotFoundError:*|IsADirectoryError:*|NotADirectoryError:*)
        tone="$M_GOLD"; title="ملف أو مجلد مفقود"
        hint="تحقّق من المسار ومن وجود الملفات بجانب السكربت"
        MAHDI_FAIL_REASON="ملف مفقود" ;;
      PermissionError:*)
        tone="$M_CORAL"; title="لا توجد صلاحية للوصول"
        hint="امنح التطبيق صلاحية «كل الملفات» من الإعدادات"
        MAHDI_FAIL_REASON="صلاحية مرفوضة" ;;
      *ConnectionError:*|*TimeoutError:*|*ConnectionRefusedError:*|*gaierror:*)
        tone="$M_GOLD"; title="تعذّر الاتصال بالشبكة"
        hint="تحقّق من الإنترنت ثم أعد التشغيل"
        MAHDI_FAIL_REASON="اتصال بالشبكة" ;;
      KeyboardInterrupt:*)
        tone="$M_MUTED"; title="أوقفت التشغيل بنفسك"
        hint="لا شيء لإصلاحه"
        MAHDI_FAIL_REASON="إيقاف من المستخدم" ;;
      ModuleNotFoundError:*|ImportError:*)
        tone="$M_CORAL"; title="مكتبة ناقصة"
        hint="سيُعاد التثبيت تلقائيًا بطرق أخرى — أعد التشغيل بعد وصل الإنترنت"
        MAHDI_FAIL_REASON="مكتبة ناقصة" ;;
      TypeError:*|ValueError:*|KeyError:*|NameError:*|IndexError:*|ZeroDivisionError:*|RuntimeError:*|OSError:*|Exception:*)
        tone="$M_VIOLET"; title="خطأ في السكربت"
        hint="راجع الرسالة أعلاه والسطر المشار إليه في الملف"
        MAHDI_FAIL_REASON="خطأ في السكربت" ;;
      *)
        if [ -z "$line" ]; then
          tone="$M_MUTED"; title="أنهاه السكربت بالرمز $rc دون رسالة"
          hint="افتح الملف وتحقّق من أوامره"
          MAHDI_FAIL_REASON="أنهاه السكربت بالرمز $rc"
        else
          tone="$M_VIOLET"; title="توقّف السكربت"
          hint="راجع الرسالة أدناه"
          MAHDI_FAIL_REASON="خطأ في السكربت"
        fi ;;
    esac
  fi

  printf "%b✖  %s%b\n" "$tone" "$title" "$M_RESET" >&2

  # 9.2.0: خطأ الصياغة يُعرض بسطر الملف ومؤشّره، لا باسم الخطأ الإنجليزي وحده.
  if [ -n "${MAHDI_SYN_SOURCE:-}" ]; then
    local _fsi _pdi
    # FSI/PDI بالبايتات (U+2068 / U+2069): $'\u…' لا يُفسَّر في كل بناءات bash
    _fsi="$(printf '\342\201\250')"
    _pdi="$(printf '\342\201\251')"
    # كل معلومة في سطرها: لا خلط عربي/لاتيني في سطر واحد (اتجاه واحد لكل سطر)
    printf "${M_SKY}  %s%s%s${M_RESET}\n" "$_fsi" "${MAHDI_SYN_FILE:-}" "$_pdi" >&2
    printf "${M_GOLD}  السطر %s${M_RESET}\n" "$MAHDI_SYN_LINE" >&2
    printf "${M_WHITE}  %s${M_RESET}\n" "$MAHDI_SYN_SOURCE" >&2
    [ -n "${MAHDI_SYN_CARET:-}" ] &&
      printf "${M_CORAL}  %s${M_RESET}\n" "$MAHDI_SYN_CARET" >&2
  elif [ -n "$line" ] && [ -z "$mod" ]; then
    printf "  ${M_WHITE}%s${M_RESET}\n" "$line" >&2
  fi
  if [ -n "$hint" ]; then
    printf "${M_MUTED}  %s${M_RESET}\n" "$hint" >&2
  fi

  # سطور إضافية غير مصنّفة (أخطاء المفسّرات الأخرى) بعد تنقية ضجيج pip/npm
  if [ -z "$mod" ] && [ -s "$errfile" ]; then
    # سطور إضافية للغات الأخرى فقط: أثر بايثون فيه أسطر كود وإشارات ^^^^^
    # لا تضيف معلومة بعد أن عرضنا آخر سطر خطأ وتصنيفه.
    if [ "$lang" != "python" ] && [ -n "$line" ]; then
      local extra
      extra="$(mahdi_heal_strip_noise "$errfile" | grep -vF "$line" | tail -n 3)"
      [ -n "$extra" ] && printf "${M_MUTED}%s${M_RESET}\n" "$extra" >&2
    fi
    if [ "${MAHDI_TRACE:-0}" = "1" ]; then
      printf "${M_MUTED}  ── أثر التنفيذ ──${M_RESET}\n" >&2
      mahdi_heal_strip_noise "$errfile" | tail -n 24 >&2
    fi
  fi
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
  command -v "${MAHDI_PY:-python3}" >/dev/null 2>&1 || return 1
  "${MAHDI_PY:-python3}" "$helper" --lang "$lang" --stderr "$errfile" --file "$script" 2>/dev/null
}

# 8.4.0: مكتبة موجودة لكنها قديمة/خاطئة — أوامر الإصلاح لا أسماء حزم
mahdi_heal_detect_broken() {
  local lang="$1" errfile="$2" script="$3"
  [ "$lang" = "python" ] || return 1
  local helper
  helper="$(mahdi_heal_helper)"
  [ -n "$helper" ] && [ -f "$helper" ] || return 1
  "${MAHDI_PY:-python3}" "$helper" --broken --lang python --stderr "$errfile" --file "$script" 2>/dev/null
}

# ينفّذ أوامر remove:/upgrade:/shadow: ويعيد 0 فقط إن أُنجز إصلاح حقيقي
mahdi_heal_repair() {
  local actions="$1" action did=0
  while IFS= read -r action; do
    [ -z "$action" ] && continue
    case "$action" in
      shadow:*)
        printf "${M_GOLD}⚠  الملف «%s» بجانب السكربت يحجب المكتبة الحقيقية — أعد تسميته.${M_RESET}\n" "${action#shadow:}" >&2
        ;;
      remove:*)
        local bare="${action#remove:}"
        if "${MAHDI_PY:-python3}" -m pip uninstall -y "$bare" >/dev/null 2>&1; then
          did=1
        fi
        ;;
      upgrade:*)
        local dist="${action#upgrade:}"
        if [ "${MAHDI_OFFLINE:-0}" = "1" ]; then
          if [ "${MAHDI_UPGRADE_OFFLINE_SHOWN:-0}" != "1" ]; then
            printf "${M_GOLD}⚠  لا يوجد اتصال — لا يمكن تحديث المكتبة.${M_RESET}\n" >&2
            MAHDI_UPGRADE_OFFLINE_SHOWN=1
          fi
          declare -f mahdi_deps_mark_failed >/dev/null 2>&1 && mahdi_deps_mark_failed "upgrade:$dist"
          return 2
        fi
        if declare -f mahdi_deps_failed >/dev/null 2>&1 && mahdi_deps_failed "upgrade:$dist"; then
          continue
        fi
        printf "${M_SKY}↻  تحديث %s …${M_RESET}\n" "$dist" >&2
        local rc=0
        "${MAHDI_PY:-python3}" -m pip install --upgrade --prefer-binary --no-input \
          --quiet --disable-pip-version-check --retries 2 --timeout 20 "$dist" >/dev/null 2>&1 || rc=$?
        if [ "$rc" -eq 0 ]; then
          printf "${M_MINT}✔  جُدِّث %s${M_RESET}\n" "$dist" >&2
          did=1
        else
          declare -f mahdi_deps_mark_failed >/dev/null 2>&1 && mahdi_deps_mark_failed "upgrade:$dist"
          printf "${M_CORAL}✖  تعذّر تحديث %s${M_RESET}\n" "$dist" >&2
        fi
        ;;
    esac
  done <<<"$actions"
  [ "$did" -eq 1 ] && return 0
  return 1
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
      # 9.3.0: السلسلة الكاملة أولًا (ويل ← إصدارات أقدم ← بناء ← حزمة نظام ← مصدر بديل)
      if declare -f mahdi_py_install_any >/dev/null 2>&1; then
        mahdi_py_install_any "$pkg" && return 0
        return 1
      fi
      if declare -f mahdi_pip_run >/dev/null 2>&1; then
        # المثبّت الذكي يرسم النسبة الحقيقية بنفسه
        local rc=0
        mahdi_pip_run "$label" "$pkg" || rc=$?
        # ٢ = لا يوجد اتصال (طُبعت الرسالة العربية من المثبّت) — لا نكرّرها
        [ "$rc" -eq 2 ] && return 2
        return "$rc"
      fi
      mahdi_bar_start "$label"
      "${MAHDI_PY:-python3}" -m pip install --upgrade --prefer-binary --disable-pip-version-check \
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
      # 9.3.0: node/npm يُثبَّتان عند الحاجة، ثم أربع محاولات لكل حزمة
      if declare -f mahdi_npm_install_any >/dev/null 2>&1; then
        mahdi_npm_install_any "$pkg" "${PWD:-/}" >/dev/null 2>&1 && return 0
      fi
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

  local max_attempts=3
  local attempt=1
  local repaired=0
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

    # ————————————————————— 9.3.0: سبب «الانهيار» الأشهر عند تشغيل مشاريع GitHub —————
    # سكربت يستدعي أمرًا غير موجود (npm · node · git · curl · ffmpeg …) كان يفشل
    # فورًا بلا أي محاولة تثبيت، ويُعرض كأن المشروع «منهار».
    local missing_cmd=""
    # (الصيغ الثلاث: «npm: command not found» · «bash: line 3: npm: command not found»
    #  · «sh: 1: npm: not found») — نأخذ الكلمة الأخيرة قبل العبارة نفسها.
    missing_cmd="$(grep -oE '[A-Za-z0-9_.+-]+: (command )?not found' "$errfile" 2>/dev/null |
      head -n 1 | sed 's/:.*//')"
    if [ -n "$missing_cmd" ] && declare -f mahdi_install_command >/dev/null 2>&1; then
      printf "${M_SKY}↻  الأمر «%s» غير مثبَّت — تجهيزه ثم إعادة التشغيل…${M_RESET}\n" "$missing_cmd"
      if mahdi_install_command "$missing_cmd" >/dev/null 2>&1; then
        hash -r 2>/dev/null || true
        attempt=$((attempt + 1))
        continue
      fi
    fi

    # صلاحية التنفيذ: مشاريع GitHub تأتي غالبًا بلا +x بعد فكّ الضغط
    if grep -qiE 'permission denied' "$errfile" 2>/dev/null && [ -f "$script" ]; then
      if [ ! -x "$script" ]; then
        chmod +x "$script" 2>/dev/null || true
        printf "${M_SKY}↻  منح صلاحية التنفيذ ثم إعادة التشغيل…${M_RESET}\n"
        attempt=$((attempt + 1))
        continue
      fi
    fi

    # 8.4.0: مكتبة موجودة لكنها قديمة أو خاطئة (مثل telebot بلا AsyncTeleBot).
    # لا يظهر ModuleNotFoundError، فلا يلتقطها كاشف الحزم — نصلحها هنا مرة واحدة.
    if [ "$repaired" -eq 0 ]; then
      local broken
      broken="$(mahdi_heal_detect_broken "$lang" "$errfile" "$script" || true)"
      if [ -n "$broken" ]; then
        repaired=1
        if mahdi_heal_repair "$broken"; then
          printf "${M_SKY}↻  إعادة التشغيل بعد الإصلاح…${M_RESET}\n"
          attempt=$((attempt + 1))
          continue
        fi
      fi
    fi

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
