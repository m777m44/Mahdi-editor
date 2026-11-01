# shellcheck shell=bash
# ——————————————————————————————————————————————————————————————————————————————
# مثبّت بيئات التشغيل الحقيقي (3.0.0)
#
# يكتب **حالة التثبيت** في ملف يقرأه التطبيق مباشرة، فلا تبقى الواجهة تدور بلا نهاية:
#
#   ~/.mahdi/install-status/<id>     state=installed|installing|failed · progress=0..100 · msg=…
#   ~/.mahdi/installed/<id>          علامة النجاح + الإصدار (يقرأها LanguageInstallStore)
#   ~/.mahdi/logs/install-<id>.log   سجلّ كامل للتشخيص
#
# قاعدة صارمة: إذا كانت البيئة موجودة أصلًا فلا يُطبع **أي سطر** — صمت تام.
# ——————————————————————————————————————————————————————————————————————————————

MAHDI_STATE_DIR="${HOME}/.mahdi"
MAHDI_STATUS_DIR="${MAHDI_STATE_DIR}/install-status"
MAHDI_MARKER_DIR="${MAHDI_STATE_DIR}/installed"
MAHDI_LOG_DIR="${MAHDI_STATE_DIR}/logs"

# يُشغَّل من الطرفية داخل الـ sandbox: bash install_runtime <id> <packages> <post> <version_cmd>
mahdi_runtime_status() {
  local id="$1" state="$2" progress="$3" msg="${4:-}"
  mkdir -p "$MAHDI_STATUS_DIR" 2>/dev/null
  # رسالة بسطر واحد لتفادي كسر ملف الحالة
  msg=$(printf '%s' "$msg" | tr '\n\r\t' '   ' | cut -c1-200)
  {
    printf 'state=%s\n' "$state"
    printf 'progress=%s\n' "$progress"
    printf 'msg=%s\n' "$msg"
    printf 'time=%s\n' "$(date +%s 2>/dev/null || echo 0)"
  } > "$MAHDI_STATUS_DIR/$id" 2>/dev/null
}

mahdi_runtime_version() {
  local cmd="$1"
  [ -z "$cmd" ] && return 0
  command -v sh >/dev/null 2>&1 || return 0
  sh -c "$cmd 2>/dev/null | head -n1" 2>/dev/null | tr -d '\r' | cut -c1-60
}

mahdi_runtime_installed() {
  local id="$1" version_cmd="$2"
  [ -f "$MAHDI_MARKER_DIR/$id" ] && return 0
  [ -z "$version_cmd" ] && return 1
  # التحقّق الفعلي: تنفيذ الأمر يُثبت أن البيئة تعمل لا أن ملفًا موجود فقط
  sh -c "$version_cmd >/dev/null 2>&1" 2>/dev/null
}

main() {
  local id="${1:-}" packages="${2:-}" post="${3:-}" version_cmd="${4:-}"
  [ -z "$id" ] && { echo "install_runtime: missing id" >&2; exit 2; }

  mkdir -p "$MAHDI_STATE_DIR" "$MAHDI_STATUS_DIR" "$MAHDI_MARKER_DIR" "$MAHDI_LOG_DIR" 2>/dev/null

  # موجودة فعلًا؟ صمت تام + تأكيد الحالة بلا أي طباعة
  if mahdi_runtime_installed "$id" "$version_cmd"; then
    local existing_version
    existing_version=$(mahdi_runtime_version "$version_cmd")
    if [ ! -f "$MAHDI_MARKER_DIR/$id" ]; then
      printf 'version=%s\n' "$existing_version" > "$MAHDI_MARKER_DIR/$id" 2>/dev/null
    fi
    mahdi_runtime_status "$id" installed 100 ""
    exit 0
  fi

  local log="$MAHDI_LOG_DIR/install-$id.log"
  : > "$log" 2>/dev/null

  # شريط التقدّم إن توفّر
  if ! declare -f mahdi_bar_start >/dev/null 2>&1; then
    # shellcheck disable=SC1090
    [ -f "$LOCAL/bin/progress" ] && source "$LOCAL/bin/progress"
  fi

  mahdi_runtime_status "$id" installing 3 "بدء التثبيت"
  declare -f mahdi_bar_start >/dev/null 2>&1 && mahdi_bar_start "تثبيت $id"

  local label
  case "$id" in
    python) label="تثبيت Python" ;;
    node) label="تثبيت Node.js" ;;
    php) label="تثبيت PHP" ;;
    ruby) label="تثبيت Ruby" ;;
    lua) label="تثبيت Lua" ;;
    git) label="تثبيت Git" ;;
    java) label="تثبيت Java" ;;
    gcc) label="تثبيت C/C++" ;;
    go) label="تثبيت Go" ;;
    rust) label="تثبيت Rust" ;;
    perl) label="تثبيت Perl" ;;
    sqlite) label="تثبيت SQLite" ;;
    *) label="تثبيت $id" ;;
  esac

  if ! command -v apt-get >/dev/null 2>&1; then
    mahdi_runtime_status "$id" failed 0 "apt-get غير متاح — أعد تثبيت الطرفية"
    declare -f mahdi_bar_abort >/dev/null 2>&1 && mahdi_bar_abort
    echo "✘ apt-get غير متاح داخل الطرفية" >&2
    exit 1
  fi

  export DEBIAN_FRONTEND=noninteractive
  declare -f mahdi_bar_set >/dev/null 2>&1 && mahdi_bar_set 10 "$label"
  mahdi_runtime_status "$id" installing 10 "تحديث قوائم الحزم"

  if ! apt-get update -qq >>"$log" 2>&1; then
    mahdi_runtime_status "$id" failed 0 "تعذّر تحديث قوائم الحزم — تحقّق من الشبكة"
    declare -f mahdi_bar_abort >/dev/null 2>&1 && mahdi_bar_abort
    echo "✘ تعذّر apt-get update (راجع ~/.mahdi/logs/install-$id.log)" >&2
    exit 1
  fi

  declare -f mahdi_bar_set >/dev/null 2>&1 && mahdi_bar_set 35 "$label"
  mahdi_runtime_status "$id" installing 35 "تنزيل الحزم"

  if [ -n "$packages" ]; then
    # shellcheck disable=SC2086
    if ! apt-get install -y --no-install-recommends $packages >>"$log" 2>&1; then
      local reason
      reason=$(grep -Ei 'E:|Unable to|Could not|not found|404' "$log" 2>/dev/null | tail -n1)
      [ -z "$reason" ] && reason="فشل تنزيل الحزم"
      mahdi_runtime_status "$id" failed 0 "$reason"
      declare -f mahdi_bar_abort >/dev/null 2>&1 && mahdi_bar_abort
      echo "✘ $reason" >&2
      exit 1
    fi
  fi

  if [ -n "$post" ]; then
    declare -f mahdi_bar_set >/dev/null 2>&1 && mahdi_bar_set 70 "$label"
    mahdi_runtime_status "$id" installing 70 "إعداد إضافي"
    sh -c "$post" >>"$log" 2>&1 || true
  fi

  declare -f mahdi_bar_set >/dev/null 2>&1 && mahdi_bar_set 92 "التحقّق"
  mahdi_runtime_status "$id" installing 92 "التحقّق من التثبيت"

  if [ -n "$version_cmd" ] && ! sh -c "$version_cmd >/dev/null 2>&1"; then
    mahdi_runtime_status "$id" failed 0 "الثنائي غير قابل للتشغيل بعد التثبيت"
    declare -f mahdi_bar_abort >/dev/null 2>&1 && mahdi_bar_abort
    echo "✘ لم يعمل الأمر: $version_cmd" >&2
    exit 1
  fi

  local version
  version=$(mahdi_runtime_version "$version_cmd")
  {
    printf 'version=%s\n' "$version"
    printf 'time=%s\n' "$(date +%s 2>/dev/null || echo 0)"
  } > "$MAHDI_MARKER_DIR/$id" 2>/dev/null

  mahdi_runtime_status "$id" installed 100 ""
  declare -f mahdi_bar_done >/dev/null 2>&1 && mahdi_bar_done "${label} ✔"
  exit 0
}

main "$@"
