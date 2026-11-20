#!/usr/bin/env bash
# ——————————————————————————————————————————————————————————————————————————————
# 9.6.0 — اختبار تكامل لحرس رابط بايثون (libpython) بلا أندرويد ولا محاكي.
#
# يتحقق من:
#   ١) كشف رسالة الرابط بأشكالها (Android linker وglibc).
#   ٢) استخراج اسم المكتبة والأرقام.
#   ٣) تثبيت المكتبة: وصلات بالاسمين + LD_LIBRARY_PATH + مجلد ثابت.
#   ٤) إصلاح غلاف معطوب فعليًا: يصير يعمل بعد الإصلاح (دون أي تدخّل).
#   ٥) عدم المساس بغلاف سليم.
#   ٦) رسالة عربية واضحة عند تعذّر كل شيء (لا نص إنجليزي خام).
#
# التشغيل: bash tools/tests/pylink_test.sh
# ——————————————————————————————————————————————————————————————————————————————
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ASSETS="$ROOT/features/terminal/src/main/assets/terminal"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

pass=0
fail=0
ok() { printf '  ✔ %s\n' "$1"; pass=$((pass + 1)); }
no() {
  printf '  ✘ %s\n' "$1"
  # 9.6.0: التشخيص يظهر أيضًا كتعليق على الـ PR في GitHub (annotations API)،
  # فحين يفشل الاختبار في CI نعرف **أي** تحقّق سقط بلا حاجة لسجلّ كامل.
  printf '::error title=pylink_test::%s\n' "$1"
  fail=$((fail + 1))
}
check() { if eval "$2"; then ok "$1"; else no "$1"; fi; }

mkdir -p "$WORK/home" "$WORK/bin" "$WORK/lib"
ln -s "$ASSETS/utils.sh" "$WORK/bin/utils"
ln -s "$ASSETS/pylink.sh" "$WORK/bin/pylink"

export HOME="$WORK/home"
export LOCAL="$WORK"
export MAHDI_PY_LIB_DIR="$WORK/lib"
# shellcheck disable=SC1091
source "$WORK/bin/pylink"

printf '\n== ١) كشف رسالة الرابط ==\n'
BROKEN='CANNOT LINK EXECUTABLE "/data/user/0/com.mahdi.editor/tmp/tmpl12rec_h/Pyahmed.so": library "libpython3.11.so.1.0" not found: needed by main executable'
check "صيغة Android linker" "mahdi_py_link_broken \"\$BROKEN\""
check "اسم المكتبة" "[ \"\$(mahdi_py_link_signature \"\$BROKEN\")\" = 'libpython3.11.so.1.0' ]"
check "رقم الإصدار 3.11" "[ \"\$(mahdi_py_link_versions \"\$BROKEN\")\" = '3.11' ]"
GLIBC='python3: error while loading shared libraries: libpython3.12.so.1.0: cannot open shared object file'
check "صيغة glibc" "mahdi_py_link_broken \"\$GLIBC\""
check "نص سليم لا يُصنّف عطلًا" "! mahdi_py_link_broken 'print(\"hello\")'"

printf '\n== ٢) تثبيت المكتبة في مجلد ثابت ==\n'
: >"$WORK/lib/libpython3.11.so.1.0"
mahdi_py_install_lib "$WORK/lib/libpython3.11.so.1.0" libpython3.11.so.1.0 >/dev/null
check "وصل الاسم الكامل" "[ -L \"\$HOME/.mahdi/pylib/libpython3.11.so.1.0\" ]"
check "وصل الاسم القصير" "[ -L \"\$HOME/.mahdi/pylib/libpython3.11.so\" ]"
check "LD_LIBRARY_PATH يحوي المجلد" "printf '%s' \"\$LD_LIBRARY_PATH\" | grep -q \"\$HOME/.mahdi/pylib\""

printf '\n== ٣) إصلاح غلاف معطوب فعليًا ==\n'
# نُفرغ مسار المكتبات كي يُختبر الإصلاح من الصفر (لا يعتمد على اختبار ٢)
unset LD_LIBRARY_PATH
rm -rf "$HOME/.mahdi/pylib"

cat >"$WORK/bin/Pyahmed.so" <<'WRAP'
#!/bin/sh
for d in $(printf '%s' "$LD_LIBRARY_PATH" | tr ':' ' '); do
  [ -f "$d/libpython3.11.so.1.0" ] && { echo "Python 3.11.9"; exit 0; }
done
echo 'CANNOT LINK EXECUTABLE "Pyahmed.so": library "libpython3.11.so.1.0" not found: needed by main executable' >&2
exit 1
WRAP
chmod +x "$WORK/bin/Pyahmed.so"
"$WORK/bin/Pyahmed.so" --version >"$WORK/err.log" 2>&1 || true
check "الغلاف معطوب قبل الإصلاح" "grep -q 'CANNOT LINK' \"$WORK/err.log\""
mahdi_py_link_fix "$WORK/err.log" "$WORK/bin/Pyahmed.so" >/dev/null 2>&1
fix_rc=$?
check "الإصلاح أعاد 0 (رمز $fix_rc)" "[ \"$fix_rc\" -eq 0 ]"
check "الغلاف يعمل بعد الإصلاح" "\"$WORK/bin/Pyahmed.so\" --version 2>&1 | grep -q 'Python'"

printf '\n== ٤) غلاف سليم لا يُمسّ ==\n'
printf '#!/bin/sh\necho "Python 3.11.9"\n' >"$WORK/bin/healthy"
chmod +x "$WORK/bin/healthy"
mahdi_py_link_check_wrapper "$WORK/bin/healthy"
check "غلاف سليم يُقبل" "[ $? -eq 0 ]"
check "لم يُعَد تسميته" "[ -f \"$WORK/bin/healthy\" ]"

printf '\n== ٥) تعذّر كل شيء: لا نص إنجليزي خام ==\n'
reason="$(mahdi_py_link_reason)"
check "السبب عربي" "printf '%s' \"\$reason\" | grep -qE '[ء-ي]'"
check "لا يحتوي CANNOT LINK" "! printf '%s' \"\$reason\" | grep -q 'CANNOT LINK'"

printf '\n== ٦) صحة الملفات ==\n'
check "pylink.sh سليم نحويًا" "bash -n \"$ASSETS/pylink.sh\""

# تشخيص البيئة عند الفشل فقط — يشرح فرق CI عن الجهاز المحلي.
if [ "$fail" -ne 0 ]; then
  {
    printf 'user=%s home=%s\n' "$(id -un 2>/dev/null)" "$HOME"
    printf 'python3=%s\n' "$(command -v python3 || printf 'غائب')"
    printf 'patchelf=%s\n' "$(command -v patchelf || printf 'غائب')"
    printf 'ldconfig=%s\n' "$(command -v ldconfig || printf 'غائب')"
    printf 'LD_LIBRARY_PATH=%s\n' "${LD_LIBRARY_PATH:-<فارغ>}"
    printf 'locale=%s\n' "${LANG:-<فارغ>}"
  } | while IFS= read -r line; do printf '::error title=env::%s\n' "$line"; done
fi

printf '\n————————————————————————————\n'
printf 'نجح: %d · فشل: %d\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
