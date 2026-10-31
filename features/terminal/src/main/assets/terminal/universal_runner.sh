set -e
file="$1"

source "$LOCAL/bin/utils"

# التعرّف التلقائي على مكتبات السكربت وتثبيتها قبل التشغيل (Python/Node/PHP)
if [ -f "$LOCAL/bin/auto_deps" ]; then
  # shellcheck disable=SC1091
  source "$LOCAL/bin/auto_deps"
fi

# دالة بديلة إن لم يُنزَّل ملف auto_deps بعد (تثبيت قديم للتطبيق)
if ! declare -f mahdi_auto_deps >/dev/null 2>&1; then
  mahdi_auto_deps() { return 0; }
fi

if [ ! -f "$file" ]; then
  error "File not found -> $file"
  exit 1
fi

# نسخة المجلد الرئيسي (مأخوذة من /sdcard لأنها للقراءة فقط)
case "$file" in
  /home/mahdi-run/*)
    info "نسخة قابلة للكتابة: $file"
    ;;
esac

run_code() {
    echo -e "\e[32;1m[✓]\e[37m Compilation successful! Running...\e[0m"
    chmod +x "$1" 2>/dev/null || true
    if [ -x "$1" ]; then
        "$1"
        rm "$1"
    else
        mv "$1" /tmp/a.out
        chmod +x /tmp/a.out
        /tmp/a.out
        rm /tmp/a.out
    fi
}

install_package() {
  local packages="$1"

  # If curl is requested, ensure ca-certificates is also installed
  if echo "$packages" | grep -qw curl; then
    if ! echo "$packages" | grep -qw ca-certificates; then
      packages="$packages ca-certificates"
    fi
  fi

  info "Installing $packages..."
  # بدون apt upgrade وبدون الحزم الاختيارية، وبمهلة قفل محدودة — لا انتظار للأبد
  export DEBIAN_FRONTEND=noninteractive
  if declare -f apt_fast >/dev/null 2>&1; then
    apt_fast $packages
    return $?
  fi
  apt-get -o DPkg::Lock::Timeout=60 -o Acquire::Retries=3 update -qq || true
  apt-get -o DPkg::Lock::Timeout=60 -o Acquire::Retries=3 install -y --no-install-recommends $packages </dev/null
}

install_nodejs() {
  info "Installing Node.js LTS..."
  install_package "curl"
  curl -fsSL https://deb.nodesource.com/setup_lts.x | bash -
  apt install -y nodejs
}

install_rust() {
  info "Installing Rust..."
  install_package "curl"
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
  source "$HOME/.cargo/env"
  # Add to PATH for current session
  export PATH="$HOME/.cargo/bin:$PATH"
}

install_dotnet() {
  info "Installing .NET SDK..."
  if declare -f mahdi_fetch_fast >/dev/null 2>&1; then
    mahdi_fetch_fast https://packages.microsoft.com/config/ubuntu/22.04/packages-microsoft-prod.deb packages-microsoft-prod.deb
  else
    wget https://packages.microsoft.com/config/ubuntu/22.04/packages-microsoft-prod.deb -O packages-microsoft-prod.deb
  fi
  dpkg -i packages-microsoft-prod.deb
  rm packages-microsoft-prod.deb
  apt update -qq
  apt install -y dotnet-sdk-8.0
}

install_kotlin() {
    install_package "unzip curl"
    info "Fetching latest Kotlin compiler..."
    url=$(curl -s https://api.github.com/repos/JetBrains/kotlin/releases/latest \
        | grep "browser_download_url" \
        | grep "kotlin-compiler-.*zip" \
        | grep -v ".sha256" \
        | cut -d '"' -f 4)

    if [ -z "$url" ]; then
        error "Could not find Kotlin compiler download URL."
        return 1
    fi

    info "Downloading from: $url"
    if declare -f mahdi_fetch_fast >/dev/null 2>&1; then
        mahdi_fetch_fast "$url" /tmp/kotlin.zip
    elif declare -f mahdi_fetch >/dev/null 2>&1; then
        mahdi_fetch "$url" /tmp/kotlin.zip
    else
        curl -fL --retry 5 -C - -o /tmp/kotlin.zip "$url"
    fi

    info "Extracting to /opt/kotlinc ..."
    mkdir -p /opt/kotlinc
    unzip -qo /tmp/kotlin.zip -d /opt

    ln -sf /opt/kotlinc/bin/kotlinc /usr/local/bin/kotlinc
    ln -sf /opt/kotlinc/bin/kotlin /usr/local/bin/kotlin

    info "Kotlin installed at /opt/kotlinc"
}

case "$file" in
  *.py)
    if ! command_exists python3; then
      install_package "python3 python3-pip python3-venv"
    fi
    # 1) يكتشف كل import في الملف + أوامر pip المكتوبة داخله، 2) يثبّت الناقص بآخر إصدار، 3) يشغّل
    mahdi_auto_deps "$file"
    python3 "$file"
    ;;

  *.js|*.mjs|*.cjs)
    if ! command_exists node; then
      install_nodejs
    fi
    mahdi_auto_deps "$file"
    node "$file"
    ;;

  *.ts|*.tsx)
    if ! command_exists node; then
      install_nodejs
    fi

    mahdi_auto_deps "$file"

    if ! command_exists tsc; then
      info "Installing TypeScript compiler..."
      npm install -g typescript
    fi

    tempdir="/tmp/runner-ts"
    rm -rf "$tempdir"
    mkdir -p "$tempdir"

    filename="$(basename "$file" .ts)"

    # Compile with correct working directory
    tsc "$file" --outDir "$tempdir"

    node "$tempdir/$filename.js"

    rm -rf "$tempdir"
    ;;


  *.java)
    if ! command_exists java; then
      install_package "default-jdk"
    fi

    java "$file"
    ;;


  *.kt)
    if ! command_exists java; then
      info "Installing Java..."
      install_package "default-jdk"
    fi
    if ! command_exists kotlinc; then
      info "Installing Kotlin..."
      install_kotlin
    fi
    kotlinc "$file" -include-runtime -d temp.jar && java -jar temp.jar
    rm -f temp.jar
    ;;

  *.rs)
    if ! command_exists rustc; then
      install_rust
    fi
    rustc "$file" -o temp.out
    run_code ./temp.out
    ;;

  *.rb)
    if ! command_exists ruby; then
      install_package "ruby-full"
    fi
    ruby "$file"
    ;;

  *.php)
    if ! command_exists php; then
      install_package "php-cli"
    fi
    mahdi_auto_deps "$file"
    php "$file"
    ;;

  *.c)
    if ! command_exists gcc; then
      install_package "build-essential"
    fi
    gcc "$file" -o temp.out
    run_code ./temp.out
    ;;

  *.cpp|*.cc|*.cxx)
    if ! command_exists g++; then
      install_package "build-essential"
    fi
    g++ "$file" -o temp.out
    run_code ./temp.out
    ;;

  *.cs)
    if ! command_exists dotnet; then
      install_dotnet
    fi
    # Create a temporary project
    mkdir -p temp_cs_project
    cd temp_cs_project
    export DOTNET_GCHeapHardLimit=1C0000000
    dotnet new console --force
    cp "../$file" Program.cs
    dotnet run
    cd ..
    rm -rf temp_cs_project
    ;;

  *.sh|*.bash)
    chmod +x "$file" 2>/dev/null || true
    bash "$file"
    ;;

  *.zsh)
    if ! command_exists zsh; then
      install_package "zsh"
    fi
    chmod +x "$file" 2>/dev/null || true
    zsh "$file"
    ;;

  *.fish)
    if ! command_exists fish; then
      install_package "fish"
    fi
    chmod +x "$file" 2>/dev/null || true
    fish "$file"
    ;;

  *.pl)
    if ! command_exists perl; then
      install_package "perl"
    fi
    perl "$file"
    ;;

  *.lua)
    if ! command_exists lua; then
      install_package "lua5.3"
    fi
    lua "$file"
    ;;

  *.r|*.R)
    if ! command_exists Rscript; then
      install_package "r-base"
    fi
    Rscript "$file"
    ;;

  *.f90|*.f95|*.f03|*.f08)
    if ! command_exists gfortran; then
      install_package "gfortran"
    fi
    gfortran "$file" -o temp.out
    run_code ./temp.out
    ;;

  *.pas)
    if ! command_exists fpc; then
      install_package "fpc"
    fi
    fpc "$file" && "./$(basename "$file" .pas)"
    rm -f "$(basename "$file" .pas)" *.o
    ;;

  *.tcl)
    if ! command_exists tclsh; then
      install_package "tcl"
    fi
    tclsh "$file"
    ;;

  *.elm)
    if ! command_exists elm; then
      info "Installing Elm..."
      if ! command_exists node; then
        install_nodejs
      fi
      npm install -g elm
    fi
    elm make "$file" --output=temp.html
    info "Elm compiled to temp.html - transfer to browser to view"
    ;;

  *.fsx|*.fs)
    if ! command_exists dotnet; then
      install_dotnet
    fi
    dotnet fsi "$file"
    ;;

  # ——— لغات أُضيفت في Mahdi-Editor 2.0 ———
  *.go)
    if ! command_exists go; then
      install_package "golang-go"
    fi
    go run "$file"
    ;;

  *.jl)
    if ! command_exists julia; then
      install_package "julia"
    fi
    julia "$file"
    ;;

  *.groovy)
    if ! command_exists groovy; then
      install_package "groovy"
    fi
    groovy "$file"
    ;;

  *.scala)
    if ! command_exists scala; then
      install_package "scala"
    fi
    scala "$file"
    ;;

  *.ml|*.mli)
    if ! command_exists ocaml; then
      install_package "ocaml"
    fi
    ocaml "$file"
    ;;

  *.ex|*.exs)
    if ! command_exists elixir; then
      install_package "elixir"
    fi
    elixir "$file"
    ;;

  *.erl)
    if ! command_exists escript; then
      install_package "erlang"
    fi
    escript "$file"
    ;;

  *.hs)
    if ! command_exists runghc; then
      install_package "ghc"
    fi
    runghc "$file"
    ;;

  *.sql)
    if ! command_exists sqlite3; then
      install_package "sqlite3"
    fi
    info "تشغيل ملف SQL على قاعدة بيانات مؤقتة…"
    sqlite3 /tmp/mahdi_sql_run.db < "$file"
    ;;

  *.awk)
    if ! command_exists awk; then
      install_package "gawk"
    fi
    awk -f "$file"
    ;;

  *.vb|*.vbs)
    if ! command_exists dotnet; then
      install_dotnet
    fi
    info "ملفات Visual Basic تحتاج مشروعًا كاملًا؛ استخدم مشروعًا بامتداد .vbproj"
    ;;
esac
