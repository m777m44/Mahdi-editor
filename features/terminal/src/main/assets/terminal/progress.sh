# shellcheck shell=bash
# ——————————————————————————————————————————————————————————————————————————————
# شريط تقدّم أحادي السطر داخل الطرفية (5.5.0)
#
# يُرسم في **نفس السطر** عبر \r، فلا تتكوّم الأسطر ولا تتكرّر الكتابة:
#   ▓▓▓▓▓▓▓▓░░░░░░░░░░░░  40%  تثبيت Python
#
# المواصفات المطلوبة (5.5.0):
# - عرض ثابت = **٢٠ خلية**، حرف مملوء `▓` وفارغ `░`.
# - النسبة تُطبع بنمط %3d%%.
# - البداية صفراء، الشريط سماوي، النجاح أخضر، الفشل أحمر.
# - يُعاد الرسم فقط عند **تغيّر النسبة** (لا كتابة مكرّرة ولا وميض).
# - يُمسح السطر في النهاية بـ \r\033[K فلا يبقى أثر.
#
# الاستخدام:
#   mahdi_bar_start "تثبيت Python"    # سطر أصفر + شريط 0%
#   mahdi_bar_set 40 "تثبيت Python"   # تحديث في نفس السطر
#   mahdi_bar_done "Python جاهز"      # 100% أخضر + سطر جديد
#   mahdi_bar_abort "فشل التثبيت"     # أحمر + سطر جديد
#
# التعطيل: MAHDI_BAR=0
# ——————————————————————————————————————————————————————————————————————————————

MAHDI_BAR_WIDTH="${MAHDI_BAR_WIDTH:-20}"
# 8.0.0: الشريط داخل الطرفية **مطفأ افتراضيًا**.
#
# كان يُرسم سطر في الطرفية *و* بطاقة عائمة في الوقت نفسه، فتظهر نسبتان وأسطر
# تتراكب (وهو ما في لقطة المستخدم). صارت البطاقة هي المصدر الوحيد للتقدّم،
# ولمن يفضّل السطر داخل الطرفية: MAHDI_BAR=1
MAHDI_BAR_ENABLED="${MAHDI_BAR:-0}"
MAHDI_BAR_LAST=""
MAHDI_BAR_LABEL=""

# يرسم سطرًا واحدًا: شريط سماوي + نسبة + تسمية
mahdi_bar_render() {
  local percent="$1" label="${2:-}"
  [ "$MAHDI_BAR_ENABLED" = "0" ] && return 0
  # لا نُعيد الرسم إن لم تتغيّر النسبة — هذا ما يمنع الوميض والتكرار
  [ "$percent" = "$MAHDI_BAR_LAST" ] && return 0
  MAHDI_BAR_LAST="$percent"
  if [ -n "$label" ]; then MAHDI_BAR_LABEL="$label"; fi

  local width="$MAHDI_BAR_WIDTH"
  local filled=$((percent * width / 100))
  [ "$filled" -gt "$width" ] && filled="$width"
  [ "$filled" -lt 0 ] && filled=0

  local bar="" i=0
  while [ "$i" -lt "$filled" ]; do bar="$bar▓"; i=$((i + 1)); done
  while [ "$i" -lt "$width" ]; do bar="$bar░"; i=$((i + 1)); done

  # \r يعود لبداية السطر، و\033[K يمسح أي بقايا أطول
  printf '\r\033[K\033[33m▸ %s\033[0m \033[36m[%s]\033[0m \033[33m%3d%%\033[0m' \
    "$MAHDI_BAR_LABEL" "$bar" "$percent"
  return 0
}

# بداية الشريط: سطر واحد فقط — تسمية صفراء + شريط 0% (لا سطر إضافي)
mahdi_bar_start() {
  [ "$MAHDI_BAR_ENABLED" = "0" ] && return 0
  MAHDI_BAR_LAST=""
  MAHDI_BAR_LABEL="${1:-}"
  mahdi_bar_render 0 "$MAHDI_BAR_LABEL"
  return 0
}

# تحديث النسبة (0-100)
mahdi_bar_set() {
  mahdi_bar_render "${1:-0}" "${2:-}"
  return 0
}

# إنهاء ناجح: 100% أخضر ثم سطر جديد
mahdi_bar_done() {
  [ "$MAHDI_BAR_ENABLED" = "0" ] && return 0
  local label="${1:-$MAHDI_BAR_LABEL}"
  MAHDI_BAR_LAST=""
  local width="$MAHDI_BAR_WIDTH"
  local bar="" i=0
  while [ "$i" -lt "$width" ]; do bar="$bar▓"; i=$((i + 1)); done
  printf '\r\033[K\033[32m[%s]\033[0m \033[32m100%%\033[0m  \033[32m✔ %s\033[0m\n' "$bar" "$label"
  MAHDI_BAR_LAST=""
  MAHDI_BAR_LABEL=""
  return 0
}

# فشل: سطر أحمر ثم تنظيف السطر
mahdi_bar_abort() {
  [ "$MAHDI_BAR_ENABLED" = "0" ] && return 0
  local label="${1:-$MAHDI_BAR_LABEL}"
  printf '\r\033[K\033[31m✖ %s\033[0m\n' "${label:-فشل}"
  MAHDI_BAR_LAST=""
  MAHDI_BAR_LABEL=""
  return 0
}
