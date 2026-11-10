# shellcheck shell=bash
# ——————————————————————————————————————————————————————————————————————————————
# شعار Mahdi-Editor داخل الطرفية (8.2.0)
#
# - الجلسة التفاعلية: لوحة فنّية متدرّجة الألوان، مرة واحدة لكل جلسة.
# - تشغيل أي سكربت: نسخة **مدمجة** من ثلاثة أسطر (MAHDI_BANNER_FORCE=1)
#   فلا تزاحم مخرجات السكربت ولا تملأ شاشة الهاتف باثني عشر سطرًا.
# - إطار واحد فقط، بلا أي أسطر شرح: الشكل وحده يكفي (6.1.0).
# - الإصدار الحقيقي يُقرأ من `$LOCAL/bin/.mahdi_version` الذي يكتبه التطبيق
#   عند كل إقلاع، فلا يبقى الرقم قديمًا أبدًا.
# - يمكن تعطيله كليًا: MAHDI_BANNER=0
#
# هندسة الإطار: الخطوط الفنية مرسومة بمحارف `█ ╔ ═` (كل محرف بعرض عمود واحد)،
# وسطر الإصدار **ASCII فقط** حتى يكون `${#var}` عدد المحارف = عدد الأعمدة في كل
# لغة/ترميز داخل الجلسة. لذلك لا يختلّ الإطار مهما كانت `LANG`.
# ——————————————————————————————————————————————————————————————————————————————

MAHDI_BANNER_ENABLED="${MAHDI_BANNER:-1}"

# إن استُدعي الشعار وحده (بلا utils) لا تُرمى أخطاء «أمر غير موجود» داخل الطرفية
if ! declare -f mahdi_rgb >/dev/null 2>&1; then
  M_RESET='' M_SKY='' M_TEAL='' M_VIOLET=''
  mahdi_rgb() { :; }
fi

# الإصدار: متغيّر بيئة، ثم ملف التطبيق، ثم قيمة احتياطية
if [ -z "${MAHDI_VERSION:-}" ]; then
  if [ -n "${LOCAL:-}" ] && [ -f "$LOCAL/bin/.mahdi_version" ]; then
    MAHDI_VERSION="$(cat "$LOCAL/bin/.mahdi_version" 2>/dev/null)"
  fi
fi
MAHDI_VERSION="${MAHDI_VERSION:-8.2.0}"

# ست محطّات لونية: سماوي ← تركوازي ← نعناعي ← ذهبي ← بنفسجي ← وردي.
# تُستخدم للأحرف الفنية والحدود، فتبدو اللوحة متدرّجة بدل لون واحد قديم.
MAHDI_BANNER_STOPS=(56 189 248  45 212 191  52 211 153  245 215 110  167 139 250  244 114 182)

# حدّ متدرّج: يسار + `n` محارف ملوّنة + يمين، في سطر واحد
_mahdi_banner_border() {
  local left="$1" right="$2" n=46 i=0 k r g b out=''
  while [ "$i" -lt "$n" ]; do
    k=$((i * 6 / n))
    [ "$k" -gt 5 ] && k=5
    r=${MAHDI_BANNER_STOPS[k * 3]}
    g=${MAHDI_BANNER_STOPS[k * 3 + 1]}
    b=${MAHDI_BANNER_STOPS[k * 3 + 2]}
    out="${out}$(mahdi_rgb "$r" "$g" "$b")─"
    i=$((i + 1))
  done
  printf "${M_SKY}  %s%b%s${M_RESET}\n" "$left" "$out" "$right"
}

# سطر فنّي واحد بلون من المحطّات الست
_mahdi_banner_art() {
  local idx="$1" art="$2"
  local r=${MAHDI_BANNER_STOPS[idx * 3]}
  local g=${MAHDI_BANNER_STOPS[idx * 3 + 1]}
  local b=${MAHDI_BANNER_STOPS[idx * 3 + 2]}
  printf "${M_TEAL}  │${M_RESET}%b%s${M_RESET}${M_TEAL}  │${M_RESET}\n" \
    "$(mahdi_rgb "$r" "$g" "$b")" "  $art"
}

# سطر الإصدار: ASCII خالص، محسوب العرض بدقّة (لا يختلّ مع RTL ولا مع LANG)
_mahdi_banner_version() {
  local inner=46
  local ver_line="  Mahdi-Editor  |  v${MAHDI_VERSION}"
  local pad=$((inner - ${#ver_line}))
  if [ "$pad" -lt 0 ]; then
    ver_line="${ver_line:0:$inner}"
    pad=0
  fi
  printf "${M_TEAL}  │${M_RESET}${M_VIOLET}%s%*s${M_RESET}${M_TEAL}│${M_RESET}\n" "$ver_line" "$pad" ""
}

_mahdi_banner_full() {
  printf '\n'
  _mahdi_banner_border '╭' '╮'
  _mahdi_banner_art 0 '███╗   ███╗  █████╗  ██╗  ██╗ ██████╗  ██╗'
  _mahdi_banner_art 1 '████╗ ████║ ██╔══██╗ ██║  ██║ ██╔══██╗ ██║'
  _mahdi_banner_art 2 '██╔████╔██║ ███████║ ███████║ ██║  ██║ ██║'
  _mahdi_banner_art 3 '██║╚██╔╝██║ ██╔══██║ ██╔══██║ ██║  ██║ ██║'
  _mahdi_banner_art 4 '██║ ╚═╝ ██║ ██║  ██║ ██║  ██║ ██████╔╝ ██║'
  _mahdi_banner_art 5 '╚═╝     ╚═╝ ╚═╝  ╚═╝ ╚═╝  ╚═╝ ╚═════╝  ╚═╝'
  _mahdi_banner_border '├' '┤'
  _mahdi_banner_version
  _mahdi_banner_border '╰' '╯'
  printf '\n'
}

# عند تشغيل سكربت: ثلاثة أسطر فقط — لا تزاحم المخرجات ولا تُطيل الشاشة
_mahdi_banner_compact() {
  printf '\n'
  _mahdi_banner_border '╭' '╮'
  _mahdi_banner_version
  _mahdi_banner_border '╰' '╯'
  printf '\n'
}

mahdi_banner() {
  [ "$MAHDI_BANNER_ENABLED" = "0" ] && return 0

  # الوضعان: إجباري (تشغيل سكربت) أو تفاعلي مرة واحدة لكل جلسة
  if [ "${MAHDI_BANNER_FORCE:-0}" = "1" ]; then
    _mahdi_banner_compact
    return 0
  fi

  [ -n "${MAHDI_BANNER_SHOWN:-}" ] && return 0
  [ -n "${BASH_EXECUTION_STRING:-}" ] && return 0
  [ -t 1 ] || return 0
  export MAHDI_BANNER_SHOWN=1
  _mahdi_banner_full
}

mahdi_banner
