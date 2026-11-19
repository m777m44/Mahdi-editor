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

# ——————————————————————————————————————————————————————————————————————————————
# 9.3.1 — «المكتبة ”core“ غير مثبّتة»: الوحدة المحلية ليست حزمة ناقصة.
#
# اللقطة أظهرت سكربتًا يفشل بـ `No module named 'core'`، فبدأ محرّك الإصلاح
# يثبّت `core` من الشبكة (وهي وحدة **داخل المشروع**) ثم قال «أعد التشغيل بعد
# وصل الإنترنت». الآن نعرف الوحدات المحلية من مساعد بايثون، ولا نلمس pip إطلاقًا.
# ——————————————————————————————————————————————————————————————————————————————
mahdi_heal_is_local() {
  local module="$1" script="$2"
  [ -z "$module" ] && return 1
  case "$module" in
    */* | *.py) return 1 ;;
  esac
  # ١) ملف أو مجلد بنفس الاسم بجانب السكربت أو فوقه
  local dir
  dir="$(dirname "$script" 2>/dev/null)"
  local probe="$dir"
  local i=0
  while [ -n "$probe" ] && [ "$i" -lt 3 ]; do
    if [ -e "$probe/$module.py" ] || [ -d "$probe/$module" ]; then
      return 0
    fi
    probe="$(dirname "$probe" 2>/dev/null)"
    [ "$probe" = "/" ] && break
    i=$((i + 1))
  done
  # ٢) أسماء شائعة في كل مشروع (core · utils · config · models …)
  case "$module" in
    core | utils | util | helpers | helper | config | configs | settings | models | model | \
      views | controllers | controller | routes | router | routers | handlers | handler | \
      services | service | api | apis | app | main | run | bot | client | server | \
      database | db | dao | schemas | schema | serializers | forms | admin | auth | lib | \
      libs | src | source | sources | tests | test | scripts | static | templates | \
      migrations | plugins | plugin | modules | module | components | component | engine | \
      engines | agents | agent | tools | tool | crawler | scraper | scrapers | workers | \
      worker | tasks | task | jobs | job | pipelines | pipeline | storage | cache | logger | \
      logging | exceptions | errors | constants | enums | types | shared | common | \
      middleware | security | session | sessions | user | users | data | dataset | datasets | \
      train | training | inference | predict)
      return 0
      ;;
  esac
  return 1
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
  local lang="$1" errfile="$2" rc="${3:-1}" script_path="${4:-${script:-}}"
  local mod="" line="" tone="" title="" hint=""

  # 9.2.0: تصفير سياق الصياغة — لا تتسرّب بيانات خطأ سابق إلى الخطأ الحالي
  MAHDI_SYN_LINE="" MAHDI_SYN_SOURCE="" MAHDI_SYN_CARET="" MAHDI_SYN_FILE=""
  # 9.5.1: إجراء مقترح يخصّ هذا الخطأ بعينه (يطبعه سطر النتيجة النهائي)
  MAHDI_FAIL_ACTION=""

  if [ -s "$errfile" ]; then
    mod="$(mahdi_heal_missing_module "$errfile")"
    line="$(grep -E '^[A-Za-z_][A-Za-z0-9_.]*(Error|Exception|Interrupt):' "$errfile" | tail -n 1)"
  fi

  if [ -n "$mod" ] && mahdi_heal_is_local "$mod" "$script_path"; then
    # 9.3.1: وحدة محلية — لا علاقة لها بالإنترنت ولا بـ pip
    tone="$M_GOLD"
    title="وحدة محلية مفقودة: $mod"
    hint="مجلد «$mod» ليس بجانب الملف المشغَّل — شغّله من داخل مجلده، أو جهّز المشروع: mh install ."
    MAHDI_FAIL_REASON="وحدة محلية مفقودة: $mod"
    mod=""
    # لا نطبع سطر بايثون الإنجليزي أدناه: اسم الوحدة واضح أعلاه،
    # وتكراره بالإنجليزية يجعل الرسالة تبدو كخطأين.
    line=""
  elif [ -n "$mod" ]; then
    # 9.5.1: سببان مختلفان تمامًا، فلا تصلح لهما رسالة واحدة:
    #   • الشبكة تعمل ولا حزمة بهذا الاسم ⇒ المشكلة في الاسم (إجراء: صحّحه)
    #   • الشبكة متعذّرة ⇒ المشكلة في الاتصال (إجراء: لا شيء — يتم تلقائيًا)
    local _net_known=0 _net_ok=0
    if declare -f mahdi_deps_net_ok >/dev/null 2>&1; then
      _net_known=1
      mahdi_deps_net_ok && _net_ok=1
    fi
    if [ "$_net_known" = "1" ] && [ "$_net_ok" = "1" ]; then
      tone="$M_GOLD"
      title="لا توجد حزمة باسم «$mod»"
      hint=""
      MAHDI_FAIL_REASON="اسم مكتبة غير معروف: $mod"
      MAHDI_FAIL_ACTION="صحّح اسم «$mod» في أعلى الملف ثم شغّل ▶"
    elif [ "$_net_known" = "1" ]; then
      tone="$M_GOLD"
      title="تعذّر تجهيز المكتبة «$mod»"
      hint=""
      MAHDI_FAIL_REASON="تعذّر تجهيز مكتبة: $mod"
      MAHDI_FAIL_ACTION="سيكتمل تجهيز «$mod» تلقائيًا عند عودة الشبكة"
    else
      # لا نعرف حالة الشبكة (محرّك الاعتماديات غير محمَّل) ⇒ لا ندّعي شيئًا
      tone="$M_GOLD"
      title="تعذّر تجهيز المكتبة «$mod»"
      hint=""
      MAHDI_FAIL_REASON="تعذّر تجهيز مكتبة: $mod"
      MAHDI_FAIL_ACTION="يتكرّر تجهيز «$mod» تلقائيًا عند التشغيل القادم"
    fi
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
        hint=""
        MAHDI_FAIL_REASON="مكتبة ناقصة"
        MAHDI_FAIL_ACTION="سيكتمل تجهيز المكتبة تلقائيًا عند عودة الشبكة" ;;
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

  # 9.3.1: التشخيص يبدأ بعلامة هادئة (⚠) — العلامة ✖ محفوظة لسطر النتيجة
  # النهائي في نهاية التشغيل، فلا يظهر خطأان متتاليان للسبب نفسه.
  printf "%b⚠  %s%b\n" "$tone" "$title" "$M_RESET" >&2
  # تعليم أن التشخيص المفصّل طُبع — نهايةُ التشغيل لا تُكرّره
  MAHDI_REPORTED=1
  export MAHDI_REPORTED

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

# ——————————————————————————————————————————————————————————————————————————————
# 9.5.1 — هل صار الاستيراد ممكنًا فعلًا؟
#
# «نجح pip» ليست إجابة كافية: حزمة باسم قريب قد تُثبَّت ولا تُوفّر الوحدة
# المطلوبة (‏`pip install telegram` ≠ pyTelegramBotAPI). لذلك نتحقّق بالاستيراد
# الحقيقي قبل إعادة تشغيل السكربت، فلا تُعاد المحاولة على خطأ لم يُصلح.
# ——————————————————————————————————————————————————————————————————————————————
mahdi_py_module_ok() {
  local module="${1:-}"
  [ -n "$module" ] || return 1
  "${MAHDI_PY:-python3}" - "$module" >/dev/null 2>&1 <<'PYMOD'
import importlib
import sys

name = sys.argv[1]
sys.path.insert(0, "")
try:
    importlib.import_module(name)
except Exception:
    sys.exit(1)
PYMOD
}

# حزم PyPI المرشّحة لوحدة بايثون ناقصة (الأرجح أولًا) — تُطبع سطرًا لكل حزمة.
# تُستدعى باسم **الوحدة** (‏telegram) لا باسم الحزمة، لأن الجدول وحدة ← حزمة.
mahdi_heal_candidates() {
  local module="${1:-}"
  [ -n "$module" ] || return 1
  local helper
  helper="$(mahdi_heal_helper)"
  [ -n "$helper" ] && [ -f "$helper" ] || return 1
  command -v "${MAHDI_PY:-python3}" >/dev/null 2>&1 || return 1
  local out
  out="$("${MAHDI_PY:-python3}" "$helper" --candidates "$module" 2>/dev/null || true)"
  [ -n "$out" ] || return 1
  printf '%s\n' "$out"
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
#
# $3 (اختياري) = اسم الوحدة المفقودة كما وردت في الخطأ. وجوده يفعّل التحقّق
# الحقيقي بالاستيراد، وتجربة الأسماء البديلة للحزمة (`telegram` ← pyTelegramBotAPI
# أو python-telegram-bot) حتى ينجح الاستيراد فعلًا.
mahdi_heal_install() {
  local lang="$1" pkg="$2" module="${3:-}"
  local label

  case "$lang" in
    python)
      label="pip install $pkg"
      # 9.5.1: لم يعد «فشل سابق» يمنع المحاولة. كان الفشل الوهمي (فحص شبكة
      # متشائم) يُسجَّل دائمًا فيمنع الإصلاح الحقيقي، فينتهي السكربت ناقصًا.
      local cand rc=0
      local -a candidates=("$pkg")
      # أسماء بديلة: الوحدة قد تأتي من حزمة أخرى (‏telegram ← pyTelegramBotAPI)
      while IFS= read -r cand; do
        [ -n "$cand" ] || continue
        case " ${candidates[*]} " in
          *" $cand "*) continue ;;
        esac
        candidates+=("$cand")
      done < <(mahdi_heal_candidates "$module" 2>/dev/null || true)

      for cand in "${candidates[@]}"; do
        rc=0
        if declare -f mahdi_py_install_any >/dev/null 2>&1; then
          mahdi_py_install_any "$cand" "$module" || rc=$?
        elif declare -f mahdi_pip_run >/dev/null 2>&1; then
          mahdi_pip_run "pip install $cand" "$cand" || rc=$?
        fi
        # لا اتصال بعد كل المصادر ⇒ لا فائدة من اسم حزمة آخر
        [ "$rc" -eq 2 ] && return 2
        # نجاح حقيقي = الاستيراد صار يعمل (أو لا وحدة للتحقّق منها)
        if [ -z "$module" ] || mahdi_py_module_ok "$module"; then
          return 0
        fi
        # الحزمة ثُبّتت لكن الوحدة ما زالت ناقصة ⇒ نجرّب الاسم البديل التالي
      done
      return 1
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
    [ "$MAHDI_HEAL" = "0" ] && { mahdi_heal_report "$lang" "$errfile" "$rc" "$script"; break; }
    [ "$MAHDI_AUTO_DEPS" = "0" ] && { mahdi_heal_report "$lang" "$errfile" "$rc" "$script"; break; }

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
      mahdi_heal_report "$lang" "$errfile" "$rc" "$script"
      break
    fi

    # 9.5.1 — كان هنا «كل الحزم سبق أن فشلت ⇒ نتوقّف». ذلك الشرط هو ما جعل
    # السلوك المبلَّغ عنه: فشل وهمي واحد (فحص شبكة متشائم) يمنع أي محاولة
    # تثبيت حقيقية، مع أن الشبكة تعمل. الشرط الوحيد المشروع للتوقّف الآن هو
    # انقطاع مؤكّد: بلا شبكة يستحيل التثبيت، وبها نجرّب كل الطرق والمرايا.
    if declare -f mahdi_deps_net_ok >/dev/null 2>&1 && ! mahdi_deps_net_ok; then
      mahdi_heal_report "$lang" "$errfile" "$rc" "$script"
      break
    fi

    # 9.5.1: سطر واحد هادئ يشرح ما يجري، ثم تأكيد أن الإعادة تلقائية —
    # فلا يظنّ المستخدم أن عليه إعادة التشغيل بنفسه.
    printf "${M_SKY}⚙${M_RESET}  ${M_BOLD}تجهيز المكتبة الناقصة${M_RESET}  ${M_MUTED}·${M_RESET}  ${M_GOLD}%s${M_RESET}\n" "$(printf '%s' "$pkgs" | tr '\n' ' ')"

    # 9.5.1 — الوحدة التي شكا منها المفسّر: تُستخدم للتحقّق من نجاح التثبيت.
    local mod_now
    mod_now="$(mahdi_heal_missing_module "$errfile" 2>/dev/null || true)"

    # 9.5.1 — «فشل سابق» لا يعني فشلًا حقيقيًا. نمسح العلامة هنا كي يُعاد
    # التثبيت بكل الطرق والمرايا، فلا يبقى السكربت ناقصًا بلا محاولة.
    if declare -f mahdi_deps_clear >/dev/null 2>&1; then
      mahdi_deps_clear "$mod_now"
      while IFS= read -r pkg; do
        [ -n "$pkg" ] && mahdi_deps_clear "$pkg"
      done <<<"$pkgs"
    fi

    local failed=0
    while IFS= read -r pkg; do
      [ -z "$pkg" ] && continue
      local install_rc=0
      mahdi_heal_install "$lang" "$pkg" "$mod_now" || install_rc=$?
      if [ "$install_rc" -ne 0 ]; then
        declare -f mahdi_deps_mark_failed >/dev/null 2>&1 && mahdi_deps_mark_failed "$pkg"
        # 9.5.1: لا نطلب من المستخدم إعادة التشغيل — نُسجّلها لتُجهَّز تلقائيًا
        # في أول تشغيل تالٍ بعد عودة الشبكة.
        declare -f mahdi_pending_add >/dev/null 2>&1 && mahdi_pending_add "$pkg"
        # 9.5.1: لا سطر ثانٍ هنا. السبب يُعرض مرة واحدة في تقرير الخطأ
        # التالي (⚠) ثم في سطر النتيجة (✖) — لا تكرار ولا علامتان لسبب واحد.
        failed=1
        break
      fi
    done <<<"$pkgs"

    if [ "$failed" -eq 1 ]; then
      # 8.3.0: كان يكتفي بسطر «تحقّق من الاتصال» ويُخفي خطأ السكربت الأصلي.
      mahdi_heal_report "$lang" "$errfile" "$rc" "$script"
      break
    fi

    if [ "$failed" -eq 0 ] && [ "$attempt" -lt "$max_attempts" ]; then
      printf "${M_MINT}✔${M_RESET}  ${M_MUTED}جُهِّزت — يُعاد تشغيل السكربت تلقائيًا${M_RESET}\n" || true
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
