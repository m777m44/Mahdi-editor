# shellcheck disable=SC2034
force_color_prompt=yes
shopt -s checkwinsize

export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/games:/usr/local/bin:/usr/local/sbin:$LOCAL/bin:$PATH
export SHELL="bash"
# 4.1.0: بيئة UTF-8 افتراضية كما في Termux — نصوص عربية وإيموجي بلا أخطاء ترميز
export LANG="${LANG:-C.UTF-8}"
export LC_ALL="${LC_ALL:-C.UTF-8}"
export PYTHONIOENCODING=utf-8
export PYTHONUTF8=1
# 8.2.0: مِحَثّ (prompt) أنيق بأربع درجات من لوحة Mahdi، بدل الأخضر/الأزرق
# الافتراضي. الألوان من خانات ANSI الـ16 التي يُعرّفها الثيم، فتظل مقروءة
# على «الفاتح» كما على «السمائي». بلا أسطر إضافية تستهلك ارتفاع الهاتف.
export PS1='\[\e[1;96m\]▌\[\e[0m\] \[\e[94m\]\u@\h\[\e[0m\] \[\e[1;97m\]\w\[\e[0m\] \[\e[93m\]❯\[\e[0m\] '

source "$LOCAL/bin/utils"

SDK="$(getprop ro.build.version.sdk 2>/dev/null)"

# Use ${SDK:-999} to default to 999 if $SDK is empty, preventing syntax errors.
if [ "${SDK:-999}" -le 29 ]; then
    rm -f "$LOCAL/bin/mahdi"
    cp "$NATIVE_LIB_DIR/libmahdi_cli.so" "$LOCAL/bin/mahdi"
    chmod +x "$LOCAL/bin/mahdi"
else
    ln -sfn "$NATIVE_LIB_DIR/libmahdi_cli.so" "$LOCAL/bin/mahdi"
fi


if [ -f "$LOCAL/.sandbox_degraded" ]; then
    warn "الطرفية في وضع محدود؛ أعد تثبيتها لاستعادة كل المزايا."
fi

# Set timezone
CONTAINER_TIMEZONE="UTC"  # or any timezone like "Asia/Kolkata"

# Symlink /etc/localtime to the desired timezone
ln -snf "/usr/share/zoneinfo/$CONTAINER_TIMEZONE" /etc/localtime

# Write the timezone string to /etc/timezone
echo "$CONTAINER_TIMEZONE" > /etc/timezone

# Reconfigure tzdata to apply without prompts
DEBIAN_FRONTEND=noninteractive dpkg-reconfigure -f noninteractive tzdata >/dev/null 2>&1


if [[ -f ~/.bashrc ]]; then
    # shellcheck disable=SC1090
    source ~/.bashrc
fi


ensure_packages_once() {
    local marker_file="/.cache/.packages_ensured"
    local PACKAGES=("command-not-found" "sudo" "xkb-data" "libjemalloc-dev")

    # Exit early if already done
    [[ -f "$marker_file" ]] && return 0

    echo 'APT::Install-Recommends "false";' > /etc/apt/apt.conf.d/99norecommends
    echo 'APT::Install-Suggests "false";' >> /etc/apt/apt.conf.d/99norecommends

    # Create cache dir
    mkdir -p "/.cache"

    # Check for missing packages
    local MISSING=()
    for pkg in "${PACKAGES[@]}"; do
        if ! dpkg -s "$pkg" >/dev/null 2>&1; then
            MISSING+=("$pkg")
        fi
    done

    # If nothing missing, just mark as done
    if [ ${#MISSING[@]} -eq 0 ]; then
        touch "$marker_file"
        return 0
    fi

    info "تجهيز حزم النظام: ${MISSING[*]}"

    if export DEBIAN_FRONTEND=noninteractive && \
       apt update -y && \
       apt install -y "${MISSING[@]}"; then
       touch "$marker_file"
       clear
       success "اكتمل التجهيز."
    else
        error "تعذّر تثبيت الحزم."
        return 1
    fi

    # Update command-not-found database
    update-command-not-found 2>/dev/null || true
}


ensure_packages_once
unset -f ensure_packages_once

# 8.0.0: أمر غير موجود ⇒ سطر عربي واحد + اسم الحزمة، بلا نصّ إنجليزي طويل.
#
# كان يظهر نصّ command-not-found الإنجليزي كاملًا (خمسة أسطر) في تطبيق عربي.
# الآن: سبب مختصر بالعربية، ثم سطر واحد يقول الحزمة المطلوبة. ولمن يريد
# التثبيت التلقائي: MAHDI_AUTO_INSTALL=1 (يُثبَّت الأمر ثم يٌعاد تشغيله).
function command_not_found_handle {
  local cmd="$1"
  shift || true
  local hint=""

  if [ -x /usr/lib/command-not-found ]; then
    hint=$(/usr/lib/command-not-found -- "$cmd" 2>&1 | sed -n 's/.*apt install \(.*\)/\1/p' | head -1)
  elif [ -x /usr/share/command-not-found/command-not-found ]; then
    hint=$(/usr/share/command-not-found/command-not-found -- "$cmd" 2>&1 | sed -n 's/.*apt install \(.*\)/\1/p' | head -1)
  fi

  printf "${M_CORAL}✖${M_RESET}  الأمر غير موجود: ${M_BOLD}%s${M_RESET}\n" "$cmd" >&2

  if [ -n "$hint" ]; then
    if [ "${MAHDI_AUTO_INSTALL:-0}" = "1" ] && declare -f apt_fast >/dev/null 2>&1; then
      printf "${M_SKY}↳${M_RESET}  تثبيت ${M_BOLD}%s${M_RESET} تلقائيًا…\n" "$hint" >&2
      if apt_fast $hint >/dev/null 2>&1; then
        hash -r 2>/dev/null || true
        "$cmd" "$@"
        return $?
      fi
    fi
    printf "${M_SKY}↳${M_RESET}  يمكن تثبيته: ${M_BOLD}apt install %s${M_RESET}\n" "$hint" >&2
  else
    printf "${M_MUTED}  تحقّق من الاسم أو ثبّته من: اللغات ← المشغّرات${M_RESET}\n" >&2
  fi
  return 127
}


alias ls='ls --color=auto'
alias grep='grep --color=auto'
alias egrep='egrep --color=auto'
alias fgrep='fgrep --color=auto'
alias pkg='apt'

if [[ -f /initrc ]]; then
    # shellcheck disable=SC1090
    source /initrc
fi

# shellcheck disable=SC2164
cd "$WKDIR" || cd $HOME

# 3.0.0: شعار MAHDI في الجلسات التفاعلية (وعند كل تشغيل سكربت عبر universal_runner)
if [ -f "$LOCAL/bin/banner" ]; then
    # shellcheck disable=SC1091
    source "$LOCAL/bin/banner"
fi

# 3.1.0: اختصارات التشغيل py · Py · pY · PY · js · ts · run (ملفات تنفيذية على PATH)
if [ -f "$LOCAL/bin/aliases" ]; then
    # shellcheck disable=SC1091
    source "$LOCAL/bin/aliases"
fi
