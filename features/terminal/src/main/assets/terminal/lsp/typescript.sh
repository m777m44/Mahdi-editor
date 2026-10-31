set -e

source "$LOCAL/bin/utils"

install() {
  # بطاقة التقدّم: اسم الخادم ونسبته من 0 إلى 100 داخل التطبيق
  declare -f mahdi_prog_start >/dev/null 2>&1 && (mahdi_prog_start "TypeScript Language Server" "تجهيز الخادم…") || true
  if ! command_exists node || ! command_exists npm; then
    install_nodejs
  fi

  info "Installing TypeScript (including language server)..."
  npm_fast install -g --prefix /usr typescript@latest
  info 'TypeScript language server installed successfully.'
  exit 0
}

uninstall() {
  info "Uninstalling TypeScript (including language server)..."
  npm_fast uninstall -g --prefix /usr typescript typescript-language-server
  info 'TypeScript language server uninstalled successfully.'
  uninstall_nodejs
  exit 0
}

update() {
  info "Updating TypeScript (including language server)..."
  npm_fast install -g --prefix /usr typescript@latest
  info 'TypeScript language server updated successfully.'
  exit 0
}

case "$1" in
  --uninstall) uninstall;;
  --update) update;;
  *) install;;
esac
