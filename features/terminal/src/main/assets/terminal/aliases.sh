# shellcheck shell=bash
# ——————————————————————————————————————————————————————————————————————————————
# اختصارات التشغيل (3.1.0)
#
# الهدف: بدل كتابة `python3 script.py` يكتب المستخدم `py script.py` — بأي شكل من
# الأحرف: py / Py / pY / PY. وكذلك `js` و`ts` و`run` لأي ملفٍ حسب امتداده.
#
# الاختلافات مكتوبة كملفّات تنفيذية حقيقية داخل `$LOCAL/bin` (وهو على PATH داخل
# الـ sandbox) لا كـaliases في bash، فتعمل في كل الأصداف والسكربتات والطرفيات
# الفرعية، لا في الجلسة التفاعلية وحدها.
# ——————————————————————————————————————————————————————————————————————————————

MAHDI_ALIASES_VERSION="3.1.0"
MAHDI_ALIASES_MARKER="$LOCAL/.mahdi_aliases_version"

_mahdi_write_alias() {
  local name="$1" body="$2" target="$LOCAL/bin/$1"
  printf '%s\n' "$body" >"$target"
  chmod 755 "$target" 2>/dev/null || true
}

mahdi_install_aliases() {
  if [ "$(cat "$MAHDI_ALIASES_MARKER" 2>/dev/null)" = "$MAHDI_ALIASES_VERSION" ]; then
    return 0
  fi

  local head='#!/bin/bash
# Mahdi-Editor — اختصار تشغيل. أي حالة أحرف مقبولة: py · Py · pY · PY
LOCAL="${LOCAL:-$(cd "$(dirname "$0")/.." && pwd)}"
RUNNER="$LOCAL/bin/universal_runner"'

  local py_body="$head"'
case "$1" in
  -h|--help)
    printf "\033[36m%s\033[0m\n" "py — تشغيل بايثون باختصار"
    printf "  %s\n" "py script.py        ← تشغيل ملف بايثون"
    printf "  %s\n" "py -c \"print(42)\"  ← تنفيذ سطر مباشر"
    printf "  %s\n" "py                  ← الطرفية التفاعلية"
    printf "  %s\n" "أي شكل: Py · pY · PY"
    exit 0
    ;;
esac
if [ $# -eq 0 ]; then
  exec python3
fi
case "$1" in
  *.py) [ -f "$1" ] && exec bash "$RUNNER" "$@" ;;
esac
exec python3 "$@"'

  local js_body="$head"'
case "$1" in
  -h|--help)
    printf "\033[36m%s\033[0m\n" "js — تشغيل جافاسكربت باختصار"
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

  local ts_body="$head"'
case "$1" in
  -h|--help)
    printf "\033[36m%s\033[0m\n" "ts — تشغيل TypeScript باختصار"
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

  local run_body="$head"'
case "$1" in
  -h|--help|"")
    printf "\033[36m%s\033[0m\n" "run — تشغيل أي ملف حسب امتداده"
    printf "  %s\n" "run script.py · run app.js · run main.go · run file.c"
    printf "  %s\n" "يكتشف المفسّر، يثبّت المكتبات الناقصة، ثم يشغّل."
    exit 0
    ;;
esac
exec bash "$RUNNER" "$@"'

  local usage_body="$head"'
printf "\033[36m%s\033[0m\n" "طرق التشغيل في Mahdi-Editor"
printf "  %s\n" "py script.py    بايثون        (Py · pY · PY)"
printf "  %s\n" "js app.js       جافاسكربت     (Js · jS · JS)"
printf "  %s\n" "ts app.ts       TypeScript    (Ts · tS · TS)"
printf "  %s\n" "run file.any    أي امتداد مدعوم"
printf "  %s\n" "python3 --version   للتحقّق من النسخة المثبَّتة"
printf "\n"
printf "%s\n" "المكتبات الناقصة تُثبَّت تلقائيًا وبصمت، وإن كانت كلها موجودة فلا يظهر أي سطر."
exit 0'

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

  # واجهة `python` كاسم بديل لـ python3 (بعض السكربتات تنادي python)
  if ! command -v python >/dev/null 2>&1; then
    _mahdi_write_alias python '#!/bin/bash
exec python3 "$@"'
  fi

  printf '%s' "$MAHDI_ALIASES_VERSION" >"$MAHDI_ALIASES_MARKER"
}

mahdi_install_aliases
