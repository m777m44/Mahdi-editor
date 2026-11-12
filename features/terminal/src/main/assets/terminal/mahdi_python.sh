#!/bin/bash
# shellcheck shell=bash
# ——————————————————————————————————————————————————————————————————————————————
# mahdi-python — بيئة بايثون داخل Mahdi-Editor (8.5.0)
#
#   mahdi-python             معلومات المفسّر المستخدم حاليًا
#   mahdi-python --list      كل مفسّرات بايثون المتاحة في الحاوية
#   mahdi-python --use X     اختيار مفسّر (python3.12 مثلًا) للجلسات القادمة
#   mahdi-python --reset     العودة إلى الاختيار التلقائي (أحدث إصدار يملك pip)
#   mahdi-python --doctor    فحص شامل: المفسّر · pip · المخازن · الشبكة
#   mahdi-python --upgrade   تحديث بايثون من مستودع التوزيعة (بلا أي سؤال)
#
# التطبيق يختار تلقائيًا **أعلى إصدار يمتلك pip**، ويُثبّت المكتبات بنفس
# المفسّر الذي سيشغّل السكربت، فلا تضيع الواردات بين إصدارين.
# للتعطيل اليدوي: MAHDI_PY_PREFERRED=python3.11
# ——————————————————————————————————————————————————————————————————————————————

source "$LOCAL/bin/utils" 2>/dev/null || exit 1

# بدائل إن كان utils قديمًا (تثبيت لم يُحدَّث بعد)
declare -f mahdi_human_size >/dev/null 2>&1 || mahdi_human_size() { printf '%s' "${1:-0} B"; }
declare -f mahdi_path_bytes >/dev/null 2>&1 || mahdi_path_bytes() { printf '0'; }
declare -f mahdi_ar_plural >/dev/null 2>&1 || mahdi_ar_plural() { printf '%s' "$1"; }

_pref_file="${HOME:-/home}/.mahdi/python_preferred"
_scan_file="${HOME:-/home}/.mahdi/python_scan"
_cmd="${1:---info}"

_version_of() {
  local v
  v="$("$1" -c 'import sys;print("%d.%d.%d" % sys.version_info[:3])' 2>/dev/null)"
  if [ -n "$v" ]; then printf '%s' "$v"; else printf '—'; fi
}

_pip_of() {
  local v
  v="$("$1" -m pip --version 2>/dev/null | awk '{print $2; exit}')"
  if [ -n "$v" ]; then printf '%s' "$v"; else printf '—'; fi
}

_pkg_count() {
  "$1" - 2>/dev/null <<'PY' || printf '—'
import importlib.metadata as meta

try:
    print(len(list(meta.distributions())))
except Exception:
    print(0)
PY
}

_list_pythons() {
  local c v pip marked pip_tone
  for c in python3 python3.9 python3.10 python3.11 python3.12 python3.13 python3.14; do
    command -v "$c" >/dev/null 2>&1 || continue
    v="$(_version_of "$c")"
    pip="$(_pip_of "$c")"
    marked="${M_MUTED}·${M_RESET}"
    [ "$c" = "${MAHDI_PY:-python3}" ] && marked="${M_MINT}●${M_RESET}"
    if [ "$pip" = "—" ]; then
      pip_tone="${M_CORAL}"
    else
      pip_tone="${M_TEAL}"
    fi
    printf "  %b  ${M_BOLD}%-12s${M_RESET} ${M_TEAL}%-10s${M_RESET} ${pip_tone}pip %-9s${M_RESET} ${M_DIM}%s${M_RESET}\n" \
      "$marked" "$c" "$v" "$pip" "$(command -v "$c")"
  done
}

case "$_cmd" in
  --info | -i | "")
    mahdi_heading "بيئة بايثون"
    mahdi_kv "الإصدار" "$(mahdi_python_version)"
    mahdi_kv "المفسّر" "$(command -v "${MAHDI_PY:-python3}" 2>/dev/null || printf '—')"
    mahdi_kv "pip" "$(_pip_of "${MAHDI_PY:-python3}")"
    mahdi_kv "الحزم" "$(mahdi_ar_plural "$(_pkg_count "${MAHDI_PY:-python3}")" "حزمة واحدة" "حزمتان" "حزم" "حزمة")"
    mahdi_kv "المخزن" "${MAHDI_CACHE_DIR:-${HOME:-/home}/.mahdi/cache}/wheels · $(mahdi_human_size "$(mahdi_path_bytes "${MAHDI_CACHE_DIR:-${HOME:-/home}/.mahdi/cache}/wheels")")"
    printf "\n"
    mahdi_heading "المفسّرات المتاحة"
    _list_pythons
    printf "\n${M_MUTED}  %s${M_RESET}\n" "--use python3.12 للاختيار · --reset للعودة التلقائية · --doctor للفحص"
    ;;

  --list | -l)
    mahdi_heading "المفسّرات المتاحة"
    _list_pythons
    ;;

  --use | -u)
    _target="${2:-}"
    if [ -z "$_target" ] || ! command -v "$_target" >/dev/null 2>&1; then
      error "المفسّر غير موجود: ${_target:-—}"
      printf "${M_MUTED}  استعمل: mahdi-python --use python3.12${M_RESET}\n"
      exit 1
    fi
    _target_pip="$(_pip_of "$_target")"
    if [ "$_target_pip" = "—" ]; then
      warn "هذا المفسّر بلا pip — ستُثبَّت المكتبات بمفسّر آخر وقد لا تُوجد عند التشغيل."
      printf "${M_MUTED}  الأفضل: mahdi-python --upgrade ثم أعد المحاولة${M_RESET}\n"
    fi
    mkdir -p "$(dirname "$_pref_file")" 2>/dev/null || true
    printf '%s\n' "$_target" >"$_pref_file"
    rm -f "$_scan_file" 2>/dev/null || true
    success "سيُستخدم $(_version_of "$_target") مع $_target في الجلسات القادمة"
    printf "${M_MUTED}  للجلسة الحالية: export MAHDI_PY_PREFERRED=%s${M_RESET}\n" "$_target"
    ;;

  --reset)
    rm -f "$_pref_file" "$_scan_file" 2>/dev/null || true
    success "أُعيد الاختيار التلقائي — الفحص التالي يختار أحدث إصدار يملك pip"
    printf "${M_MUTED}  المفسّر المختار الآن: %s${M_RESET}\n" "$(mahdi_python_bin)"
    ;;

  --doctor)
    shift || true
    if [ -x "$LOCAL/bin/mh" ]; then
      exec "$LOCAL/bin/mh" doctor "$@"
    fi
    mahdi_heading "فحص بايثون"
    mahdi_kv "الإصدار" "$(mahdi_python_version)"
    mahdi_kv "pip" "$(_pip_of "${MAHDI_PY:-python3}")"
    mahdi_kv "الحزم" "$(_pkg_count "${MAHDI_PY:-python3}")"
    ;;

  --upgrade)
    export DEBIAN_FRONTEND=noninteractive
    printf "${M_SKY}▸${M_RESET}  جارٍ تحديث بايثون من مستودع التوزيعة…\n"
    if declare -f apt_fast >/dev/null 2>&1; then
      apt_fast --only-upgrade python3 python3-pip python3-venv >/dev/null 2>&1 || true
    else
      apt-get update -qq >/dev/null 2>&1 || true
      apt-get install -y --only-upgrade --no-install-recommends \
        python3 python3-pip python3-venv </dev/null >/dev/null 2>&1 || true
    fi
    rm -f "$_scan_file" 2>/dev/null || true
    success "تمّ التحديث — الإصدار المتاح الآن: $(mahdi_python_version)"
    printf "${M_MUTED}  يُثبَّت أحدث إصدار توفّره التوزيعة؛ بناء CPython جديد داخل الحاوية غير موثوق.${M_RESET}\n"
    ;;

  *)
    error "أمر غير معروف: $_cmd"
    printf "${M_MUTED}  mahdi-python · --list · --use <bin> · --reset · --doctor · --upgrade${M_RESET}\n"
    exit 2
    ;;
esac
