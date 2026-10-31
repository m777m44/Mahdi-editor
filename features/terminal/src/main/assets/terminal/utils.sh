RESET='\033[0m'

BOLD_BLUE='\033[1;34m'
BOLD_YELLOW='\033[1;33m'
BOLD_RED='\033[1;31m'
BOLD_GREEN='\033[1;32m'

BLUE_BG='\033[1;44m'
YELLOW_BG='\033[1;43m'
RED_BG='\033[1;41m'
GREEN_BG='\033[42m'

info() {
  printf "\n${BLUE_BG}  INFO  ${RESET} ${BOLD_BLUE}%s${RESET}\n" "$1"
}

warn() {
  printf "\n${YELLOW_BG}  WARN  ${RESET} ${BOLD_YELLOW}%s${RESET}\n" "$1"
}

error() {
  printf "\n${RED_BG} ERROR ${RESET} ${BOLD_RED}%s${RESET}\n" "$1"
}

success() {
  printf "\n${GREEN_BG} SUCCESS ${RESET} ${BOLD_GREEN}%s${RESET}\n" "$1"
}

ask() {
  local prompt="$1"
  local response

  while true; do
    printf "\n${BLUE_BG}  ?  ${RESET} ${BOLD_BLUE}%s${RESET}\n" "$prompt"
    read -rp "[y/N]: " response
    case "$response" in
      [Yy]|[Yy][Ee][Ss])
        return 0
        ;;
      [Nn]|[Nn][Oo]|"")
        return 1
        ;;
      *)
        warn "Please answer yes or no."
        ;;
    esac
  done
}

# ——————————————————————————————————————————————————————————————
# أدوات تثبيت سريعة ومحصّنة ضدّ أقفال apt الميتة (كانت تُعلّق التطبيق للأبد)
# ——————————————————————————————————————————————————————————————

# يحذف أقفال apt إن لم تكن هناك عملية حقيقية تستخدمها
break_stale_apt_locks() {
  local alive=1
  if command -v pgrep >/dev/null 2>&1; then
    if pgrep -x apt-get >/dev/null 2>&1 || pgrep -x apt >/dev/null 2>&1 || pgrep -x dpkg >/dev/null 2>&1; then
      alive=0
    fi
  elif ps -e 2>/dev/null | grep -Eq '([ /])(apt-get|apt|dpkg)( |$)'; then
    alive=0
  fi
  [ "$alive" -eq 0 ] && return 0
  local lock
  for lock in /var/lib/dpkg/lock-frontend /var/lib/dpkg/lock /var/cache/apt/archives/lock /var/lib/apt/lists/lock; do
    [ -e "$lock" ] && rm -f "$lock" 2>/dev/null
  done
  return 0
}

# اسم مختصر من وسائط الأمر (آخر وسيط ليس علمًا)
_mahdi_prog_label() {
  local a label=""
  for a in "$@"; do
    case "$a" in
      -*) continue ;;
      install | uninstall | update | upgrade | add | remove | i) continue ;;
      *) label="$a" ;;
    esac
  done
  printf '%s' "${label:-حزم النظام}"
}

# apt سريع: بلا upgrade، بلا حزم اختيارية، وبمهلة قفل محدودة بدل الانتظار اللانهائي
apt_fast() {
  export DEBIAN_FRONTEND=noninteractive
  break_stale_apt_locks
  local _label
  _label="$(_mahdi_prog_label "$@")"
  if declare -f mahdi_prog_auto >/dev/null 2>&1; then
    mahdi_prog_auto "$_label" "تجهيز حزم النظام…"
    mahdi_prog 20 "تجهيز مستودعات النظام" "$_label"
  fi
  if [ ! -f /.cache/apt_last_update ] || [ -n "$(find /.cache/apt_last_update -mmin +180 2>/dev/null)" ]; then
    apt-get -o DPkg::Lock::Timeout=60 -o Acquire::Retries=3 update -qq >/dev/null 2>&1 || true
    mkdir -p /.cache 2>/dev/null || true
    touch /.cache/apt_last_update 2>/dev/null || true
  fi
  declare -f mahdi_prog >/dev/null 2>&1 && mahdi_prog 45 "تنزيل وتثبيت حزم النظام" "$_label"
  local _rc=0
  apt-get -o DPkg::Lock::Timeout=60 -o Acquire::Retries=3 install -y --no-install-recommends -o Acquire::Retries=3 "$@" </dev/null || _rc=$?
  if [ "$_rc" -eq 0 ]; then
    declare -f mahdi_prog_done >/dev/null 2>&1 && mahdi_prog_done "تم تثبيت حزم النظام" "$_label"
  fi
  return $_rc
}

# مخزن دائم داخل التطبيق (يبقى بعد إغلاق التطبيق فلا يُعاد التنزيل)
MAHDI_CACHE_DIR="${MAHDI_CACHE_DIR:-$HOME/.mahdi/cache}"
mkdir -p "$MAHDI_CACHE_DIR/pip" "$MAHDI_CACHE_DIR/npm" "$MAHDI_CACHE_DIR/wheels" 2>/dev/null || true
export PIP_CACHE_DIR="${PIP_CACHE_DIR:-$MAHDI_CACHE_DIR/pip}"
export PIP_ROOT_USER_ACTION=ignore
export npm_config_cache="${npm_config_cache:-$MAHDI_CACHE_DIR/npm}"

# npm سريع وهادئ
npm_fast() {
  local _label _rc=0
  _label="$(_mahdi_prog_label "$@")"
  if declare -f mahdi_prog_auto >/dev/null 2>&1; then
    mahdi_prog_auto "$_label" "تنزيل الحزمة…"
    mahdi_prog 15 "تنزيل الحزمة من npm" "$_label"
  fi
  npm --no-fund --no-audit --loglevel=error --prefer-offline --progress=false "$@" || _rc=$?
  if [ "$_rc" -eq 0 ]; then
    declare -f mahdi_prog_done >/dev/null 2>&1 && mahdi_prog_done "تم تثبيت الحزمة" "$_label"
  fi
  return $_rc
}

install_nodejs() {
  info "Installing Node.js..."
  # أولًا: حزمة التوزيعة (أسرع بكثير من سكربت NodeSource)، ثم السكربت كخطة بديلة
  if apt_fast nodejs npm >/dev/null 2>&1 && command_exists node && command_exists npm; then
    success "Node.js installed from the distribution repository"
    return 0
  fi

  apt_fast curl ca-certificates >/dev/null 2>&1 || true
  if command_exists curl; then
    curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - >/dev/null 2>&1 || true
    apt_fast nodejs >/dev/null 2>&1 || apt-get -o DPkg::Lock::Timeout=60 install -y nodejs >/dev/null 2>&1 || true
  fi
  command_exists node
}

uninstall_nodejs() {
  if ask "Do you want to uninstall Node.js LTS? It was installed as a dependency of this language server. This will also remove all globally installed npm packages."; then
    info "Uninstalling Node.js LTS..."
    apt remove -y nodejs
    apt autoremove -y
    info "Node.js LTS uninstalled successfully."
  fi
}

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

# يقرأ نسبة التقدّم من سطر إخراج aria2/curl ويحدّث البطاقة
_mahdi_prog_parse() {
  local name="$1" line pct
  declare -f mahdi_prog >/dev/null 2>&1 || return 0
  while IFS= read -r line; do
    case "$line" in
      *"("*"%)"*)
        pct="${line#*(}"
        pct="${pct%%\%*}"
        pct="${pct//[!0-9]/}"
        [ -n "$pct" ] && mahdi_prog "$pct" "تنزيل" "$name"
        ;;
    esac
  done
  return 0
}

# تنزيل سريع: عدة اتصالات إن توفّر aria2c، وإلا curl مع استئناف وإعادة محاولة
fetch_fast() {
  local url="$1" out="$2"
  local name
  name="$(basename "$out")"
  mkdir -p "$(dirname "$out")" 2>/dev/null || true
  if declare -f mahdi_prog_auto >/dev/null 2>&1; then
    mahdi_prog_auto "$name" "تنزيل…"
    mahdi_prog 2 "بدء التنزيل" "$name"
  fi
  if command_exists aria2c; then
    aria2c -x8 -s8 -k1M --allow-overwrite=true --auto-file-renaming=false \
      --console-log-level=notice --summary-interval=1 \
      -d "$(dirname "$out")" -o "$(basename "$out")" "$url" 2>&1 |
      _mahdi_prog_parse "$name" || true
    [ -s "$out" ] && { declare -f mahdi_prog_done >/dev/null 2>&1 && mahdi_prog_done "اكتمل التنزيل" "$name"; return 0; }
  fi
  if command_exists curl; then
    curl -fL --retry 5 --retry-delay 2 --connect-timeout 20 -C - \
      --progress-bar -o "$out" "$url" 2>&1 | tr '\r' '\n' | _mahdi_prog_parse "$name" || true
    [ -s "$out" ] && { declare -f mahdi_prog_done >/dev/null 2>&1 && mahdi_prog_done "اكتمل التنزيل" "$name"; return 0; }
  fi
  wget -c -O "$out" "$url"
}

# تنزيل مع تسريع: يثبّت aria2 عند أول استخدام ثم ينزّل بعدة اتصالات
fetch_faster() {
  if ! command_exists aria2c; then
    apt_fast aria2 >/dev/null 2>&1 || true
  fi
  fetch_fast "$1" "$2"
}

# ——————————————————————————————————————————————————————————————
# شريط التقدّم الحيّ (يقرأه التطبيق ويعرضه في بطاقة أنيقة)
#
# يكتب ملفّ JSON صغيرًا في ~/.mahdi/progress.json بعد كل تحديث، ويقرأه التطبيق
# كل ربع ثانية. لا يؤثر على عمل السكربت إن لم يكن التطبيق يعمل.
# ——————————————————————————————————————————————————————————————

MAHDI_PROG_DIR="$HOME/.mahdi"
MAHDI_PROG_FILE="$MAHDI_PROG_DIR/progress.json"
MAHDI_PROG_NAME=""
MAHDI_PROG_ID=""

# ينظّف النص من أي شيء يفسد JSON
_mahdi_prog_clean() {
  printf '%s' "$1" | tr -d '"\\\n\r\t' | cut -c1-220
}

# ترجمة اسم الطور إلى الإنجليزية (للتطبيق ثنائي اللغة)
_mahdi_prog_en() {
  case "$1" in
    "جارٍ البدء…") printf 'Starting…' ;;
    "جارٍ التحضير…") printf 'Preparing…' ;;
    "تجهيز حزم النظام…") printf 'Preparing system packages…' ;;
    "تجهيز مستودعات النظام") printf 'Refreshing repositories' ;;
    "تنزيل وتثبيت حزم النظام") printf 'Downloading system packages' ;;
    "تم تثبيت حزم النظام") printf 'System packages installed' ;;
    "تنزيل الحزمة…") printf 'Downloading package…' ;;
    "تنزيل الحزمة من npm") printf 'Downloading from npm' ;;
    "تم تثبيت الحزمة") printf 'Package installed' ;;
    "تنزيل") printf 'Downloading' ;;
    "بدء التنزيل") printf 'Starting download' ;;
    "اكتمل التنزيل") printf 'Download complete' ;;
    "تثبيت المكتبة") printf 'Installing library' ;;
    "تم التثبيت بنجاح") printf 'Installed successfully' ;;
    "تعذّر تثبيت المكتبة") printf 'Installation failed' ;;
    "التحقق من الإصدارات") printf 'Resolving versions' ;;
    "تنزيل الحزم") printf 'Downloading packages' ;;
    "إعادة المحاولة بمصدر بديل") printf 'Retrying with a mirror' ;;
    "تم تثبيت المكتبات") printf 'Libraries installed' ;;
    "تم التثبيت") printf 'Installed' ;;
    "تثبيت مباشر بـ pip…") printf 'Installing with pip…' ;;
    "تجهيز الخادم…") printf 'Preparing language server…' ;;
    "تم تثبيت الخادم") printf 'Language server installed' ;;
    *) printf '%s' "$1" ;;
  esac
}

_mahdi_prog_write() {
  local percent="$1" stage="$2" detail="$3" done="$4"
  mkdir -p "$MAHDI_PROG_DIR" 2>/dev/null || return 0
  [ -z "$MAHDI_PROG_ID" ] && MAHDI_PROG_ID="$(date +%s)-$$"
  local tmp="$MAHDI_PROG_FILE.tmp"
  printf '{"id":"%s","name":"%s","stage":"%s","stage_en":"%s","detail":"%s","percent":%s,"done":%s,"pid":%s,"ts":%s}\n' \
    "$MAHDI_PROG_ID" \
    "$(_mahdi_prog_clean "$MAHDI_PROG_NAME")" \
    "$(_mahdi_prog_clean "$stage")" \
    "$(_mahdi_prog_clean "$(_mahdi_prog_en "$stage")")" \
    "$(_mahdi_prog_clean "$detail")" \
    "$percent" "$done" "$$" "$(date +%s)" >"$tmp" 2>/dev/null || return 0
  mv -f "$tmp" "$MAHDI_PROG_FILE" 2>/dev/null || true
  return 0
}

# بدء عملية جديدة: mahdi_prog_start "requests"
mahdi_prog_start() {
  MAHDI_PROG_NAME="${1:-}"
  MAHDI_PROG_ID="$(date +%s)-$$"
  _mahdi_prog_write 0 "${2:-جارٍ البدء…}" ""
}

# بدء فقط إن لم تكن هناك عملية جارية (تستخدمه أدوات التثبيت تلقائيًا)
mahdi_prog_auto() {
  [ -n "$MAHDI_PROG_ID" ] && return 0
  mahdi_prog_start "$1" "${2:-جارٍ التحضير…}"
}

# تحديث: mahdi_prog 42 "تنزيل الحزمة" "1.2/2.8 MB"
mahdi_prog() {
  _mahdi_prog_write "${1:-0}" "${2:-}" "${3:-}" false
}

# إخفاء البطاقة فورًا (لعمليات صامتة)
mahdi_prog_clear() {
  MAHDI_PROG_ID=""
  rm -f "$MAHDI_PROG_FILE" 2>/dev/null || true
}

# انتهاء ناجح
mahdi_prog_done() {
  _mahdi_prog_write 100 "${1:-تم بنجاح}" "${2:-}" true
  MAHDI_PROG_ID=""
}

# فشل
mahdi_prog_fail() {
  _mahdi_prog_write 100 "${1:-تعذّر الإكمال}" "${2:-}" true
  MAHDI_PROG_ID=""
}
