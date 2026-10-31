set -e

source "$LOCAL/bin/utils"

install() {
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
