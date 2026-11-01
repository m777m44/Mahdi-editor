# shellcheck shell=bash
# ——————————————————————————————————————————————————————————————————————————————
# شريط تقدّم أحادي السطر داخل الطرفية (3.0.0)
#
# يُرسم في **نفس السطر** عبر \r، فلا تتكوّم الأسطر ولا تتكرّر الكتابة:
#   [████████░░░░░░░░░░░░░░░░]  40%  تثبيت Python
#
# الاستخدام:
#   mahdi_bar_start "تثبيت Python"
#   mahdi_bar_set 40 "تثبيت Python"
#   mahdi_bar_done "Python جاهز"
#
# لا يُطبع السطر إذا كانت النسبة لم تتغيّر (منع الكتابة المكرّرة)،
# ويمكن تعطيل الشريط كليًا بـ MAHDI_BAR=0.
# ——————————————————————————————————————————————————————————————————————————————

MAHDI_BAR_WIDTH="${MAHDI_BAR_WIDTH:-24}"
MAHDI_BAR_ENABLED="${MAHDI_BAR:-1}"
MAHDI_BAR_LAST=""

mahdi_bar_render() {
  local percent="$1" label="${2:-}"
  [ "$MAHDI_BAR_ENABLED" = "0" ] && return 0
  [ "$percent" = "$MAHDI_BAR_LAST" ] && return 0
  MAHDI_BAR_LAST="$percent"

  local width="$MAHDI_BAR_WIDTH"
  local filled=$(( percent * width / 100 ))
  [ "$filled" -gt "$width" ] && filled="$width"
  [ "$filled" -lt 0 ] && filled=0

  local bar="" i=0
  while [ "$i" -lt "$filled" ]; do bar="$bar█"; i=$((i + 1)); done
  while [ "$i" -lt "$width" ]; do bar="$bar░"; i=$((i + 1)); done

  # \r يعود لبداية السطر، والمسافات في النهاية تمسح بقايا سطر أطول
  printf '\r\033[36m[%s]\033[0m %3d%%  %s          ' "$bar" "$percent" "$label"
}

# بداية الشريط: يمسح السطر الحالي ويبدأ من 0٪
mahdi_bar_start() {
  [ "$MAHDI_BAR_ENABLED" = "0" ] && return 0
  MAHDI_BAR_LAST=""
  printf '\r\033[K'
  mahdi_bar_render 0 "${1:-}"
}

# تحديث النسبة (0-100)
mahdi_bar_set() {
  mahdi_bar_render "${1:-0}" "${2:-}"
}

# إنهاء الشريط: 100٪ ثم سطر جديد واحد
mahdi_bar_done() {
  [ "$MAHDI_BAR_ENABLED" = "0" ] && return 0
  MAHDI_BAR_LAST=""
  mahdi_bar_render 100 "${1:-}"
  printf '\n'
  MAHDI_BAR_LAST=""
}

# إلغاء الشريط بلا سطر نجاح (عند الفشل)
mahdi_bar_abort() {
  [ "$MAHDI_BAR_ENABLED" = "0" ] && return 0
  printf '\r\033[K'
  MAHDI_BAR_LAST=""
}
