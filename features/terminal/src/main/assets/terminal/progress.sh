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

# 8.2.0: تدرّج حقيقي داخل الشريط (سماوي ← بنفسجي) بدل لون سماوي واحد.
# محطّات RGB للخلايا المملوءة، ورمادي خافت للخلايا الفارغة.
MAHDI_BAR_STOPS=(56 189 248  45 212 191  52 211 153  245 215 110  167 139 250  244 114 182)

# استقلال عن utils إن استُدعي الشريط وحده
if ! declare -f mahdi_rgb >/dev/null 2>&1; then
  M_RESET='' M_BOLD='' M_GOLD='' M_MINT='' M_CORAL='' M_MUTED=''
  mahdi_rgb() { :; }
fi

# يبني سلسلة الشريط (ألوان + محارف) لتُفسَّر لاحقًا عبر printf '%b'
_mahdi_bar_build() {
  local width="$1" filled="$2" i=0 k r g b out=''
  while [ "$i" -lt "$width" ]; do
    if [ "$i" -lt "$filled" ]; then
      k=$((i * 6 / width))
      [ "$k" -gt 5 ] && k=5
      r=${MAHDI_BAR_STOPS[k * 3]}
      g=${MAHDI_BAR_STOPS[k * 3 + 1]}
      b=${MAHDI_BAR_STOPS[k * 3 + 2]}
      out="${out}$(mahdi_rgb "$r" "$g" "$b")▓"
    else
      out="${out}${M_MUTED}░"
    fi
    i=$((i + 1))
  done
  printf '%s' "$out"
}

# يرسم سطرًا واحدًا: شريط متدرّج + نسبة + تسمية، بلا كتابة مكرّرة
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

  local bar
  bar="$(_mahdi_bar_build "$width" "$filled")"

  # \r يعود لبداية السطر، و\033[K يمسح أي بقايا أطول من السطر الجديد
  printf '\r\033[K'
  printf "${M_GOLD}▸ %s${M_RESET}  " "$MAHDI_BAR_LABEL"
  printf '%b' "$bar"
  printf "${M_RESET} ${M_BOLD}%3d%%${M_RESET}" "$percent"
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
  local width="$MAHDI_BAR_WIDTH"
  local bar
  bar="$(_mahdi_bar_build "$width" "$width")"
  printf '\r\033[K'
  printf '%b' "$bar"
  printf "${M_RESET} ${M_BOLD}100%%${M_RESET}  ${M_MINT}✔ %s${M_RESET}\n" "$label"
  MAHDI_BAR_LAST=""
  MAHDI_BAR_LABEL=""
  return 0
}

# فشل: سطر أحمر ثم تنظيف السطر
mahdi_bar_abort() {
  [ "$MAHDI_BAR_ENABLED" = "0" ] && return 0
  local label="${1:-$MAHDI_BAR_LABEL}"
  printf "\r\033[K${M_CORAL}✖ %s${M_RESET}\n" "${label:-فشل}"
  MAHDI_BAR_LAST=""
  MAHDI_BAR_LABEL=""
  return 0
}
