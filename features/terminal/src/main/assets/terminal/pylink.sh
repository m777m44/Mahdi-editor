# shellcheck shell=bash
# ——————————————————————————————————————————————————————————————————————————————
# Mahdi-Editor — حرس الربط الديناميكي لبايثون (9.6.0)
#
# المشكلة التي أبلغ عنها المستخدم:
#
#   CANNOT LINK EXECUTABLE "/data/user/0/com.mahdi.editor/tmp/tmp…/Pyahmed.so":
#   library "libpython3.11.so.1.0" not found: needed by main executable
#
# وهذا **ليس خطأً في السكربت** بل عطل في غلاف بايثون: ملف تنفيذي/غلاف `.so`
# بُني ليعتمد على `libpythonX.Y.so.1.0` لكن المكتبة غير موجودة في مسار بحث
# الرابط الديناميكي (‏LD_LIBRARY_PATH · RPATH · ld.so.cache) داخل الحاوية.
#
# هذا الملف يعالج السبب لا العَرَض، بالترتيب التالي:
#   ١) يكشف رسالة الرابط في مخرجات/ملف الخطأ ويعرف اسم المكتبة المطلوبة.
#   ٢) يبحث عن المكتبة في كل المسارات المعقولة (‏multiarch · /usr/local · /opt
#      · مجلد التطبيق · مجلد الغلاف نفسه).
#   ٣) إن وُجدت: يُثبّت مسارها في LD_LIBRARY_PATH، وينشئ وصلات ثابتة بالاسمين
#      (`libpython3.11.so` و`libpython3.11.so.1.0`)، ويكتب إعداد ld.so.conf
#      ويشغّل ldconfig، ويعدّل RPATH بـpatchelf إن توفّر.
#   ٤) إن لم تُوجد: يُجهّز مفسّر التوزيعة السليم (`python3` من apt) ويُبعد الغلاف
#      المعطوب إلى `.broken-<time>` كي لا يحجب المفسّر السليم في PATH.
#   ٥) يتحقّق دائمًا بالتشغيل الحقيقي: `python3 -c 'print(1)'` — لا يُعلن نجاحًا
#      إلا إذا نفّذ أمرًا فعليًا.
#
# كل هذا صامت عند السلامة (يبقى ملف علامة واحد)، ومحميّ بمهل، وبلا أي انتظار
# لا نهائي. اختبره يدويًا: mh pyfix
# ——————————————————————————————————————————————————————————————————————————————

MAHDI_PYLIB_DIR="${MAHDI_PYLIB_DIR:-${HOME:-/home}/.mahdi/pylib}"
MAHDI_PYLIB_STAMP="${MAHDI_PYLIB_STAMP:-${HOME:-/home}/.mahdi/pylink.ok}"

# —————————————————————— ١) الكشف —————————————————————

# يطبع "soname<TAB>wrapper" من نص فيه رسالة رابط، أو لا شيء.
# يتعرّف على الصيغتين: Android linker وglibc ld.so.
mahdi_py_link_signature() {
  local text="${1:-}"
  [ -n "$text" ] || return 1
  printf '%s\n' "$text" | awk '
    /CANNOT LINK EXECUTABLE|error while loading shared libraries|not found: needed by/ {
      for (i = 1; i <= NF; i++) {
        field = $i
        gsub(/[":]/, "", field)
        if (field ~ /^libpython[0-9.]*\.so/) { print field; exit }
      }
    }
  ' | head -n 1
}

# هل نصّ ما يحمل بصمة عطل ربط بايثون؟
mahdi_py_link_broken() {
  local text="${1:-}"
  case "$text" in
    *"CANNOT LINK EXECUTABLE"* | *"not found: needed by"* | *"error while loading shared libraries"*)
      [ -n "$(mahdi_py_link_signature "$text")" ]
      ;;
    *) return 1 ;;
  esac
}

# يطبع أرقام إصدارات libpython المطلوبة في نص (‏3.11 …) — قد تكون أكثر من واحد
mahdi_py_link_versions() {
  printf '%s\n' "${1:-}" |
    grep -oE 'libpython[0-9]+\.[0-9]+' |
    sed 's/^libpython//' |
    sort -u
}

# —————————————————————— ٢) البحث —————————————————————

# مجلدات مرشّحة للمكتبة المشتركة (الأرجح أولًا). تُطبع سطرًا لكل مجلد موجود.
mahdi_py_lib_dirs() {
  local d
  # 9.6.0: مجلد مكتبات التطبيق الأصلي (NATIVE_LIB_DIR) يمرّره التطبيق لكل جلسة،
  # وهو المكان الذي توجد فيه أي مكتبة مضمَّنة مع APK. كان خارج البحث تمامًا.
  for d in \
    "${NATIVE_LIB_DIR:-}" "${MAHDI_PY_LIB_DIR:-}" \
    /usr/lib/aarch64-linux-gnu /usr/lib/arm-linux-gnueabihf /usr/lib/x86_64-linux-gnu \
    /usr/lib64 /usr/lib /usr/local/lib /usr/local/lib64 \
    /lib/aarch64-linux-gnu /lib/x86_64-linux-gnu /lib64 /lib \
    /opt/python/lib /opt/conda/lib /opt/venv/lib \
    "${LOCAL:-/home}/lib" "${LOCAL:-/home}/../lib" \
    "${HOME:-/home}/.local/lib" "${HOME:-/home}/.mahdi/pylib" \
    "${MAHDI_PY_WRAPPER_DIR:-}" "${PRIVATE_DIR:-}/lib"; do
    [ -n "$d" ] && [ -d "$d" ] && printf '%s\n' "$d"
  done
}

# يبحث عن ملف مكتبة باسم soname. يطبع المسار الكامل عند النجاح.
# $1 = الاسم (مثل libpython3.11.so.1.0) أو رقم إصدار (3.11) للبحث الحرّ.
mahdi_py_find_lib() {
  local want="${1:-}"
  [ -n "$want" ] || return 1
  local dir path

  # ١) المسارات المعتادة (سريع، بلا find)
  case "$want" in
    lib*)
      while IFS= read -r dir; do
        for path in "$dir/$want" "$dir/${want%%.so*}.so"; do
          [ -f "$path" ] && { printf '%s\n' "$path"; return 0; }
        done
      done < <(mahdi_py_lib_dirs)
      ;;
  esac

  # ٢) بحث حرّ عن أي libpython برقم الإصدار المطلوب
  local version="$want"
  case "$version" in
    libpython*) version="$(printf '%s' "$version" | sed -E 's/^libpython([0-9]+\.[0-9]+).*/\1/')" ;;
  esac
  if printf '%s' "$version" | grep -qE '^[0-9]+\.[0-9]+$'; then
    local -a roots=(/usr /lib /opt "${HOME:-/home}" "${LOCAL:-/home}/lib")
    [ -n "${MAHDI_PY_WRAPPER_DIR:-}" ] && roots+=("$MAHDI_PY_WRAPPER_DIR")
    [ -n "${NATIVE_LIB_DIR:-}" ] && roots+=("$NATIVE_LIB_DIR")
    path="$(find "${roots[@]}" -maxdepth 4 -name "libpython${version}.so*" -type f 2>/dev/null | head -n 1)"
    [ -n "$path" ] && { printf '%s\n' "$path"; return 0; }
  fi

  # ٣) أي libpython على الجهاز (كحلّ أخير: قد تكون نسخة أقرب)
  path="$(find /usr /lib /opt -maxdepth 4 -name 'libpython3*.so*' -type f 2>/dev/null | head -n 1)"
  [ -n "$path" ] && { printf '%s\n' "$path"; return 0; }
  return 1
}

# —————————————————————— ٣) الإصلاح —————————————————————

# محرّر PATH: يضيف مجلدًا في المقدّمة مرة واحدة فقط
mahdi_py_path_prepend() {
  local dir="$1"
  [ -n "$dir" ] || return 0
  # ‏${LD_LIBRARY_PATH:-} لأن بعض الصدَفات تُشغَّل بـ`set -u`
  case ":${LD_LIBRARY_PATH:-}:" in
    *":$dir:"*) ;;
    *) export LD_LIBRARY_PATH="$dir${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" ;;
  esac
}

# يثبّت مكتبة وجدها في مجلد ثابت بالاسمين، ويحدّث مسار البحث وإعدادات النظام.
# $1 = مسار المكتبة، $2 = الاسم المطلوب (soname) إن عُرف
mahdi_py_install_lib() {
  local source="$1" want="${2:-}"
  [ -f "$source" ] || return 1
  mkdir -p "$MAHDI_PYLIB_DIR" 2>/dev/null || return 1

  local base version soname
  base="$(basename "$source")"
  # libpython3.11.so.1.0 → 3.11
  version="$(printf '%s' "$base" | sed -E 's/^libpython([0-9]+\.[0-9]+).*/\1/')"
  soname="${want:-$base}"
  case "$soname" in libpython*.so*) ;; *) soname="$base" ;; esac

  # وصلات ثابتة: الاسم الكامل (‏soname) والاسم القصير (للمترجم والربط)
  ln -sf "$source" "$MAHDI_PYLIB_DIR/$base" 2>/dev/null || true
  ln -sf "$source" "$MAHDI_PYLIB_DIR/$soname" 2>/dev/null || true
  if printf '%s' "$version" | grep -qE '^[0-9]+\.[0-9]+$'; then
    ln -sf "$source" "$MAHDI_PYLIB_DIR/libpython${version}.so" 2>/dev/null || true
  fi

  mahdi_py_path_prepend "$MAHDI_PYLIB_DIR"

  # إعداد نظام دائم (يُقرأ في كل جلسة جديدة) — آمن إن لم يكن /etc قابلًا للكتابة
  if [ -w /etc ] 2>/dev/null; then
    mkdir -p /etc/ld.so.conf.d 2>/dev/null || true
    printf '%s\n' "$MAHDI_PYLIB_DIR" >/etc/ld.so.conf.d/mahdi-python.conf 2>/dev/null || true
  fi
  if command -v ldconfig >/dev/null 2>&1; then
    ldconfig >/dev/null 2>&1 || true
  fi
  return 0
}

# يعدّل RPATH/RUNPATH لملف تنفيذي إن توفّر patchelf (يتجاوز الحاجة للمتغيّرات).
mahdi_py_patch_rpath() {
  local target="$1"
  [ -f "$target" ] || return 1
  command -v patchelf >/dev/null 2>&1 || return 1
  local current
  current="$(patchelf --print-rpath "$target" 2>/dev/null || true)"
  case "$current" in
    *"$MAHDI_PYLIB_DIR"*) return 0 ;;
  esac
  local updated
  if [ -n "$current" ]; then
    updated="$MAHDI_PYLIB_DIR:$current"
  else
    updated="$MAHDI_PYLIB_DIR"
  fi
  patchelf --set-rpath "$updated" "$target" >/dev/null 2>&1 || return 1
  # سجلّ ما عُدِّل كي يُمكن التراجع عنه لاحقًا
  mkdir -p "${HOME:-/home}/.mahdi" 2>/dev/null || true
  printf '%s\n' "$target" >>"${HOME:-/home}/.mahdi/patched_rpath" 2>/dev/null || true
  return 0
}

# القاعدة النهائية: هل يعمل بايثون فعلًا؟ (لا مجرّد وجود الملف)
mahdi_py_works() {
  local bin="${1:-${MAHDI_PY:-python3}}"
  [ -n "$bin" ] || return 1
  command -v "$bin" >/dev/null 2>&1 || [ -x "$bin" ] || return 1
  local out rc=0
  out="$("$bin" -c 'print("MAHDI_PY_OK")' 2>&1)" || rc=$?
  [ "$rc" -eq 0 ] && printf '%s' "$out" | grep -q 'MAHDI_PY_OK'
}

# يعيد مفسّر بايثون سليمًا (موجودًا في PATH) — يُطبَع اسمه، أو لا شيء.
mahdi_py_first_working() {
  local c
  for c in "${MAHDI_PY:-python3}" python3 python3.13 python3.12 python3.11 python3.10 python3.9 python; do
    [ -n "$c" ] || continue
    command -v "$c" >/dev/null 2>&1 || continue
    if mahdi_py_works "$c"; then
      printf '%s\n' "$c"
      return 0
    fi
  done
  return 1
}

# يبعد غلافًا معطوبًا عن PATH (يُعاد تسميته، لا يُحذف) كي لا يحجب المفسّر السليم.
mahdi_py_set_aside() {
  local target="$1"
  [ -n "$target" ] || return 1
  [ -f "$target" ] || return 1
  case "$target" in
    /usr/bin/* | /bin/* | /usr/local/bin/python*) ;;
  esac
  local aside="${target}.broken-$(date +%s 2>/dev/null || printf '0')"
  mv "$target" "$aside" 2>/dev/null || return 1
  hash -r 2>/dev/null || true
  printf '%s\n' "$aside"
  return 0
}

# يُبقي مفسّرًا يعمل: يعيد 0 ويطبع اسمه، أو يعيد 1 مع سبب.
mahdi_py_ensure_working() {
  local working
  if working="$(mahdi_py_first_working)"; then
    printf '%s\n' "$working"
    return 0
  fi
  return 1
}

# —————————————————————— ٤) الأمر الجامع —————————————————————

# mahdi_py_link_fix [ملف خطأ أو نص] [الغلاف]
# يعيد: 0 = صار بايثون يعمل، 2 = لا مفسّر سليم (يحتاج تثبيتًا)، 1 = فشل آخر.
mahdi_py_link_fix() {
  local source="${1:-}" wrapper="${2:-}"
  local text="" soname="" version="" lib=""

  if [ -n "$source" ]; then
    if [ -f "$source" ]; then
      text="$(tail -c 20000 "$source" 2>/dev/null || true)"
    else
      text="$source"
    fi
  fi

  soname="$(mahdi_py_link_signature "$text" || true)"
  version="$(mahdi_py_link_versions "$text" | head -n 1)"

  # مجلد الغلاف نفسه أولى مواضع البحث: المكتبة غالبًا بجانبه
  if [ -n "$wrapper" ]; then
    MAHDI_PY_WRAPPER_DIR="$(dirname "$wrapper" 2>/dev/null || true)"
    export MAHDI_PY_WRAPPER_DIR
  fi

  # ① المكتبة موجودة في مكان ما؟ نصلها بدل البحث عنها.
  if [ -n "$soname" ]; then
    lib="$(mahdi_py_find_lib "$soname" || true)"
  fi
  if [ -z "$lib" ] && [ -n "$version" ]; then
    lib="$(mahdi_py_find_lib "$version" || true)"
  fi

  if [ -n "$lib" ]; then
    mahdi_py_install_lib "$lib" "$soname" >/dev/null 2>&1 || true
    [ -n "$wrapper" ] && mahdi_py_patch_rpath "$wrapper" >/dev/null 2>&1 || true
    if [ -n "$wrapper" ]; then
      mahdi_py_link_check_wrapper "$wrapper" && return 0
    fi
    mahdi_py_ensure_working >/dev/null 2>&1 && return 0
    # المكتبة رُبطت لكن الغلاف ما زال لا يعمل: نُبعد الغلاف ونستعمل المفسّر السليم
  fi

  # ② لا مكتبة أو لا فائدة منها: مفسّر التوزيعة هو الحلّ الجذري
  if ! mahdi_py_first_working >/dev/null 2>&1; then
    if declare -f mahdi_apt_install >/dev/null 2>&1; then
      mahdi_apt_install python3 python3-venv >/dev/null 2>&1 || true
    elif command -v apt-get >/dev/null 2>&1; then
      DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends python3 \
        </dev/null >/dev/null 2>&1 || true
    fi
  fi

  # ③ الغلاف المعطوب يُبعد دائمًا حتى لا يبقى في PATH أمام المفسّر السليم
  if [ -n "$wrapper" ] && mahdi_py_link_check_wrapper "$wrapper" 2>/dev/null; then
    return 0
  fi
  if [ -n "$wrapper" ] && [ -f "$wrapper" ]; then
    mahdi_py_set_aside "$wrapper" >/dev/null 2>&1 || true
  elif [ -n "$soname" ] && [ -n "$version" ]; then
    # غلاف باسم python/pyahmed داخل مجلد الأوامر: يُبعد إن كان هو المعطوب
    local candidate
    for candidate in \
      "${LOCAL:-/home}/bin/python3" "${LOCAL:-/home}/bin/python" \
      "${LOCAL:-/home}/bin/Pyahmed.so" "${LOCAL:-/home}/bin/pyahmed"; do
      [ -f "$candidate" ] || continue
      mahdi_py_link_check_wrapper "$candidate" 2>/dev/null && continue
      mahdi_py_set_aside "$candidate" >/dev/null 2>&1 || true
    done
  fi

  if mahdi_py_ensure_working >/dev/null 2>&1; then
    return 0
  fi
  return 2
}

# هل يعمل هذا الغلاف فعلًا؟ (تشغيل حقيقي، لا فحص ملف)
mahdi_py_link_check_wrapper() {
  local target="$1"
  [ -n "$target" ] || return 1
  [ -e "$target" ] || return 1
  local out rc=0
  out="$("$target" --version 2>&1)" || rc=$?
  [ "$rc" -eq 0 ] && ! mahdi_py_link_broken "$out"
}

# —————————————————————— ٥) الحرس السريع —————————————————————

# فحص خفيف محفوظ بعلامة: لا يُشغّل شيئًا إن نجح سابقًا.
# يعيد 0 إذا كان بايثون يعمل (أو أُصلح)، و1 إذا تعذّر.
mahdi_py_ensure() {
  local fresh="${MAHDI_PY_FORCE:-0}"
  if [ "$fresh" != "1" ] && [ -f "$MAHDI_PYLIB_STAMP" ]; then
    local age=""
    # عمر العلامة يُحسب بأداة النظام أولًا — لا نستدعي بايثون (قد يكون هو المعطوب)
    if command -v stat >/dev/null 2>&1; then
      local mtime now
      mtime="$(stat -c %Y "$MAHDI_PYLIB_STAMP" 2>/dev/null || true)"
      now="$(date +%s 2>/dev/null || true)"
      case "${mtime:-x}${now:-x}" in
        *[!0-9]*) age="" ;;
        *) age=$((now - mtime)) ;;
      esac
    fi
    if [ -z "$age" ]; then
      age="$("${MAHDI_PY:-python3}" - "$MAHDI_PYLIB_STAMP" 2>/dev/null <<'PYAGE'
import os, sys, time
try:
    print(int(time.time() - os.path.getmtime(sys.argv[1])))
except Exception:
    print(10 ** 9)
PYAGE
)"
    fi
    case "${age:-}" in
      '' | *[!0-9]*) age=999999999 ;;
    esac
    # العلامة صالحة ليوم واحد، وتُلغى فورًا إن كان المفسّر نفسه لا يعمل
    if [ "$age" -lt 86400 ]; then
      return 0
    fi
  fi

  local bin="${MAHDI_PY:-python3}" out rc=0
  out="$("$bin" -c 'print("MAHDI_PY_OK")' 2>&1)" || rc=$?
  if [ "$rc" -eq 0 ] && printf '%s' "$out" | grep -q 'MAHDI_PY_OK'; then
    mkdir -p "$(dirname "$MAHDI_PYLIB_STAMP")" 2>/dev/null || true
    : >"$MAHDI_PYLIB_STAMP" 2>/dev/null || true
    return 0
  fi

  # المفسّر لا يعمل: هل السبب ربط؟
  if mahdi_py_link_broken "$out"; then
    mahdi_py_link_fix "$out" "$(command -v "$bin" 2>/dev/null || true)" >/dev/null 2>&1
  else
    mahdi_py_link_fix "" "$(command -v "$bin" 2>/dev/null || true)" >/dev/null 2>&1
  fi

  local working
  if working="$(mahdi_py_ensure_working)"; then
    export MAHDI_PY="$working"
    mkdir -p "$(dirname "$MAHDI_PYLIB_STAMP")" 2>/dev/null || true
    : >"$MAHDI_PYLIB_STAMP" 2>/dev/null || true
    return 0
  fi
  return 1
}

# يطبع سطرًا عربيًا واحدًا يشرح الحالة — يُستخدم حين يفشل كل شيء.
mahdi_py_link_reason() {
  printf '%s' "مكتبة بايثون المشتركة (libpython) مفقودة من مسار الرابط — أُعيد تجهيز المفسّر تلقائيًا"
}
