set -e
file="$1"

source "$LOCAL/bin/utils"

# 3.1.0: شعار MAHDI في بداية كل تشغيل سكربت — داخليًا كان أو خارجيًا،
# حتى لو كان التشغيل غير تفاعلي (bash -lc …) كما طُلب.
if [ -f "$LOCAL/bin/banner" ]; then
  # shellcheck disable=SC1091
  MAHDI_BANNER_FORCE=1 source "$LOCAL/bin/banner"
fi

# التعرّف التلقائي على مكتبات السكربت وتثبيتها قبل التشغيل (Python/Node/PHP)
if [ -f "$LOCAL/bin/auto_deps" ]; then
  # shellcheck disable=SC1091
  source "$LOCAL/bin/auto_deps"
fi

# دالة بديلة إن لم يُنزَّل ملف auto_deps بعد (تثبيت قديم للتطبيق)
if ! declare -f mahdi_auto_deps >/dev/null 2>&1; then
  mahdi_auto_deps() { return 0; }
fi

# 5.5.0: شريط التقدّم أحادي السطر + محرّك «ثبّت المكتبة الناقصة وأعد التشغيل»
if [ -f "$LOCAL/bin/progress" ]; then
  # shellcheck disable=SC1091
  source "$LOCAL/bin/progress"
fi
if [ -f "$LOCAL/bin/self_heal" ]; then
  # shellcheck disable=SC1091
  source "$LOCAL/bin/self_heal"
fi
# بديل بسيط: تشغيل مباشر بلا إصلاح ذاتي (تثبيت قديم للتطبيق)
if ! declare -f mahdi_run_heal >/dev/null 2>&1; then
  mahdi_run_heal() {
    local _lang="$1"
    shift 2
    "$@"
  }
fi

if [ ! -f "$file" ]; then
  error "File not found -> $file"
  exit 1
fi

# ———————————————————— تقرير التشغيل (8.1.0) ————————————————————
# كان المستخدم لا يعرف: هل السكربت يعمل أم انتهى أم فشل؟ الطرفية تبقى ساكنة
# والشاشة لا تتحرّك (شكوى «يبقى واقف في شاشة ولا يسوي شي»). الآن:
#   ▸ اسم الملف        عند البدء
#   ✔ تمّ التشغيل — 4s  عند النجاح
#   ✖ توقّف السكربت (الرمز 1) — 4s  عند الفشل
# ثم تُترك للمستخدم طرفية حيّة بدل شاشة ميتة مكتوب عليها
# «[Process completed - press Enter]».
MAHDI_RUN_STARTED=$SECONDS

mahdi_run_footer() {
  local rc=$?
  local dur=$((SECONDS - MAHDI_RUN_STARTED))
  if [ "$rc" -eq 0 ]; then
    printf '\033[32m✔\033[0m تمّ التشغيل — %ss\n' "$dur" || true
  else
    printf '\033[31m✖\033[0m توقّف السكربت (الرمز %s) — %ss\n' "$rc" "$dur" || true
  fi

  # طرفية حيّة بعد الانتهاء: لا شاشة ميتة، ويستطيع المستخدم إعادة التشغيل بيده
  if [ "${MAHDI_KEEP_SHELL:-1}" = "1" ] && [ -t 0 ] && [ -t 1 ]; then
    printf '\033[2m  طرفية تفاعلية — اكتب exit للخروج\033[0m\n' || true
    exec /bin/bash -i || true
  fi
  return 0
}
trap mahdi_run_footer EXIT

printf '\033[36m▸\033[0m %s\n' "$(basename "$file")" || true

# ——————————————— 4.1.0: بيئة تشغيل موحّدة ———————————————
# كل المفسّرات (Python/Node/PHP/Ruby/…‎) تُشغَّل بترميز UTF-8 افتراضيًا، فلا تتعطّل السكربتات
# التي تحتوي نصًّا عربيًا أو إيموجي، ولا يظهر UnicodeEncodeError على طرفية أندرويد.
export LANG="${LANG:-C.UTF-8}"
export LC_ALL="${LC_ALL:-C.UTF-8}"
export LANGUAGE="${LANGUAGE:-C.UTF-8}"
export PYTHONIOENCODING=utf-8
export PYTHONUTF8=1
export PYTHONDONTWRITEBYTECODE=1
export NODE_NO_WARNINGS=1
export TERM="${TERM:-xterm-256color}"

# هل الملف تنفيذي حقيقي (ELF) ناتج عن ترجمة سابقة؟
# يعمل بلا أدوات خارجية (لا od ولا hexdump): أول أربعة بايتات هي 0x7F ثم "ELF".
is_native_binary() {
  [ -f "$1" ] || return 1
  [ "$(head -c 4 "$1" 2>/dev/null)" = $'\177ELF' ]
}

# 4.1.0: تشغيل أي سكربت عبر سطر shebang — مهما كان امتداده أو انعدامه.
# مثال: #!/usr/bin/env python3 أو #!/bin/bash أو #!/usr/bin/php
run_by_shebang() {
  local f="$1" line cmd interp
  line=$(head -n 1 "$f" 2>/dev/null || true)
  case "$line" in
    '#!'*) : ;;
    *) return 1 ;;
  esac

  cmd=${line#'#!'}
  cmd=${cmd# }
  # #!/usr/bin/env python3 → python3
  case "$cmd" in
    */env\ *) cmd=${cmd#*/env } ;;
  esac
  interp=${cmd%% *}
  interp=${interp##*/}
  [ -n "$interp" ] || return 1

  if ! command_exists "$interp"; then
    case "$interp" in
      python|python3) install_package "python3 python3-pip python3-venv" >/dev/null 2>&1 ;;
      node) install_nodejs >/dev/null 2>&1 ;;
      php) install_package "php-cli" >/dev/null 2>&1 ;;
      ruby) install_package "ruby-full" >/dev/null 2>&1 ;;
      perl) install_package "perl" >/dev/null 2>&1 ;;
      lua) install_package "lua5.3" >/dev/null 2>&1 ;;
      bash|sh|dash) : ;;
      *)
        warn "المفسّر المطلوب في سطر shebang غير متوفّر: $interp"
        return 2
        ;;
    esac
  fi

  if command_exists "$interp"; then
    info "التشغيل عبر $interp (من سطر shebang)"
    "$interp" "$f"
    return 0
  fi
  return 1
}

# 4.1.0: تخمين المفسّر من محتوى الملف — للسكربتات بامتداد غريب أو بلا امتداد.
guess_interpreter() {
  local f="$1"
  if grep -q '<\?php' "$f" 2>/dev/null; then
    echo php; return
  fi
  if grep -qE '^package main$' "$f" 2>/dev/null && grep -q 'func main' "$f" 2>/dev/null; then
    echo go; return
  fi
  if grep -q 'console\.log(' "$f" 2>/dev/null; then
    echo node; return
  fi
  if grep -qE '^(import |from |def |print\()' "$f" 2>/dev/null; then
    echo python3; return
  fi
  echo ""
}

# نسخة المجلد الرئيسي (مأخوذة من /sdcard لأنها للقراءة فقط)
case "$file" in
  /home/mahdi-run/*)
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
    mahdi_run_heal python "$file" python3 "$file"
    ;;

  *.js|*.mjs|*.cjs)
    if ! command_exists node; then
      install_nodejs
    fi
    mahdi_auto_deps "$file"
    mahdi_run_heal node "$file" node "$file"
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
    mahdi_run_heal php "$file" php "$file"
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

  # ——— لغات وُسِّعت في Mahdi-Editor 2.5.0 (كل اللغات قابلة للتشغيل) ———

  *.json)
    # التحقق من صحّة JSON وطباعته مرتّبًا
    python3 -c 'import json,sys; json.dump(json.load(open(sys.argv[1], encoding="utf-8")), sys.stdout, ensure_ascii=False, indent=2)' "$file" && echo
    ;;

  *.yaml|*.yml)
    mahdi_auto_deps "$file"
    python3 -c 'import sys,yaml; yaml.safe_load(open(sys.argv[1], encoding="utf-8")); print("✓ YAML صحيح")' "$file"
    ;;

  *.toml)
    python3 -c 'import sys,tomllib; tomllib.load(open(sys.argv[1],"rb")); print("✓ TOML صحيح")' "$file"
    ;;

  *.ini|*.cfg|*.conf)
    python3 -c 'import configparser,sys; c=configparser.ConfigParser(); c.read(sys.argv[1], encoding="utf-8"); print("✓ INI صحيح — أقسام:", ", ".join(c.sections()) or "—")' "$file"
    ;;

  *.xml|*.svg)
    if ! command_exists xmllint; then
      install_package "libxml2-utils"
    fi
    xmllint --noout "$file" && echo "✓ XML صحيح" || error "XML غير صالح"
    ;;

  *.md|*.markdown)
    mahdi_auto_deps "$file"
    if python3 -c "import markdown" 2>/dev/null; then
      out="${file%.*}.html"
      python3 -c 'import sys,markdown; open(sys.argv[2],"w",encoding="utf-8").write(markdown.markdown(open(sys.argv[1],encoding="utf-8").read(), extensions=["extra","toc"]))' "$file" "$out"
      success "تم تحويل Markdown إلى HTML: $out"
    else
      info "معاينة Markdown:"; sed -n '1,200p' "$file"
    fi
    ;;

  *.html|*.htm|*.xhtml)
    info "ملف HTML جاهز للمعاينة — استخدم «معاينة HTML» من التطبيق."
    python3 - "$file" <<'PYEOF'
import sys, html
src = open(sys.argv[1], encoding="utf-8", errors="ignore").read()
print("الحجم: %d حرفًا | عدد الوسوم: %d" % (len(src), src.count("<")))
PYEOF
    ;;

  *.css|*.scss|*.less)
    python3 - "$file" <<'PYEOF'
import sys, re
src = open(sys.argv[1], encoding="utf-8", errors="ignore").read()
rules = len(re.findall(r"\{", src))
selectors = len(re.findall(r"[^{}]+\{", src))
print("✓ تم تحليل الملف: %d قاعدة" % rules)
if src.count("{") != src.count("}"):
    print("⚠ الأقواس غير متوازنة: %d { و %d }" % (src.count("{"), src.count("}")))
else:
    print("✓ الأقواس متوازنة")
PYEOF
    ;;

  *.diff|*.patch)
    cat "$file"
    ;;

  *.tex)
    if ! command_exists pdflatex; then
      info "تثبيت LaTeX (قد يستغرق دقائق)…"
      install_package "texlive-latex-base"
    fi
    pdflatex -interaction=nonstopmode -halt-on-error "$file" | tail -20
    ;;

  *.cmake)
    if ! command_exists cmake; then
      install_package "cmake make"
    fi
    mkdir -p /tmp/mahdi-cmake && cd /tmp/mahdi-cmake
    cmake -DCMAKE_PROJECT_INCLUDE="$file" . 2>&1 | tail -20
    cd - >/dev/null
    ;;

  *.nix)
    if command_exists nix-instantiate; then
      nix-instantiate --eval --strict "$file"
    else
      info "Nix غير متوفّر في هذه التوزيعة — الملف صالح للتحرير والتنسيق فقط."
    fi
    ;;

  *.nim)
    if ! command_exists nim; then
      install_package "nim"
    fi
    nim c -r --hints:off "$file"
    ;;

  *.zig)
    if ! command_exists zig; then
      info "تنزيل Zig…"
      mahdi_fetch_fast "https://ziglang.org/download/0.13.0/zig-linux-x86_64-0.13.0.tar.xz" /tmp/zig.tar.xz || true
      if [ -s /tmp/zig.tar.xz ]; then
        mkdir -p /opt/zig && tar -xf /tmp/zig.tar.xz -C /opt/zig --strip-components=1 && ln -sf /opt/zig/zig /usr/local/bin/zig
      fi
    fi
    if command_exists zig; then zig run "$file"; else error "تعذّر تجهيز Zig"; fi
    ;;

  *.dart)
    if command_exists dart; then
      dart run "$file"
    else
      warn "Dart يحتاج SDK كاملًا (لا يتوفّر كحزمة نظام). الملف يعمل على جهاز فيه Dart SDK."
    fi
    ;;

  *.swift)
    if command_exists swift; then
      swift "$file"
    else
      warn "Swift يحتاج أداة swiftlang الرسمية. الملف صالح للتحرير الآن."
    fi
    ;;

  *.ps1)
    if command_exists pwsh; then
      pwsh -NoProfile -File "$file"
    else
      install_package "wget" >/dev/null 2>&1 || true
      warn "PowerShell يحتاج حزمة pwsh من مستودع مايكروسوفت. يمكن تثبيته يدويًا: apt install powershell"
    fi
    ;;

  *.bat|*.cmd)
    if command_exists wine; then
      wine cmd /c "$file"
    else
      warn "ملفات .bat تعمل على ويندوز فقط؛ لا يوجد محاكي wine في هذه التوزيعة."
    fi
    ;;

  *.asm|*.s)
    if ! command_exists gcc; then
      install_package "build-essential"
    fi
    gcc -x assembler "$file" -o temp.out && run_code ./temp.out
    ;;

  *.txt|*.log)
    cat "$file"
    ;;

  # ——— 4.1.0: حِزَم تشغيل جاهزة (zipapp/Java/…) ———
  *.pyz|*.zipapp)
    if ! command_exists python3; then
      install_package "python3"
    fi
    mahdi_auto_deps "$file"
    mahdi_run_heal python "$file" python3 "$file"
    ;;

  *.jar)
    if ! command_exists java; then
      install_package "default-jdk"
    fi
    java -jar "$file"
    ;;

  *.cgi)
    chmod +x "$file" 2>/dev/null || true
    set +e
    run_by_shebang "$file"
    cgi_status=$?
    set -e
    if [ "$cgi_status" -ne 0 ]; then
      bash "$file"
    fi
    ;;

  *)
    # ——— 4.1.0: «يشغّل كل شيء» — أي سكربت مهما كان امتداده أو انعدامه ———
    # 1) سطر shebang هو المرجع الأول (وهو ما تتبعه طرفية لينكس نفسها)
    set +e
    run_by_shebang "$file"
    shebang_status=$?
    set -e
    if [ "$shebang_status" -eq 0 ]; then
      exit 0
    fi

    # 2) ملف تنفيذي مترجم مسبقًا
    if is_native_binary "$file"; then
      chmod +x "$file" 2>/dev/null || true
      info "تشغيل ملف تنفيذي…"
      "$file"
      exit 0
    fi

    # 3) تخمين المفسّر من محتوى الملف
    guess=$(guess_interpreter "$file")
    if [ -n "$guess" ] && command_exists "$guess"; then
      info "التشغيل عبر $guess (تعرّف تلقائي على المحتوى)"
      "$guess" "$file"
      exit 0
    fi

    # 4) أخيرًا: bash يستطيع قراءة كثير من السكربتات البسيطة
    warn "امتداد غير معروف ولا يوجد سطر shebang — نحاول التشغيل بـ bash."
    bash "$file"
    ;;
esac
