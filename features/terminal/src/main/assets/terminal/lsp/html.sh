set -e

source "$LOCAL/bin/utils"

install() {
  # بطاقة التقدّم: اسم الخادم ونسبته من 0 إلى 100 داخل التطبيق
  declare -f mahdi_prog_start >/dev/null 2>&1 && (mahdi_prog_start "HTML Language Server" "تجهيز الخادم…") || true
  if ! command_exists node || ! command_exists npm; then
    install_nodejs
  fi

  info 'Installing extracted VSCode language servers...'
  npm_fast install -g --prefix /usr vscode-langservers-extracted
  info 'HTML language server installed successfully.'
  exit 0
}

uninstall() {
  if ask "Are you sure you want to uninstall the extracted VSCode language servers? This will also remove the HTML, CSS and JSON language servers."; then
    info 'Uninstalling extracted VSCode language servers...'
    npm_fast uninstall -g --prefix /usr vscode-langservers-extracted
    info 'Extracted VSCode language servers uninstalled successfully.'
    uninstall_nodejs
    exit 0
  fi
}

update() {
  info 'Updating extracted VSCode language servers...'
  npm_fast update -g --prefix /usr vscode-langservers-extracted
  info 'Extracted VSCode language servers updated successfully.'
  exit 0
}

case "$1" in
  --uninstall) uninstall;;
  --update) update;;
  *) install;;
esac
