# shellcheck shell=bash
# ——————————————————————————————————————————————————————————————————————————————
# اختصارات التشغيل (8.5.0)
#
# الهدف: بدل كتابة `python3 script.py` يكتب المستخدم `py script.py` — بأي شكل من
# الأحرف: py / Py / pY / PY. وكذلك `js` و`ts` و`run` لأي ملفٍ حسب امتداده.
#
# الاختلافات مكتوبة كملفّات تنفيذية حقيقية داخل `$LOCAL/bin` (وهو على PATH داخل
# الـ sandbox) لا كـaliases في bash، فتعمل في كل الأصداف والسكربتات والطرفيات
# الفرعية، لا في الجلسة التفاعلية وحدها.
#
# ——————————————————————————————————————————————————————————————————————————————
# 8.5.0 — «✖ الأمر غير موجود: pip»
#
# السبب: كان المُثبّت يقارن رقم إصدار ثابتًا فيترك كل شيء كما هو، ويكتب `pip`
# **فقط إن لم يكن موجودًا** لحظة التثبيت الأولى. فإذا حُذف pip لاحقًا (أو ثُبّت
# التطبيق قبل وجوده) بقي الاختصار غائبًا إلى الأبد، والعلامة تقول «تمّ».
#
# الآن: كل اختصار يُقارن **بمحتواه** في كل فتح طرفية، ويُعاد كتابته إن غاب أو
# تغيّر أو فقد صلاحية التنفيذ. و`pip` و`pip3` صارا يمرّان دائمًا على مفسّر
# بايثون المختار (أحدث إصدار يملك pip) ويُجهّزان pip تلقائيًا إن غاب.
# ——————————————————————————————————————————————————————————————————————————————

MAHDI_ALIASES_VERSION="8.5.0"

_mahdi_write_alias() {
  local name="$1" body="$2" target="$LOCAL/bin/$1"
  [ -d "$LOCAL/bin" ] || return 1

  # المحتوى نفسه؟ لا نلمس الملف (كي لا يتغيّر طابع التعديل بلا داعٍ)
  if [ -f "$target" ] && [ -x "$target" ] && [ "$(cat "$target" 2>/dev/null)" = "$body" ]; then
    return 0
  fi

  printf '%s\n' "$body" >"$target" 2>/dev/null || return 1
  chmod 755 "$target" 2>/dev/null || true
  return 0
}

# رأس مشترك لكل اختصار: يجد الحاوية فقط — بلا source لأي ملف، فيبقى البدء فوريًا
_mahdi_alias_head() {
  cat <<'HEAD'
#!/bin/bash
# Mahdi-Editor — اختصار تشغيل. أي حالة أحرف مقبولة: py · Py · pY · PY
LOCAL="${LOCAL:-$(cd "$(dirname "$0")/.." && pwd)}"
RUNNER="$LOCAL/bin/universal_runner"
HEAD
}

# اختيار المفسّر بلا تشغيل أي عملية بايثون: التفضيل اليدوي ثم نتيجة الفحص المحفوظة.
# (source utils هنا كان يكلّف ثلث ثانية في كل أمر، وهو ثمن لا يدفعه المستخدم.)
_mahdi_alias_py() {
  cat <<'PYSEL'
HOME="${HOME:-/home}"
PY="${MAHDI_PY:-${MAHDI_PY_PREFERRED:-}}"
[ -z "$PY" ] && [ -r "$HOME/.mahdi/python_preferred" ] && PY="$(head -n 1 "$HOME/.mahdi/python_preferred" 2>/dev/null)"
if [ -z "$PY" ] && [ -r "$HOME/.mahdi/python_scan" ]; then
  read -r _py_ts PY _py_rest <"$HOME/.mahdi/python_scan" 2>/dev/null || PY=""
fi
command -v "${PY:-python3}" >/dev/null 2>&1 || PY=python3
[ -n "$PY" ] || PY=python3
PYSEL
}

mahdi_install_aliases() {
  local force="${1:-}"

  local head pysel py_body js_body ts_body run_body usage_body python_body pip_body

  head="$(_mahdi_alias_head)"
  pysel="$(_mahdi_alias_py)"

  py_body="$head"$'\n'"$pysel"'
case "$1" in
  -h|--help)
    printf "\033[1;96m%s\033[0m\n" "py — تشغيل بايثون باختصار"
    printf "  %s\n" "py script.py        ← تشغيل ملف بايثون"
    printf "  %s\n" "py -c \"print(42)\"  ← تنفيذ سطر مباشر"
    printf "  %s\n" "py                  ← الطرفية التفاعلية"
    printf "  %s\n" "أي شكل: Py · pY · PY"
    exit 0
    ;;
esac
if [ $# -eq 0 ]; then
  exec "$PY"
fi
case "$1" in
  *.py) [ -f "$1" ] && exec bash "$RUNNER" "$@" ;;
esac
exec "$PY" "$@"'

  js_body="$head"'
case "$1" in
  -h|--help)
    printf "\033[1;96m%s\033[0m\n" "js — تشغيل جافاسكربت باختصار"
    printf "  %s\n" "js app.js        ← تشغيل ملف"
    printf "  %s\n" "js -e \"console.log(1)\"  ← تنفيذ سطر"
    printf "  %s\n" "أي شكل: Js · jS · JS"
    exit 0
    ;;
esac
if [ $# -eq 0 ]; then
  exec node
fi
case "$1" in
  *.js|*.mjs|*.cjs) [ -f "$1" ] && exec bash "$RUNNER" "$@" ;;
esac
exec node "$@"'

  ts_body="$head"'
case "$1" in
  -h|--help)
    printf "\033[1;96m%s\033[0m\n" "ts — تشغيل TypeScript باختصار"
    printf "  %s\n" "ts app.ts        ← ترجمة ثم تشغيل"
    printf "  %s\n" "أي شكل: Ts · tS · TS"
    exit 0
    ;;
esac
if [ $# -eq 0 ]; then
  printf "%s\n" "اكتب: ts ملف.ts"
  exit 0
fi
exec bash "$RUNNER" "$@"'

  run_body="$head"'
case "$1" in
  -h|--help|"")
    printf "\033[1;96m%s\033[0m\n" "run — تشغيل أي ملف حسب امتداده"
    printf "  %s\n" "run script.py · run app.js · run main.go · run file.c"
    printf "  %s\n" "يكتشف المفسّر، يثبّت المكتبات الناقصة، ثم يشغّل."
    exit 0
    ;;
esac
exec bash "$RUNNER" "$@"'

  usage_body="$head"'
printf "\033[1;96m%s\033[0m\n" "طرق التشغيل في Mahdi-Editor"
printf "  %s\n" "py script.py    بايثون        (Py · pY · PY)"
printf "  %s\n" "js app.js       جافاسكربت     (Js · jS · JS)"
printf "  %s\n" "ts app.ts       TypeScript    (Ts · tS · TS)"
printf "  %s\n" "run file.any    أي امتداد مدعوم"
printf "  %s\n" "mh              مركز الأدوات: فحص البيئة وإنشاء المشاريع"
printf "  %s\n" "mh doctor       فحص شامل للنظام وبايثون والمخازن"
printf "  %s\n" "mh new py app   إنشاء مشروع جاهز (py · bot · web · api · html · node)"
printf "  %s\n" "pip --version   للتحقّق من مدير الحزم"
printf "\n"
printf "%s\n" "المكتبات الناقصة تُثبَّت تلقائيًا وبصمت، وإن كانت كلها موجودة فلا يظهر أي سطر."
exit 0'

  python_body="$head"$'\n'"$pysel"'exec "$PY" "$@"
'

  # pip يمرّ دائمًا على المفسّر المختار، ويُجهّز نفسه إن غاب — لا «الأمر غير موجود»
  pip_body="$head"$'\n'"$pysel"'
export PIP_CACHE_DIR="${PIP_CACHE_DIR:-$HOME/.mahdi/cache/pip}"
export PIP_DISABLE_PIP_VERSION_CHECK=1
export PIP_ROOT_USER_ACTION=ignore
export PIP_NO_INPUT=1
export PIP_BREAK_SYSTEM_PACKAGES=1
[ -n "${MAHDI_PIP_INDEX:-}" ] && export PIP_INDEX_URL="$MAHDI_PIP_INDEX"
mkdir -p "$PIP_CACHE_DIR" 2>/dev/null || true

if ! "$PY" -m pip --version >/dev/null 2>&1; then
  # يُجهَّز مرة واحدة فقط، وبلا أي سؤال
  if [ -f "$LOCAL/bin/auto_deps" ]; then
    . "$LOCAL/bin/auto_deps" >/dev/null 2>&1
    MAHDI_PY="$PY" mahdi_ensure_pip >/dev/null 2>&1 || true
  fi
fi
exec "$PY" -m pip "$@"'

  _mahdi_write_alias py "$py_body"
  _mahdi_write_alias Py "$py_body"
  _mahdi_write_alias pY "$py_body"
  _mahdi_write_alias PY "$py_body"

  _mahdi_write_alias js "$js_body"
  _mahdi_write_alias Js "$js_body"
  _mahdi_write_alias jS "$js_body"
  _mahdi_write_alias JS "$js_body"

  _mahdi_write_alias ts "$ts_body"
  _mahdi_write_alias Ts "$ts_body"
  _mahdi_write_alias tS "$ts_body"
  _mahdi_write_alias TS "$ts_body"

  _mahdi_write_alias run "$run_body"
  _mahdi_write_alias usage "$usage_body"

  # واجهة `python` كاسم بديل للمفسّر المختار (بعض السكربتات تنادي python)
  if [ ! -x "$LOCAL/bin/python" ] && ! command -v python >/dev/null 2>&1; then
    _mahdi_write_alias python "$python_body"
  fi

  # pip وpip3: يُكتبان دائمًا، لأن أي pip خارجي قد يخصّ إصدارًا آخر بلا المكتبات
  _mahdi_write_alias pip "$pip_body"
  _mahdi_write_alias pip3 "$pip_body"

  if [ "$force" = "--force" ]; then
    hash -r 2>/dev/null || true
  fi
  return 0
}

mahdi_install_aliases
