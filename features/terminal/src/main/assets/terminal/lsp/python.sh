set -e

source "$LOCAL/bin/utils"

info 'Installing Pyright (Python language server)...'

install() {
  # بطاقة التقدّم: اسم الخادم ونسبته من 0 إلى 100 داخل التطبيق
  declare -f mahdi_prog_start >/dev/null 2>&1 && (mahdi_prog_start "Pyright (Python)" "تجهيز الخادم…") || true
  if ! command_exists node || ! command_exists npm; then
    install_nodejs
  fi

  info 'Installing Pyright language server...'
  npm_fast install -g --prefix /usr pyright

  declare -f mahdi_prog_done >/dev/null 2>&1 && (mahdi_prog_done 'تم تثبيت الخادم') || true
  success 'Python language server installed successfully.'
  exit 0
}

uninstall() {
  info 'Uninstalling Pyright...'
  npm_fast uninstall -g --prefix /usr pyright
  info 'Python language server uninstalled successfully.'
  uninstall_nodejs
  exit 0
}

update() {
  info 'Updating Pyright...'
  npm_fast install -g --prefix /usr pyright@latest
  info 'Python language server updated successfully.'
  exit 0
}

case "$1" in
  --uninstall) uninstall;;
  --update) update;;
  *) install;;
esac
