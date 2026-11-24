#!/usr/bin/env bash
# ——————————————————————————————————————————————————————————————————————————————
# 10.1.0 — اختبار دليل المكتبات العلمية (scientific_deps.sh)
#
# يتحقق من:
#   ١) خريطة apt صحيحة: numpy ← python3-numpy (وهي **الاسم الحقيقي** في أوبنتو).
#   ٢) تنظيف اسم الحزمة: إسقاط الإضافات والقيود (requests[socks]==2.31 ← requests).
#   ٣) عدم حسّاسية حالة الأحرف (NumPy · NUMPY · numpy سواء).
#   ٤) أسماء الوحدات التي تختلف عن اسم الحزمة (pyyaml ← yaml · pillow ← PIL).
#   ٥) مكتبة مجهولة ⇒ فشل نظيف (لا تخمين ينتج تثبيتًا خاطئًا).
#   ٦) دالّة التمييز «علمية/ثقيلة» تعمل للمعروفة ولا تعمل للمجهولة.
#   ٧) نصيحة الفشل تذكر apt الصحيح **ولا** تذكر pkg كحلّ صالح هنا.
#
# لماذا هذا الاختبار مهم: الخطأ الحقيقي الذي عالجناه كان **نصيحة خاطئة**
# (‏pkg install python-numpy في بيئة أوبنتو). النصيحة الخاطئة لا تُكشفها
# المراجعة، ولا يُكشفها المترجم — تُكشف بالتحقّق من النصّ نفسه.
#
# التشغيل: bash tools/tests/scientific_deps_test.sh
# ——————————————————————————————————————————————————————————————————————————————
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ASSETS="$ROOT/features/terminal/src/main/assets/terminal"

pass=0
fail=0
ok() { printf '  ✔ %s\n' "$1"; pass=$((pass + 1)); }
no() {
  printf '  ✘ %s\n' "$1"
  printf '::error title=scientific_deps_test::%s\n' "$1"
  fail=$((fail + 1))
}

[ -f "$ASSETS/scientific_deps.sh" ] || {
  printf '::error title=scientific_deps_test::الملف غير موجود\n'
  exit 1
}

# shellcheck source=/dev/null
. "$ASSETS/scientific_deps.sh"

echo "— خريطة apt"
for pair in "numpy:python3-numpy" "scipy:python3-scipy" "pandas:python3-pandas" \
  "matplotlib:python3-matplotlib" "sklearn:python3-sklearn"; do
  want="${pair#*:}"
  got="$(mahdi_scientific_apt_for "${pair%%:*}" 2>/dev/null)"
  if [ "$got" = "$want" ]; then ok "${pair%%:*} ← $want"; else no "${pair%%:*} توقّعنا $want وحصلنا '$got'"; fi
done

echo "— تنظيف الاسم"
got="$(mahdi_scientific_apt_for 'requests[socks]==2.31.0' 2>/dev/null)"
[ "$got" = "python3-requests" ] && ok "الإضافات والقيود تُسقط" || no "تنظيف الإضافات أعطى '$got'"

got="$(mahdi_scientific_apt_for 'numpy>=1.20,<2' 2>/dev/null)"
[ "$got" = "python3-numpy" ] && ok "قيود الإصدار تُسقط" || no "تنظيف القيود أعطى '$got'"

echo "— حالة الأحرف"
got="$(mahdi_scientific_apt_for 'NumPy' 2>/dev/null)"
[ "$got" = "python3-numpy" ] && ok "NumPy يطابق" || no "NumPy أعطى '$got'"
got="$(mahdi_scientific_apt_for 'PANDAS' 2>/dev/null)"
[ "$got" = "python3-pandas" ] && ok "PANDAS يطابق" || no "PANDAS أعطى '$got'"

echo "— أسماء الوحدات المخالفة لاسم الحزمة"
for pair in "pyyaml:yaml" "pillow:PIL" "scikit-learn:sklearn" \
  "opencv-python:cv2" "beautifulsoup4:bs4" "mysqlclient:MySQLdb"; do
  want="${pair#*:}"
  got="$(_mahdi_scientific_module "${pair%%:*}" 2>/dev/null)"
  if [ "$got" = "$want" ]; then ok "${pair%%:*} ⇒ $want"; else no "${pair%%:*} توقّعنا $want وحصلنا '$got'"; fi
done

echo "— مكتبة مجهولة"
if mahdi_scientific_apt_for "some-random-package-xyz" >/dev/null 2>&1; then
  no "مكتبة مجهولة يجب ألا تُطابق"
else
  ok "مكتبة مجهولة تُرفض بلا تخمين"
fi

if mahdi_is_native_heavy "numpy"; then ok "numpy تُصنَّف ثقيلة"; else no "numpy لم تُصنَّف ثقيلة"; fi
if mahdi_is_native_heavy "some-random-package-xyz"; then no "المجهولة لا تُصنَّف ثقيلة"; else ok "المجهولة ليست ثقيلة"; fi

echo "— اسم فارغ"
if mahdi_scientific_apt_for "" >/dev/null 2>&1; then no "الاسم الفارغ يجب أن يفشل"; else ok "الاسم الفارغ يفشل بأمان"; fi

echo "— نصيحة الفشل"
advice="$(mahdi_scientific_advice "numpy" 2>&1)"
case "$advice" in
  *"mh pkg install python3-numpy"*) ok "تقترح أمر apt الصحيح" ;;
  *) no "النصيحة لا تذكر 'mh pkg install python3-numpy'" ;;
esac
case "$advice" in
  *"لا داخل Termux"*) ok "تنبّه صراحةً أن نصيحة Termux لا تنطبق" ;;
  *) no "النصيحة لا تنبّه إلى Termux" ;;
esac
# الفخّ الحقيقي: أن تُقدَّم pkg كحلّ بدل apt
case "$advice" in
  *"الحلّ الصحيح"*"pkg install python-numpy"*) no "تقدم pkg install كحلّ صالح — وهو خطأ" ;;
  *) ok "لا تقدم نصيحة Termux كحلّ" ;;
esac

echo "— التكرار لا يُزعج"
export HOME="$(mktemp -d)"
first="$(mahdi_scientific_advice "numpy" 2>&1 | wc -l)"
mahdi_scientific_advise_once "numpy" >/dev/null 2>&1 || true
second="$(mahdi_scientific_advise_once "numpy" 2>&1 | wc -l)"
if [ "$second" -eq 0 ]; then ok "النصيحة تُطبع مرّة واحدة لكل حزمة"; else no "تكرارت النصيحة ($second سطرًا)"; fi
[ "$first" -gt 4 ] && ok "النصيحة الأولى غير فارغة" || no "النصيحة الأولى فارغة"
rm -rf "$HOME"

echo
printf 'النتيجة: %d ناجح · %d فاشل\n' "$pass" "$fail"
[ "$fail" -eq 0 ] || exit 1
