# shellcheck shell=bash
# ——————————————————————————————————————————————————————————————————————————————
# mahdi_dl — تنزيل سريع داخل طرفية Mahdi-Editor
#
#   mahdi_dl <الرابط> [اسم الملف]
#
# يستخدم aria2c بـ16 اتصالًا متوازيًا عند توفّره (مع تثبيته تلقائيًا عند الحاجة)،
# وإلا يعود إلى curl مع استئناف التنزيل وإعادة المحاولة، ثم إلى wget.
# الفائدة الأكبر مع الملفات الكبيرة: الأرشيفات (tar.gz / zip)، صور ISO، ملفات APK…
# ——————————————————————————————————————————————————————————————————————————————

url="$1"
if [ -z "$url" ]; then
  printf 'الاستخدام: mahdi_dl <الرابط> [اسم الملف]\n'
  exit 1
fi

out="$2"
if [ -z "$out" ]; then
  out="${url%%\?*}"
  out="${out##*/}"
  [ -z "$out" ] && out="download.bin"
fi

# أدوات التنزيل من auto_deps إن كانت الطرفية قد جهّزتها
if [ -z "${LOCAL:-}" ]; then
  LOCAL="$(cd "$(dirname "$0")/.." 2>/dev/null && pwd)"
  export LOCAL
fi
if [ -f "$LOCAL/bin/auto_deps" ]; then
  # shellcheck disable=SC1091
  . "$LOCAL/bin/auto_deps"
fi

printf '⬇  %s\n→  %s\n' "$url" "$out"

if declare -f mahdi_fetch_fast >/dev/null 2>&1; then
  mahdi_fetch_fast "$url" "$out"
elif command -v aria2c >/dev/null 2>&1; then
  aria2c -x16 -s16 -k1M --allow-overwrite=true --auto-file-renaming=false -d "$(dirname "$out")" \
    -o "$(basename "$out")" "$url"
elif command -v curl >/dev/null 2>&1; then
  curl -fL --retry 5 --retry-delay 2 -C - -o "$out" "$url"
else
  wget -c -O "$out" "$url"
fi

if [ -s "$out" ]; then
  size=$(du -h "$out" 2>/dev/null | cut -f1)
  printf '✓ تم التنزيل: %s (%s)\n' "$out" "${size:-?}"
else
  printf '✗ فشل التنزيل\n'
  exit 1
fi
