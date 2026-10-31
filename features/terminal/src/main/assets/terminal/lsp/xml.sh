set -e

source "$LOCAL/bin/utils"

LLEMINX_VERSION="0.31.0"
INSTALL_DIR="$HOME/.lsp/lemminx"

install() {
  info 'Installing LemMinX language server...'

  mkdir -p "$INSTALL_DIR"
  cd "$INSTALL_DIR"
  apt_fast curl ca-certificates default-jdk
  fetch_faster "https://download.eclipse.org/staging/2025-09/plugins/org.eclipse.lemminx.uber-jar_${LLEMINX_VERSION}.jar" "server.jar"
  echo "$LLEMINX_VERSION" > version.txt
  info 'LemMinX language server installed successfully.'
  exit 0
}

uninstall() {
  info 'Uninstalling LemMinX language server...'
  rm -rf "$INSTALL_DIR"
  info 'LemMinX language server uninstalled successfully.'

  if ask "Do you want to uninstall OpenJDK? It was installed as a dependency of this language server."; then
    info "Uninstalling OpenJDK..."
    apt-get -o DPkg::Lock::Timeout=60 remove -y default-jdk
    apt-get -o DPkg::Lock::Timeout=60 autoremove -y
    info "OpenJDK uninstalled successfully."
  fi
  exit 0
}

update() {
  info 'Updating LemMinX language server...'
  cd "$INSTALL_DIR"
  rm -f "server.jar"
  fetch_faster "https://download.eclipse.org/staging/2025-09/plugins/org.eclipse.lemminx.uber-jar_${LLEMINX_VERSION}.jar" "server.jar"
  echo "$LLEMINX_VERSION" > version.txt
  info 'LemMinX language server updated successfully.'
  exit 0
}

case "$1" in
  --uninstall) uninstall;;
  --update) update;;
  *) install;;
esac