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
source "$LOCAL/bin/utils"

# 8.3.0: «groups: cannot find name for group ID 99909997»
# أندرويد يمنح التطبيق مجموعات تكميلية بلا أسماء داخل /etc/group، و`/etc/bash.bashrc`
# في أوبنتو يستدعي groups() فتُطبع الرسالة عند كل طرفية تفاعلية. نُسمّي كل
# مجموعة مجهولة مرة واحدة لكل تثبيت (بلا حذف ولا تعديل مجموعات النظام).
if [ ! -f /etc/.mahdi_groups_ok ] && [ -w /etc/group ]; then
    _mahdi_gids="$(id -G 2>/dev/null) "
    _mahdi_groups_line="$(grep '^Groups:' /proc/self/status 2>/dev/null | cut -d: -f2-)"
    _mahdi_gids="$_mahdi_gids $_mahdi_groups_line"
    for _mahdi_gid in $_mahdi_gids; do
        case "$_mahdi_gid" in
            ''|*[!0-9]*) continue ;;
        esac
        if ! grep -qE "^[^:]+:[^:]*:${_mahdi_gid}:" /etc/group 2>/dev/null; then
            printf 'mahdi_g%s:x:%s:\n' "$_mahdi_gid" "$_mahdi_gid" >> /etc/group 2>/dev/null || true
        fi
    done
    : > /etc/.mahdi_groups_ok 2>/dev/null || true
    unset _mahdi_gids _mahdi_groups_line _mahdi_gid
fi

SDK="$(getprop ro.build.version.sdk 2>/dev/null)"

# 8.3.0: الطرفية التفاعلية التي تُفتح بعد انتهاء سكربت لا تُعيد تجهيز الحاوية.
if [ "${MAHDI_SKIP_INIT_SETUP:-0}" != "1" ]; then
    # Use ${SDK:-999} to default to 999 if $SDK is empty, preventing syntax errors.
    if [ "${SDK:-999}" -le 29 ]; then
        rm -f "$LOCAL/bin/mahdi"
        cp "$NATIVE_LIB_DIR/libmahdi_cli.so" "$LOCAL/bin/mahdi"
        chmod +x "$LOCAL/bin/mahdi"
    else
        ln -sfn "$NATIVE_LIB_DIR/libmahdi_cli.so" "$LOCAL/bin/mahdi"
    fi
fi


if [ -f "$LOCAL/.sandbox_degraded" ]; then
    warn "الطرفية في وضع محدود؛ أعد تثبيتها لاستعادة كل المزايا."
fi

# 8.3.0: ضبط المنطقة الزمنية كان يعمل عند **كل** فتح طرفية (حتى الطرفية
# التفاعلية بعد انتهاء سكربت)، فيُعيد dpkg-reconfigure ويُبطئ البدء.
# علامة واحدة تكفي: تُضبط مرة لكل تثبيت.
if [ ! -f /etc/.mahdi_timezone_ok ]; then
    CONTAINER_TIMEZONE="UTC"
    ln -snf "/usr/share/zoneinfo/$CONTAINER_TIMEZONE" /etc/localtime 2>/dev/null || true
    printf '%s\n' "$CONTAINER_TIMEZONE" > /etc/timezone 2>/dev/null || true
    DEBIAN_FRONTEND=noninteractive dpkg-reconfigure -f noninteractive tzdata >/dev/null 2>&1 || true
    : > /etc/.mahdi_timezone_ok 2>/dev/null || true
fi


if [[ -f ~/.bashrc ]]; then
    # shellcheck disable=SC1090
    source ~/.bashrc
fi

# 8.3.0: مِحَثّ Mahdi **بعد** ~/.bashrc.
# ملف bashrc الافتراضي في أوبنتو يعيد تعيين PS1 إلى `root@localhost:\w#`
# — وهو بالضبط ما ظهر في لقطة المستخدم — فكان مِحَثّنا الأنيق يُلغى فورًا.
# 8.5.0: المِحَثّ صار حيًّا: يعرض رمز آخر أمر عند الفشل، وفرع Git الحالي،
# ويتحوّل `❯` إلى المرجاني عند الخطأ — فيعرف المستخدم نتيجة الأمر قبل قراءته.
# للتعطيل: MAHDI_GIT_PS1=0 (يُخفي فرع Git فقط)
_mahdi_prompt() {
    local rc=$? status='' branch='' arrow
    if [ "$rc" -ne 0 ]; then
        status="\[\e[1;91m\]✖ ${rc}\[\e[0m\] "
        arrow='\[\e[1;91m\]❯\[\e[0m\] '
    else
        arrow='\[\e[93m\]❯\[\e[0m\] '
    fi
    if [ "${MAHDI_GIT_PS1:-1}" != "0" ] && command -v git >/dev/null 2>&1; then
        branch="$(GIT_OPTIONAL_LOCKS=0 git rev-parse --abbrev-ref HEAD 2>/dev/null)" || branch=''
        [ "$branch" = "HEAD" ] && branch=''
        # فرع طويل يدفع المِحَثّ إلى سطرين؛ نعرض 21 محرفًا ثم …
        if [ "${#branch}" -gt 24 ]; then
            branch="${branch:0:21}…"
        fi
        [ -n "$branch" ] && branch="\[\e[95m\]${branch}\[\e[0m\] "
    fi
    PS1="\[\e[1;96m\]▌\[\e[0m\] \[\e[94m\]\u@\h\[\e[0m\] \[\e[1;97m\]\w\[\e[0m\] ${status}${branch}${arrow}"
}
# نحترم أي PROMPT_COMMAND سابق (من bashrc) بدل إلغائه، ونتجنّب تكرار أنفسنا
case "${PROMPT_COMMAND:-}" in
    *_mahdi_prompt*) ;;
    "") PROMPT_COMMAND="_mahdi_prompt" ;;
    *) PROMPT_COMMAND="_mahdi_prompt; $PROMPT_COMMAND" ;;
esac


ensure_packages_once() {
    # 8.3.0: لا apt ولا dpkg عند فتح طرفية بعد انتهاء سكربت
    [ "${MAHDI_SKIP_INIT_SETUP:-0}" = "1" ] && return 0
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

# 8.4.0: أمر غير موجود ⇒ لا سؤال، ولا «apt install» إنجليزي، ولا انتظار يدوي.
# يُثبَّت تلقائيًا بلا تفاعل (DEBIAN_FRONTEND=noninteractive) ثم يُعاد الأمر،
# وإن تعذّر يبقى سطر عربي واحد يذكر الحزمة. للتعطيل: MAHDI_AUTO_INSTALL=0
function command_not_found_handle {
  local cmd="$1"
  shift || true
  local hint=""

  # 8.5.0: pip أو pip3 غائب ⇒ يُجهَّز فورًا ويُنفَّذ الأمر نفسه بلا «الأمر غير موجود».
  # كان الاختصار يُكتب مرة واحدة فقط، فإذا حُذف pip بقي الأمر مفقودًا للأبد.
  case "$cmd" in
    pip | pip3)
      printf "${M_MUTED}  تجهيز pip تلقائيًا…${M_RESET}\n" >&2
      if declare -f mahdi_ensure_pip >/dev/null 2>&1 && mahdi_ensure_pip >/dev/null 2>&1; then
        hash -r 2>/dev/null || true
        "${MAHDI_PY:-python3}" -m pip "$@"
        return $?
      fi
      ;;
  esac

  if [ -x /usr/lib/command-not-found ]; then
    hint=$(/usr/lib/command-not-found -- "$cmd" 2>&1 | sed -n 's/.*apt install \(.*\)/\1/p' | head -1)
  elif [ -x /usr/share/command-not-found/command-not-found ]; then
    hint=$(/usr/share/command-not-found/command-not-found -- "$cmd" 2>&1 | sed -n 's/.*apt install \(.*\)/\1/p' | head -1)
  fi

  printf "${M_CORAL}✖${M_RESET}  الأمر غير موجود: ${M_BOLD}%s${M_RESET}\n" "$cmd" >&2

  if [ -n "$hint" ]; then
    if [ "${MAHDI_AUTO_INSTALL:-1}" = "1" ] && declare -f apt_fast >/dev/null 2>&1; then
      printf "${M_SKY}↳${M_RESET}  تثبيت ${M_BOLD}%s${M_RESET} تلقائيًا…\n" "$hint" >&2
      if apt_fast $hint >/dev/null 2>&1; then
        hash -r 2>/dev/null || true
        "$cmd" "$@"
        return $?
      fi
    fi
    printf "${M_SKY}↳${M_RESET}  الحزمة المطلوبة: ${M_BOLD}%s${M_RESET}  ${M_MUTED}· ثبّتها من: اللغات ← المشغّلات${M_RESET}\n" "$hint" >&2
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

# 8.5.0: إكمال Tab لأوامر Mahdi وملفاتها — بلا أي إعداد من المستخدم
if [ -n "${BASH_VERSION:-}" ] && [ "${MAHDI_NO_COMPLETION:-0}" != "1" ]; then
    _mahdi_complete() {
        local cur="${COMP_WORDS[COMP_CWORD]}"
        case "${COMP_WORDS[0]}" in
            mh)
                if [ "${COMP_CWORD}" -eq 1 ]; then
                    COMPREPLY=($(compgen -W "doctor py pip new run cache version help فحص بايثون جديد تشغيل مخزن إصدار مساعدة" -- "$cur"))
                elif [ "${COMP_WORDS[1]}" = "new" ] && [ "${COMP_CWORD}" -eq 2 ]; then
                    COMPREPLY=($(compgen -W "py bot web api html node" -- "$cur"))
                elif [ "${COMP_WORDS[1]}" = "doctor" ]; then
                    COMPREPLY=($(compgen -W "--fix --net --help" -- "$cur"))
                fi
                ;;
            mahdi-python)
                COMPREPLY=($(compgen -W "--info --list --use --upgrade --doctor --reset" -- "$cur"))
                ;;
            py | Py | pY | PY | js | Js | jS | JS | ts | Ts | tS | TS | run)
                COMPREPLY=($(compgen -f -- "$cur"))
                ;;
        esac
        return 0
    }
    complete -F _mahdi_complete mh mahdi-python py Py pY PY js Js jS JS ts Ts tS TS run 2>/dev/null || true
fi
