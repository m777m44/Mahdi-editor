set -e

source "$LOCAL/bin/utils"

install() {
  # بطاقة التقدّم: اسم الخادم ونسبته من 0 إلى 100 داخل التطبيق
  declare -f mahdi_prog_start >/dev/null 2>&1 && (mahdi_prog_start "ShellCheck (Bash)" "تجهيز الخادم…") || true
  if ! command_exists node || ! command_exists npm; then
    install_nodejs
  fi

  info 'Installing Bash language server...'
  npm_fast install -g --prefix /usr bash-language-server

  info 'Installing ShellCheck...'
  apt_fast shellcheck

  info 'Bash language server installed successfully.'
  exit 0
}

uninstall() {
  info 'Uninstalling Bash language server...'
  npm_fast uninstall -g --prefix /usr bash-language-server
  info 'Bash language server uninstalled successfully.'
  uninstall_nodejs
  exit 0
}

update() {
  info 'Updating Bash language server...'
  npm_fast update -g --prefix /usr bash-language-server
  info 'Bash language server updated successfully.'
  exit 0
}

case "$1" in
  --uninstall) uninstall;;
  --update) update;;
  *) install;;
esac

