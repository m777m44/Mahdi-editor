set -e

source "$LOCAL/bin/utils"

info 'Installing Pyright (Python language server)...'

install() {
  if ! command_exists node || ! command_exists npm; then
    install_nodejs
  fi

  info 'Installing Pyright language server...'
  npm_fast install -g --prefix /usr pyright

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
