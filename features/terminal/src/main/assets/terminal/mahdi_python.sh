#!/bin/bash
# shellcheck shell=bash
# ——————————————————————————————————————————————————————————————————————————————
# mahdi-python — بيئة بايثون داخل Mahdi-Editor (8.4.0)
#
#   mahdi-python            معلومات المفسّر المستخدم حاليًا
#   mahdi-python --list     كل مفسّرات بايثون المتاحة في الحاوية
#   mahdi-python --use X    اختيار مفسّر (python3.12 مثلًا) للجلسات القادمة
#   mahdi-python --upgrade  تحديث بايثون من مستودع التوزيعة (بلا أي سؤال)
#
# التطبيق يختار تلقائيًا **أعلى إصدار يمتلك pip**، ويُثبّت المكتبات بنفس
# المفسّر الذي سيشغّل السكربت، فلا تضيع الواردات بين إصدارين.
# للتعطيل اليدوي: MAHDI_PY_PREFERRED=python3.11
# ——————————————————————————————————————————————————————————————————————————————

source "$LOCAL/bin/utils" 2>/dev/null || exit 1

_pref_file="${HOME:-/home}/.mahdi/python_preferred"
_cmd="${1:---info}"

_version_of() {
  "$1" -c 'import sys;print("%d.%d.%d" % sys.version_info[:3])' 2>/dev/null || printf '—'
}

_pip_of() {
  "$1" -m pip --version 2>/dev/null | awk '{print $2}' || printf '—'
}

_list_pythons() {
  local c v pip marked
  for c in python3 python3.9 python3.10 python3.11 python3.12 python3.13 python3.14; do
    command -v "$c" >/dev/null 2>&1 || continue
    v="$(_version_of "$c")"
    pip="$(_pip_of "$c")"
    marked=" "
    [ "$c" = "${MAHDI_PY:-python3}" ] && marked="${M_MINT}●${M_RESET}"
    printf "  %b  ${M_BOLD}%-12s${M_RESET} ${M_TEAL}%-10s${M_RESET} ${M_MUTED}pip %-9s${M_RESET} ${M_DIM}%s${M_RESET}\\n" \
      "$marked" "$c" "$v" "$pip" "$(command -v "$c")"
  done
}

case "$_cmd" in
  --info | -i | "")
    mahdi_heading "بيئة بايثون"
    mahdi_kv "الإصدار" "$(mahdi_python_version)"
    mahdi_kv "المفسّر" "$(command -v "${MAHDI_PY:-python3}" 2>/dev/null || printf '—')"
    mahdi_kv "pip" "$(_pip_of "${MAHDI_PY:-python3}")"
    mahdi_kv "المخزن" "${MAHDI_CACHE_DIR:-${HOME:-/home}/.mahdi/cache}/wheels"
    printf "\\n"
    mahdi_heading "المفسّرات المتاحة"
    _list_pythons
    printf "\\n${M_MUTED}  %s${M_RESET}\\n" "mahdi-python --use python3.12 لاختيار إصدار · --upgrade للتحديث"
    ;;

  --list | -l)
    mahdi_heading "المفسّرات المتاحة"
    _list_pythons
    ;;

  --use | -u)
    _target="${2:-}"
    if [ -z "$_target" ] || ! command -v "$_target" >/dev/null 2>&1; then
      error "المفسّر غير موجود: ${_target:-—}"
      printf "${M_MUTED}  استعمل: mahdi-python --use python3.12${M_RESET}\\n"
      exit 1
    fi
    mkdir -p "$(dirname "$_pref_file")" 2>/dev/null || true
    printf '%s\n' "$_target" > "$_pref_file"
    success "سيُستخدم $(_version_of "$_target") مع $_target في الجلسات القادمة"
    printf "${M_MUTED}  للجلسة الحالية: export MAHDI_PY_PREFERRED=%s${M_RESET}\\n" "$_target"
    ;;

  --upgrade)
    export DEBIAN_FRONTEND=noninteractive
    printf "${M_SKY}▸${M_RESET}  جارٍ تحديث بايثون من مستودع التوزيعة…\\n"
    if declare -f apt_fast >/dev/null 2>&1; then
      apt_fast --only-upgrade python3 python3-pip python3-venv >/dev/null 2>&1 || true
    else
      apt-get update -qq >/dev/null 2>&1 || true
      apt-get install -y --only-upgrade --no-install-recommends \
        python3 python3-pip python3-venv </dev/null >/dev/null 2>&1 || true
    fi
    success "تمّ التحديث — الإصدار المتاح الآن: $(mahdi_python_version)"
    printf "${M_MUTED}  يُثبَّت أحدث إصدار توفّره التوزيعة؛ بناء CPython جديد داخل الحاوية غير موثوق.${M_RESET}\\n"
    ;;

  *)
    error "أمر غير معروف: $_cmd"
    printf "${M_MUTED}  mahdi-python · --list · --use <bin> · --upgrade${M_RESET}\\n"
    exit 2
    ;;
esac
