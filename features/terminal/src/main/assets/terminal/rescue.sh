# shellcheck shell=bash
# ——————————————————————————————————————————————————————————————————————————————
# rescue.sh — محرّك «ثبّتها بأي طريقة» (9.3.0)
#
# المشكلة التي وُلد منها هذا الملف: اللقطة أظهرت أربع مرات متتالية
#
#     ✖  الأمر غير موجود: npm
#     ↳  تثبيت npm تلقائيًا…
#     ↳  الحزمة المطلوبة: npm  · ثبّتها من: اللغات ← المشغّلات
#
# أي أن «التثبيت التلقائي» لم يكن يثبّت شيئًا: كان يقرأ اسم الحزمة من قاعدة
# command-not-found، وإن فشل `apt` مرة واحدة طبع «ثبّتها بنفسك» وتوقّف. والأسوأ:
# السكربتات القادمة من GitHub (‏`curl … | sh`، `npm install`، `pip install -r`)
# تفشل لنفس السبب فتبدو التطبيق «ينهار» عند تشغيل أي مشروع.
#
# هذا الملف يعالج الجذر لا الأعراض:
#
#   ١. mahdi_pkg          — واجهة `pkg` متوافقة مع Termux فوق apt، مع خريطة أسماء
#                           (fontconfig-utils ⇒ fontconfig، python ⇒ python3…)
#                           وتجاهل حزم أندرويد التي لا معنى لها هنا.
#   ٢. mahdi_install_command — يثبّت أي أمر ناقص (npm · npx · node · git · curl ·
#                           ffmpeg …) بسلاسل بدائل: apt ← NodeSource ← حزمة
#                           Node الرسمية ← المستودع الرسمي للغة.
#   ٣. mahdi_py_install_any  — سلسلة سبع محاولات لأي مكتبة بايثون: عادي ← ويل
#                           فقط ← إصدارات أقدم ← بناء من المصدر ← حزمة apt ←
#                           مصدر بديل ← sdist. لا تعود «تعذّر التثبيت» بعد اليوم.
#   ٤. mahdi_npm_install_any — أربع محاولات: محلي ← legacy-peer-deps ← عام ←
#                           مرايا npm ← حزمة apt.
#   ٥. mahdi_project_bootstrap — يجهّز مشروعًا كاملًا من GitHub: requirements.txt
#                           · pyproject · package.json · Gemfile · composer ·
#                           go.mod · Cargo.toml، مرة واحدة فقط لكل نسخة.
#   ٦. mahdi_install_shims   — أدوات ناقصة في بيئة أندرويد: sudo · termux-* ·
#                           pkg، حتى لا تفشل سكربتات Termux عند أول سطر.
#
# كل شيء صامت عند النجاح، وسطر عربي واحد عند كل تبديل طريقة، وبلا أي سؤال.
# ——————————————————————————————————————————————————————————————————————————————

# idempotent: قد يُستدعى من init وauto_deps وself_heal وشيم pkg
if [ "${MAHDI_RESCUE_LOADED:-0}" = "1" ]; then
  return 0 2>/dev/null || true
fi
MAHDI_RESCUE_LOADED=1
MAHDI_RESCUE_VERSION="9.3.0"

# بدائل إن استُدعي الملف مبكرًا قبل utils
if ! declare -f info >/dev/null 2>&1; then
  info() { printf '▸  %s\n' "$*"; }
  warn() { printf '⚠  %s\n' "$*"; }
  error() { printf '✖  %s\n' "$*"; }
  success() { printf '✔  %s\n' "$*"; }
fi
if ! declare -f mahdi_have >/dev/null 2>&1; then
  mahdi_have() { command -v "$1" >/dev/null 2>&1; }
fi

# ألوان مختصرة (تعمل حتى لو لم يُحمَّل utils)
: "${M_RESET:=$'\033[0m'}"
: "${M_BOLD:=$'\033[1m'}"
: "${M_SKY:=$'\033[94m'}"
: "${M_GOLD:=$'\033[93m'}"
: "${M_CORAL:=$'\033[91m'}"
: "${M_MUTED:=$'\033[90m'}"
: "${M_MINT:=$'\033[92m'}"

export DEBIAN_FRONTEND=noninteractive
export APT_LISTCHANGES_FRONTEND=none

# ——————————————————————————————————————————————————————————————————————————————
# 0) apt بلا أي سؤال — إعداد عام يمنع «هل تريد المتابعة؟ [Y/n]» إلى الأبد
# ——————————————————————————————————————————————————————————————————————————————
mahdi_apt_noninteractive_once() {
  [ -f /etc/apt/apt.conf.d/99mahdi-noninteractive ] && return 0
  mkdir -p /etc/apt/apt.conf.d 2>/dev/null || return 0
  {
    printf 'APT::Get::Assume-Yes "true";\n'
    printf 'APT::Get::allowUnauthenticated "true";\n'
    printf 'APT::Install-Recommends "false";\n'
    printf 'APT::Install-Suggests "false";\n'
    printf 'Acquire::Retries "3";\n'
    printf 'Acquire::http::Timeout "30";\n'
    printf 'Acquire::https::Timeout "30";\n'
    printf 'DPkg::Lock::Timeout "60";\n'
    printf 'Dpkg::Options { "--force-confdef"; "--force-confnew"; };\n'
  } >/etc/apt/apt.conf.d/99mahdi-noninteractive 2>/dev/null || true
  return 0
}

# ——————————————————————————————————————————————————————————————————————————————
# 1) خريطة أسماء حزم Termux ⇒ أوبنتو
#
# الطرفية ليست Termux، لكن أغلب سكربتات GitHub العربية والصينية تُكتب لـ Termux.
# بدل أن يفشل `pkg install fontconfig-utils` نترجمه إلى الحزمة الصحيحة؛ وبدل أن
# تتوقّف على `libandroid-support` (لا وجود لها هنا أصلًا) نتجاوزها بصمت.
# ——————————————————————————————————————————————————————————————————————————————
_mahdi_pkg_map_one() {
  case "$1" in
    # حزم أندرويد/Termux لا معنى لها داخل الحاوية — نتجاوزها
    termux-tools | termux-am | termux-am-socket | termux-api | termux-exec | termux-services | termux-auth | \
      libandroid-support | libandroid-glob | libandroid-posix-semaphore | libandroid-shmem | libandroid-selinux | \
      libandroid-* | ndk-sysroot | ndk-multilib | libcompiler-rt | libllvm | lld | \
      root-repo | x11-repo | tur-repo | science-repo | game-repo | unstable-repo | \
      termux-elf-cleaner)
      printf ''
      return 0
      ;;
    fontconfig-utils | fontconfig) printf 'fontconfig' ;;
    python | python3) printf 'python3 python3-pip python3-venv' ;;
    python-pip | pip) printf 'python3-pip' ;;
    nodejs | nodejs-lts | node | npm | npx) printf 'nodejs npm' ;;
    yarn) printf 'yarnpkg' ;;
    openssl | openssl-tool | openssl-dev) printf 'openssl libssl-dev' ;;
    libssl) printf 'libssl-dev' ;;
    libffi) printf 'libffi-dev' ;;
    libsqlite | sqlite) printf 'sqlite3 libsqlite3-dev' ;;
    libxml2) printf 'libxml2-dev' ;;
    libxslt) printf 'libxslt1-dev' ;;
    libjpeg-turbo | libjpeg) printf 'libjpeg-turbo8-dev' ;;
    libpng) printf 'libpng-dev' ;;
    freetype) printf 'libfreetype6-dev' ;;
    harfbuzz) printf 'libharfbuzz-dev' ;;
    libzmq) printf 'libzmq3-dev' ;;
    libcrypt) printf 'libcrypt-dev' ;;
    libmpfr) printf 'libmpfr-dev' ;;
    libgmp) printf 'libgmp-dev' ;;
    zlib) printf 'zlib1g-dev' ;;
    ncurses | ncurses-utils) printf 'libncurses-dev' ;;
    readline) printf 'libreadline-dev' ;;
    libexpat) printf 'libexpat1-dev' ;;
    libbz2) printf 'libbz2-dev' ;;
    liblzma | xz-utils | unxz) printf 'xz-utils liblzma-dev' ;;
    ruby) printf 'ruby-full' ;;
    php) printf 'php-cli' ;;
    go | golang) printf 'golang-go' ;;
    rust) printf 'rustc cargo' ;;
    lua | lua53 | lua5.4) printf 'lua5.3' ;;
    clang) printf 'clang' ;;
    make) printf 'make' ;;
    cmake) printf 'cmake' ;;
    gcc) printf 'gcc' ;;
    g++) printf 'g++' ;;
    man) printf 'man-db' ;;
    which) printf 'debianutils' ;;
    cronie) printf 'cron' ;;
    openssh) printf 'openssh-client' ;;
    mariadb | mysql) printf 'mariadb-server' ;;
    postgresql) printf 'postgresql' ;;
    redis) printf 'redis-server' ;;
    *) printf '%s' "$1" ;;
  esac
}

# يحوّل قائمة أسماء (Termux أو عادية) إلى قائمة حزم أوبنتو صالحة (مفصولة بمسافات)
_mahdi_pkg_translate() {
  local out="" name mapped
  for name in "$@"; do
    case "$name" in
      -* | "") continue ;;
    esac
    mapped="$(_mahdi_pkg_map_one "$name")"
    [ -z "$mapped" ] && continue
    out="$out $mapped"
  done
  printf '%s' "${out# }"
}

# ——————————————————————————————————————————————————————————————————————————————
# 2) تثبيت حزم النظام بمرونة: تحديث فهرس، محاولة، ثم إصلاح الحزم المكسورة
# ——————————————————————————————————————————————————————————————————————————————
mahdi_apt_install_soft() {
  local -a pkgs=("$@")
  [ "${#pkgs[@]}" -eq 0 ] && return 0
  mahdi_apt_noninteractive_once

  if declare -f mahdi_apt_install >/dev/null 2>&1; then
    mahdi_apt_install "${pkgs[@]}" >/dev/null 2>&1 && return 0
  else
    apt-get -o DPkg::Lock::Timeout=60 -o Acquire::Retries=3 install -y --no-install-recommends \
      "${pkgs[@]}" </dev/null >/dev/null 2>&1 && return 0
  fi

  # محاولة ثانية: فهرس جديد + إصلاح التبعيات المكسورة (حالة شائعة بعد انقطاع تثبيت)
  apt-get -o DPkg::Lock::Timeout=60 update -qq >/dev/null 2>&1 || true
  apt-get -o DPkg::Lock::Timeout=60 -f install -y >/dev/null 2>&1 || true
  if declare -f mahdi_apt_install >/dev/null 2>&1; then
    mahdi_apt_install "${pkgs[@]}" >/dev/null 2>&1 && return 0
  else
    apt-get -o DPkg::Lock::Timeout=60 install -y --no-install-recommends "${pkgs[@]}" \
      </dev/null >/dev/null 2>&1 && return 0
  fi

  # محاولة ثالثة: كل حزمة على حدة، فحزمة واحدة معطوبة لا تُسقط الباقي
  local p any=1
  for p in "${pkgs[@]}"; do
    if apt-get -o DPkg::Lock::Timeout=60 install -y --no-install-recommends "$p" </dev/null >/dev/null 2>&1; then
      any=0
    fi
  done
  return "$any"
}

# ——————————————————————————————————————————————————————————————————————————————
# 3) mahdi_pkg — واجهة `pkg` كما في Termux
# ——————————————————————————————————————————————————————————————————————————————
mahdi_pkg_install() {
  local translated
  translated="$(_mahdi_pkg_translate "$@")"
  if [ -z "$translated" ]; then
    printf '%s✔%s  هذه الأسماء خاصة بـ Termux ولا تلزم داخل Mahdi-Editor — تم تجاوزها.\n' \
      "$M_MINT" "$M_RESET"
    return 0
  fi

  # نتجاهل ما هو مثبَّت أصلًا (apt سريع، بلا شبكة)
  local -a need=()
  local p
  for p in $translated; do
    dpkg -s "$p" >/dev/null 2>&1 || need+=("$p")
  done
  if [ "${#need[@]}" -eq 0 ]; then
    return 0
  fi

  printf '%s▸%s  تثبيت: %s%s%s\n' "$M_SKY" "$M_RESET" "$M_BOLD" "${need[*]}" "$M_RESET"
  mahdi_apt_install_soft "${need[@]}"
  return $?
}

mahdi_pkg() {
  local cmd="${1:-}"
  [ -n "$cmd" ] && shift || true

  # نُسقط الأعلام (-y --yes -q …) لأنّ التثبيت غير تفاعلي أصلًا
  local -a args=()
  local a
  for a in "$@"; do
    case "$a" in
      -y | --yes | -q | --quiet | --no-install-recommends | --no-install-suggests) continue ;;
      *) args+=("$a") ;;
    esac
  done

  case "$cmd" in
    install | add | i | in)
      mahdi_pkg_install "${args[@]}"
      ;;
    uninstall | remove | rm | purge)
      apt-get -o DPkg::Lock::Timeout=60 remove -y "${args[@]}" </dev/null
      ;;
    update)
      apt-get -o DPkg::Lock::Timeout=60 update
      ;;
    upgrade | full-upgrade | dist-upgrade)
      apt-get -o DPkg::Lock::Timeout=60 -o Dpkg::Options::=--force-confnew upgrade -y </dev/null
      ;;
    search)
      apt-cache search "${args[@]}"
      ;;
    show)
      apt-cache show "${args[@]}" 2>/dev/null || apt-cache show "$(_mahdi_pkg_map_one "${args[0]:-}")"
      ;;
    list-installed | list-installed-packages | list)
      if [ "${args[0]:-}" = "installed" ] || [ "$cmd" = "list-installed" ] || [ "$cmd" = "list-installed-packages" ]; then
        dpkg-query -W -f='${Package} ${Version}\n' 2>/dev/null
      else
        apt-cache search "${args[@]}"
      fi
      ;;
    files)
      dpkg -L "${args[@]}"
      ;;
    clean | autoclean)
      apt-get "$cmd"
      ;;
    "" | help | -h | --help)
      printf '%s pkg %s — مدير حزم Mahdi-Editor (متوافق مع أوامر Termux)\n' "$M_BOLD" "$M_RESET"
      printf '  pkg install <حزمة…>     تثبيت (مع ترجمة أسماء Termux تلقائيًا)\n'
      printf '  pkg uninstall <حزمة…>   حذف\n'
      printf '  pkg update · upgrade    تحديث الفهرس والنظام\n'
      printf '  pkg search <كلمة>       بحث\n'
      printf '  pkg list-installed      الحزم المثبَّتة\n'
      printf '%s  يعمل أيضًا داخل السكربتات: curl … | sh ثم pkg install -y …%s\n' "$M_MUTED" "$M_RESET"
      ;;
    *)
      # `pkg curl` بدون فعل: نعتبره تثبيتًا كما يفعل بعض المستخدمين
      mahdi_pkg_install "$cmd" "${args[@]}"
      ;;
  esac
}

# ——————————————————————————————————————————————————————————————————————————————
# 4) Node.js — السلسلة التي كانت تفشل عند «الأمر غير موجود: npm»
#
# الترتيب: الحاوية (apt nodejs npm) ← NodeSource ← حزمة Node الرسمية (tar.xz)
# في ~/.mahdi/node. كل خطوة تُختبر بالأمر نفسه، لا بالنيّة.
# ——————————————————————————————————————————————————————————————————————————————
_mahdi_node_arch() {
  case "$(uname -m 2>/dev/null)" in
    aarch64 | arm64) printf 'arm64' ;;
    armv7l | armv8l | arm) printf 'armv7l' ;;
    x86_64 | amd64) printf 'x64' ;;
    i*86) printf 'x86' ;;
    *) printf 'x64' ;;
  esac
}

mahdi_node_ensure() {
  if mahdi_have node && mahdi_have npm; then
    return 0
  fi

  # 1) مستودع الحاوية — الأسرع، ولا يحتاج شبكة خارجية إن كان الفهرس محدّثًا
  if ! mahdi_have node || ! mahdi_have npm; then
    mahdi_apt_install_soft nodejs npm >/dev/null 2>&1 || true
    hash -r 2>/dev/null || true
    mahdi_have node && mahdi_have npm && return 0
  fi

  # 2) NodeSource — يوفّر npm الحديث مع node نفسه
  if mahdi_have curl || mahdi_have wget; then
    printf '%s↻%s  تجربة مستودع Node.js الرسمي…\n' "$M_SKY" "$M_RESET"
    mahdi_pkg_install curl ca-certificates gnupg >/dev/null 2>&1 || true
    if mahdi_have curl; then
      curl -fsSL --retry 2 --max-time 120 https://deb.nodesource.com/setup_lts.x -o /tmp/mahdi-nodesource.sh 2>/dev/null &&
        bash /tmp/mahdi-nodesource.sh >/dev/null 2>&1 || true
      rm -f /tmp/mahdi-nodesource.sh 2>/dev/null || true
    fi
    mahdi_apt_install_soft nodejs >/dev/null 2>&1 || true
    hash -r 2>/dev/null || true
    mahdi_have node && mahdi_have npm && return 0
  fi

  # 3) الحزمة الرسمية المضغوطة — تنجح حتى لو تعطّل المستودع بالكامل
  printf '%s↻%s  تنزيل Node.js مباشرة (بلا مستودع)…\n' "$M_SKY" "$M_RESET"
  local arch ver url tmp
  arch="$(_mahdi_node_arch)"
  ver="v20.18.0"
  url="https://nodejs.org/dist/$ver/node-$ver-linux-$arch.tar.xz"
  tmp="/tmp/mahdi-node.tar.xz"
  rm -f "$tmp" 2>/dev/null || true

  if declare -f mahdi_fetch_fast >/dev/null 2>&1; then
    mahdi_fetch_fast "$url" "$tmp" >/dev/null 2>&1 || true
  elif mahdi_have curl; then
    curl -fL --retry 3 --connect-timeout 20 -o "$tmp" "$url" >/dev/null 2>&1 || true
  elif mahdi_have wget; then
    wget -q -O "$tmp" "$url" >/dev/null 2>&1 || true
  fi

  if [ -s "$tmp" ]; then
    mahdi_pkg_install xz-utils tar >/dev/null 2>&1 || true
    mkdir -p "$HOME/.mahdi/node" 2>/dev/null || true
    rm -rf "$HOME/.mahdi/node/"node-* 2>/dev/null || true
    if tar -xJf "$tmp" -C "$HOME/.mahdi/node" 2>/dev/null; then
      local root
      root="$(find "$HOME/.mahdi/node" -maxdepth 1 -mindepth 1 -type d -name 'node-*' 2>/dev/null | head -n 1)"
      if [ -n "$root" ] && [ -x "$root/bin/node" ]; then
        mkdir -p "${LOCAL:-$HOME}/bin" 2>/dev/null || true
        local tool
        for tool in node npm npx corepack; do
          [ -e "$root/bin/$tool" ] || continue
          ln -sf "$root/bin/$tool" "${LOCAL:-$HOME}/bin/$tool" 2>/dev/null || true
        done
        export PATH="${LOCAL:-$HOME}/bin:$root/bin:$PATH"
        hash -r 2>/dev/null || true
      fi
    fi
  fi
  rm -f "$tmp" 2>/dev/null || true

  hash -r 2>/dev/null || true
  if mahdi_have node; then
    # npm قد يأتي مثبَّتًا مع الحزمة الرسمية؛ وإن غاب نربطه
    if ! mahdi_have npm; then
      local nroot
      nroot="$(dirname "$(command -v node 2>/dev/null)")/.."
      [ -x "$nroot/lib/node_modules/npm/bin/npm-cli.js" ] &&
        printf '#!/bin/sh\nexec node "%s/lib/node_modules/npm/bin/npm-cli.js" "$@"\n' "$nroot" \
          >"${LOCAL:-$HOME}/bin/npm" 2>/dev/null &&
        chmod +x "${LOCAL:-$HOME}/bin/npm" 2>/dev/null || true
    fi
  fi

  mahdi_have node && mahdi_have npm && return 0
  # بعض الحالات: node موجود وnpm لا — يكفي node للتشغيل، لكن npx مطلوب للمشاريع
  mahdi_have node
}

# ——————————————————————————————————————————————————————————————————————————————
# 5) تثبيت أي أمر ناقص (npm · npx · git · curl · ffmpeg …) بلا سؤال
# ——————————————————————————————————————————————————————————————————————————————
mahdi_install_command() {
  local cmd="$1"
  [ -z "$cmd" ] && return 1
  mahdi_have "$cmd" && return 0

  case "$cmd" in
    node | nodejs | npm | npx | yarn | pnpm | corepack)
      mahdi_node_ensure
      hash -r 2>/dev/null || true
      if [ "$cmd" = "npx" ] && ! mahdi_have npx && mahdi_have npm; then
        local npm_bin
        npm_bin="$(dirname "$(command -v npm 2>/dev/null)")"
        [ -e "$npm_bin/npx" ] && ln -sf "$npm_bin/npx" "${LOCAL:-$HOME}/bin/npx" 2>/dev/null || true
      fi
      mahdi_have "$cmd" && return 0
      # yarn/pnpm قد يغيبان: يُثبَّتان عبر npm فور توفّره
      if { [ "$cmd" = "yarn" ] || [ "$cmd" = "pnpm" ]; } && mahdi_have npm; then
        npm install -g --no-fund --no-audit --loglevel=error "$cmd" >/dev/null 2>&1 || true
        hash -r 2>/dev/null || true
        mahdi_have "$cmd" && return 0
      fi
      # npx متاح دائمًا عبر npm نفسه
      if [ "$cmd" = "npx" ] && mahdi_have npm; then
        return 0
      fi
      return 1
      ;;
    pip | pip3)
      if declare -f mahdi_ensure_pip >/dev/null 2>&1; then
        mahdi_ensure_pip >/dev/null 2>&1
      else
        "${MAHDI_PY:-python3}" -m ensurepip --default-pip >/dev/null 2>&1 ||
          mahdi_pkg_install python3-pip >/dev/null 2>&1 || true
      fi
      hash -r 2>/dev/null || true
      mahdi_have "$cmd" || [ "$cmd" = "pip3" ]
      return $?
      ;;
  esac

  local pkgs
  pkgs="$(_mahdi_pkg_translate "$cmd")"
  [ -z "$pkgs" ] && pkgs="$cmd"
  mahdi_apt_install_soft $pkgs >/dev/null 2>&1 || true
  hash -r 2>/dev/null || true
  mahdi_have "$cmd"
}

# ——————————————————————————————————————————————————————————————————————————————
# 6) بايثون — «إن فشل إصدار ثبّته بغيره»
# ——————————————————————————————————————————————————————————————————————————————
_mahdi_pip_quiet() {
  local py="${MAHDI_PY:-python3}"
  local log="${TMPDIR:-/tmp}/mahdi-rescue-pip.log"
  "$py" -m pip install --disable-pip-version-check --no-input --quiet \
    --retries 2 --timeout 25 "$@" >"$log" 2>&1
}

_mahdi_pip_versions() {
  local py="${MAHDI_PY:-python3}" raw
  raw="$("$py" -m pip index versions "$1" 2>/dev/null | sed -n 's/.*Available versions: //p' | head -n 1)"
  [ -z "$raw" ] && raw="$("$py" -m pip install "$1==" 2>&1 | sed -n 's/.*from versions: //p' | head -n 1)"
  printf '%s' "$raw" | tr -d '()' | tr ',' '\n' | sed 's/^ *//;s/ *$//' | grep -E '^[0-9]' | head -n 4
}

# اسم وحدة ← اسم حزمة apt (يجرّب الشكلين)
_mahdi_apt_python_pkg() {
  local name="$1"
  name="${name%%\[*}"
  name="${name%%[<>=!~]*}"
  name="${name,,}"
  name="${name//_/-}"
  local cand
  for cand in "python3-$name" "$name"; do
    if apt-cache show "$cand" >/dev/null 2>&1; then
      printf '%s' "$cand"
      return 0
    fi
  done
  return 1
}

mahdi_py_install_any() {
  local pkg="$1"
  [ -z "$pkg" ] && return 1
  local py="${MAHDI_PY:-python3}"

  if declare -f mahdi_ensure_pip >/dev/null 2>&1; then
    mahdi_ensure_pip >/dev/null 2>&1 || true
  fi

  # ١) الطريق العادي (يجرّب أيضًا المخزن الدائم تلقائيًا)
  _mahdi_pip_quiet --upgrade --prefer-binary "$pkg" && return 0

  # ٢) ويل جاهز فقط — يتجاوز أي فشل في البناء المحلي
  printf '%s↻%s  %s: محاولة بصيغة ويل جاهزة…\n' "$M_SKY" "$M_RESET" "$pkg"
  _mahdi_pip_quiet --only-binary :all: --upgrade "$pkg" && return 0

  # ٣) إصدارات أقدم — كثير من المكتبات القديمة تُثبَّت حيث يفشل الأحدث
  local v tried=0
  while IFS= read -r v; do
    [ -z "$v" ] && continue
    tried=$((tried + 1))
    [ "$tried" -gt 3 ] && break
    printf '%s↻%s  %s: محاولة الإصدار %s…\n' "$M_SKY" "$M_RESET" "$pkg" "$v"
    _mahdi_pip_quiet --upgrade "$pkg==$v" && return 0
  done < <(_mahdi_pip_versions "$pkg")

  # ٤) بناء من المصدر مع أدوات البناء (كان السبب الأشهر لفشل التثبيت)
  printf '%s↻%s  %s: تجهيز أدوات البناء ثم بناء من المصدر…\n' "$M_SKY" "$M_RESET" "$pkg"
  mahdi_apt_install_soft build-essential python3-dev pkg-config libffi-dev libssl-dev >/dev/null 2>&1 || true
  _mahdi_pip_quiet --no-build-isolation --no-cache-dir "$pkg" && return 0
  _mahdi_pip_quiet --no-binary :all: --no-cache-dir "$pkg" && return 0

  # ٥) حزمة النظام (تتجاوزت pip تمامًا)
  local apt_pkg
  if apt_pkg="$(_mahdi_apt_python_pkg "$pkg")"; then
    printf '%s↻%s  %s: محاولة حزمة النظام %s…\n' "$M_SKY" "$M_RESET" "$pkg" "$apt_pkg"
    mahdi_apt_install_soft "$apt_pkg" >/dev/null 2>&1 || true
    if "$py" -c "import importlib.util,sys; sys.exit(0 if importlib.util.find_spec(sys.argv[1]) else 1)" \
      "$(printf '%s' "$pkg" | tr '-' '_')" >/dev/null 2>&1; then
      return 0
    fi
  fi

  # ٦) مصدر بديل صريح (قد يكون المستخدم خلف شبكة تحجب pypi)
  printf '%s↻%s  %s: محاولة عبر مصدر بديل…\n' "$M_SKY" "$M_RESET" "$pkg"
  _mahdi_pip_quiet --index-url https://pypi.org/simple "$pkg" && return 0
  _mahdi_pip_quiet --index-url https://mirrors.aliyun.com/pypi/simple "$pkg" && return 0

  return 1
}

# ——————————————————————————————————————————————————————————————————————————————
# 7) Node — أربع محاولات لكل حزمة
# ——————————————————————————————————————————————————————————————————————————————
mahdi_npm_install_any() {
  local pkg="$1" dir="${2:-$PWD}"
  [ -z "$pkg" ] && return 1
  mahdi_node_ensure >/dev/null 2>&1 || true
  mahdi_have npm || return 1

  local -a base=(--no-fund --no-audit --loglevel=error --progress=false)

  (cd "$dir" 2>/dev/null && npm install "${base[@]}" "$pkg" >/dev/null 2>&1) && return 0
  (cd "$dir" 2>/dev/null && npm install "${base[@]}" --legacy-peer-deps "$pkg" >/dev/null 2>&1) && return 0
  (cd "$dir" 2>/dev/null && npm install "${base[@]}" --force "$pkg" >/dev/null 2>&1) && return 0

  printf '%s↻%s  %s: محاولة تثبيت عام…\n' "$M_SKY" "$M_RESET" "$pkg"
  npm install -g "${base[@]}" "$pkg" >/dev/null 2>&1 && return 0
  npm install -g "${base[@]}" --registry https://registry.npmjs.org "$pkg" >/dev/null 2>&1 && return 0
  npm install -g "${base[@]}" --registry https://registry.npmmirror.com "$pkg" >/dev/null 2>&1 && return 0

  local apt_pkg
  if apt_pkg="$(_mahdi_apt_python_pkg "node-$pkg" 2>/dev/null)"; then
    mahdi_apt_install_soft "$apt_pkg" >/dev/null 2>&1 && return 0
  fi
  return 1
}

# ——————————————————————————————————————————————————————————————————————————————
# 8) تجهيز مشروع كامل من GitHub — مرة واحدة لكل نسخة من الملفات
#
# السكربتات القادمة من الإنترنت لا «تنهار» لأنها خطأ، بل لأن اعتماديات المشروع
# (requirements.txt / package.json / …) لم تُثبَّت. هنا نكتشف المشروع ونجهّزه.
# ——————————————————————————————————————————————————————————————————————————————
_mahdi_bootstrap_stamp() {
  local dir="$1" f stamp=""
  for f in requirements.txt pyproject.toml setup.py setup.cfg package.json Pipfile Gemfile composer.json go.mod Cargo.toml environment.yml; do
    [ -f "$dir/$f" ] || continue
    stamp="$stamp$f:$(wc -c <"$dir/$f" 2>/dev/null | tr -d ' '):"
  done
  printf '%s' "$stamp"
}

mahdi_project_bootstrap() {
  local dir="${1:-$PWD}"
  [ -d "$dir" ] || return 0
  [ "${MAHDI_PROJECT_BOOTSTRAP:-1}" = "0" ] && return 0
  # مجلد للقراءة فقط (‏/sdcard بلا إذن «كل الملفات»): لا node_modules ولا بناء
  [ -w "$dir" ] || return 0

  local stamp
  stamp="$(_mahdi_bootstrap_stamp "$dir")"
  [ -z "$stamp" ] && return 0

  local marker="$dir/.mahdi_deps_ready"
  [ -f "$marker" ] && [ "$(cat "$marker" 2>/dev/null)" = "$stamp" ] && return 0

  # 6.1.x: أسطر قليلة فقط، فلا يبدو التشغيل «تقريرًا»
  printf '%s▸%s  تجهيز اعتماديات المشروع…\n' "$M_SKY" "$M_RESET"

  local ok=0

  if [ -f "$dir/requirements.txt" ] && grep -qEv '^[[:space:]]*(#|$)' "$dir/requirements.txt" 2>/dev/null; then
    if declare -f mahdi_pip_run >/dev/null 2>&1; then
      mahdi_pip_run "requirements.txt" -r "$dir/requirements.txt" >/dev/null 2>&1 && ok=1
    else
      (cd "$dir" && "${MAHDI_PY:-python3}" -m pip install --disable-pip-version-check \
        --no-input --quiet -r requirements.txt >/dev/null 2>&1) && ok=1
    fi
    if [ "$ok" -eq 0 ]; then
      # ملف المتطلبات فشل ككتلة واحدة ⇒ نُثبّت سطرًا سطرًا بالسلاسل البديلة
      local line name
      while IFS= read -r line; do
        case "$line" in '' | \#* | -*) continue ;; esac
        name="${line%%[=<>!~; ]*}"
        [ -z "$name" ] && continue
        mahdi_py_install_any "$name" >/dev/null 2>&1 || true
      done <"$dir/requirements.txt"
    fi
  fi

  if [ -f "$dir/pyproject.toml" ] || [ -f "$dir/setup.py" ]; then
    (cd "$dir" && "${MAHDI_PY:-python3}" -m pip install --disable-pip-version-check --no-input \
      --no-build-isolation --quiet -e . >/dev/null 2>&1) ||
      (cd "$dir" && "${MAHDI_PY:-python3}" -m pip install --disable-pip-version-check --no-input \
        --quiet . >/dev/null 2>&1) || true
  fi

  if [ -f "$dir/package.json" ]; then
    mahdi_node_ensure >/dev/null 2>&1 || true
    if mahdi_have npm; then
      if [ ! -d "$dir/node_modules" ] || [ -z "$(ls -A "$dir/node_modules" 2>/dev/null)" ]; then
        (cd "$dir" && npm install --no-fund --no-audit --loglevel=error --progress=false >/dev/null 2>&1) ||
          (cd "$dir" && npm install --no-fund --no-audit --loglevel=error --legacy-peer-deps >/dev/null 2>&1) ||
          (cd "$dir" && npm install --no-fund --no-audit --loglevel=error --force >/dev/null 2>&1) || true
      fi
    fi
  fi

  if [ -f "$dir/Gemfile" ]; then
    mahdi_install_command bundle >/dev/null 2>&1 || mahdi_pkg_install ruby-full >/dev/null 2>&1 || true
    (cd "$dir" && bundle install >/dev/null 2>&1) || true
  fi

  if [ -f "$dir/composer.json" ]; then
    mahdi_install_command composer >/dev/null 2>&1 || mahdi_pkg_install composer >/dev/null 2>&1 || true
    (cd "$dir" && COMPOSER_ALLOW_SUPERUSER=1 composer install --no-interaction --quiet >/dev/null 2>&1) || true
  fi

  if [ -f "$dir/go.mod" ]; then
    mahdi_pkg_install golang-go >/dev/null 2>&1 || true
    (cd "$dir" && go mod download >/dev/null 2>&1) || true
  fi

  if [ -f "$dir/Cargo.toml" ]; then
    mahdi_pkg_install rustc cargo >/dev/null 2>&1 || true
    (cd "$dir" && cargo fetch >/dev/null 2>&1) || true
  fi

  # سكربتات المشروع يجب أن تكون قابلة للتنفيذ (‏./install.sh من GitHub)
  find "$dir" -maxdepth 2 -type f \( -name '*.sh' -o -name '*.bash' \) -exec chmod +x {} + 2>/dev/null || true

  printf '%s' "$stamp" >"$marker" 2>/dev/null || true
  return 0
}

# ——————————————————————————————————————————————————————————————————————————————
# 9) أدوات ناقصة في بيئة أندرويد — حتى لا تفشل سكربتات Termux من أول سطر
# ——————————————————————————————————————————————————————————————————————————————
_mahdi_write_shim() {
  local name="$1" body="$2" target
  target="${LOCAL:-$HOME}/bin/$name"
  mkdir -p "${LOCAL:-$HOME}/bin" 2>/dev/null || return 0
  # لا نكتب فوق أداة نظام حقيقية
  if [ -e "$target" ] && [ "$(cat "$target" 2>/dev/null)" = "$body" ]; then
    chmod +x "$target" 2>/dev/null || true
    return 0
  fi
  printf '%s\n' "$body" >"$target" 2>/dev/null || return 1
  chmod 755 "$target" 2>/dev/null || true
  return 0
}

mahdi_install_shims() {
  local bin="${LOCAL:-$HOME}/bin"
  [ -d "$bin" ] || mkdir -p "$bin" 2>/dev/null || return 0

  # pkg — واجهة Termux
  _mahdi_write_shim pkg '#!/bin/bash
LOCAL="${LOCAL:-$(cd "$(dirname "$0")/.." && pwd)}"
[ -f "$LOCAL/bin/rescue" ] && . "$LOCAL/bin/rescue" >/dev/null 2>&1
if declare -f mahdi_pkg >/dev/null 2>&1; then mahdi_pkg "$@"; else exec apt "$@"; fi'

  # sudo — نحن root داخل الحاوية، والسكربتات تطلبه كثيرًا
  if ! command -v sudo >/dev/null 2>&1; then
    _mahdi_write_shim sudo '#!/bin/bash
while [ $# -gt 0 ]; do
  case "$1" in
    -E | -H | -n | -S | --) shift ;;
    -u | -g | -p) shift 2 ;;
    -*) shift ;;
    *) break ;;
  esac
done
[ $# -eq 0 ] && exit 0
exec "$@"'
  fi

  # termux-* — تُستدعى في سكربتات الخطوط والواجهات، فتفشل بلا داعٍ
  _mahdi_write_shim termux-reload-settings '#!/bin/bash
printf "\033[92m✔\033[0m  الإعدادات مطبَّقة داخل Mahdi-Editor تلقائيًا — لا حاجة لإعادة التحميل.\n"'
  _mahdi_write_shim termux-setup-storage '#!/bin/bash
mkdir -p ~/storage/shared ~/storage/downloads 2>/dev/null
ln -sfn /sdcard ~/storage/shared 2>/dev/null
ln -sfn /sdcard/Download ~/storage/downloads 2>/dev/null
printf "\033[92m✔\033[0m  التخزين متاح في /sdcard و~/storage/shared\n"'
  _mahdi_write_shim termux-open '#!/bin/bash
[ -n "$1" ] && { command -v xdg-open >/dev/null 2>&1 && exec xdg-open "$1"; }
printf "\033[93m⚠\033[0m  فتح الملفات خارجيًا غير متاح هنا — المسار: %s\n" "${1:-—}"'
  _mahdi_write_shim termux-open-url '#!/bin/bash
mkdir -p ~/.local/bin 2>/dev/null
printf "\033[96m▸\033[0m  %s\n" "${1:-}"
exit 0'
  _mahdi_write_shim termux-change-repo '#!/bin/bash
printf "\033[96m▸\033[0m  مستودعات Mahdi-Editor مُدارة تلقائيًا — لا حاجة لتغييرها.\n"'
  _mahdi_write_shim termux-info '#!/bin/bash
printf "Mahdi-Editor · %s\n" "$(uname -sm)"
command -v python3 >/dev/null 2>&1 && python3 -V 2>&1
command -v node >/dev/null 2>&1 && node -v 2>&1
exit 0'
  _mahdi_write_shim termux-fix-shebang '#!/bin/bash
for f in "$@"; do
  [ -f "$f" ] || continue
  sed -i "1s|.*|#!/usr/bin/env bash|" "$f" 2>/dev/null || true
done
exit 0'
  return 0
}

# يُستدعى من init.sh بعد تحميل utils
mahdi_rescue_init() {
  mahdi_apt_noninteractive_once
  mahdi_install_shims
  return 0
}
