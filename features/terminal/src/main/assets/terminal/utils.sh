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

# apt سريع: بلا upgrade، بلا حزم اختيارية، وبمهلة قفل محدودة بدل الانتظار اللانهائي
apt_fast() {
  export DEBIAN_FRONTEND=noninteractive
  break_stale_apt_locks
  if [ ! -f /.cache/apt_last_update ] || [ -n "$(find /.cache/apt_last_update -mmin +180 2>/dev/null)" ]; then
    apt-get -o DPkg::Lock::Timeout=60 -o Acquire::Retries=3 update -qq >/dev/null 2>&1 || true
    mkdir -p /.cache 2>/dev/null || true
    touch /.cache/apt_last_update 2>/dev/null || true
  fi
  apt-get -o DPkg::Lock::Timeout=60 -o Acquire::Retries=3 install -y --no-install-recommends -o Acquire::Retries=3 "$@" </dev/null
}

# npm سريع وهادئ
npm_fast() {
  npm --no-fund --no-audit --loglevel=error --prefer-offline --progress=false "$@"
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

# تنزيل سريع: عدة اتصالات إن توفّر aria2c، وإلا curl مع استئناف وإعادة محاولة
fetch_fast() {
  local url="$1" out="$2"
  mkdir -p "$(dirname "$out")" 2>/dev/null || true
  if command_exists aria2c; then
    aria2c -x8 -s8 -k1M --allow-overwrite=true --auto-file-renaming=false \
      --console-log-level=warn --summary-interval=0 \
      -d "$(dirname "$out")" -o "$(basename "$out")" "$url" && return 0
  fi
  if command_exists curl; then
    curl -fL --retry 5 --retry-delay 2 --connect-timeout 20 -C - -o "$out" "$url" && return 0
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
