set -e

source "$LOCAL/bin/utils"

install() {
  # بطاقة التقدّم: اسم الخادم ونسبته من 0 إلى 100 داخل التطبيق
  declare -f mahdi_prog_start >/dev/null 2>&1 && (mahdi_prog_start "Emmet" "تجهيز الخادم…") || true
  if ! command_exists node || ! command_exists npm; then
    install_nodejs
  fi

  info 'Installing Emmet language server...'
  npm_fast install -g --prefix /usr @olrtg/emmet-language-server

  info 'Emmet language server installed successfully.'
  exit 0
}

uninstall() {
  info 'Uninstalling Emmet language server...'
  npm_fast uninstall -g --prefix /usr @olrtg/emmet-language-server
  info 'Emmet language server uninstalled successfully.'
  uninstall_nodejs
  exit 0
}

update() {
  info 'Updating Emmet language server...'
  npm_fast update -g --prefix /usr @olrtg/emmet-language-server
  info 'Emmet language server updated successfully.'
  exit 0
}

case "$1" in
  --uninstall) uninstall;;
  --update) update;;
  *) install;;
esac
