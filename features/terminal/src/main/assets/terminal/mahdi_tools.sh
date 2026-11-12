#!/bin/bash
# shellcheck shell=bash
# ——————————————————————————————————————————————————————————————————————————————
# mh — مركز أدوات Mahdi-Editor (8.5.0)
#
# أمر واحد يجمع كل ما يحتاجه المطوّر داخل التطبيق:
#
#   mh                نظرة سريعة على البيئة والأوامر
#   mh doctor         فحص شامل ملوّن: النظام · بايثون · pip · Node · الشبكة · المخازن · خوادم اللغة
#   mh doctor --fix   إصلاح ما يمكن إصلاحه تلقائيًا (pip · المخازن · الاختصارات)
#   mh py …           إدارة مفسّرات بايثون (يرث mahdi-python)
#   mh pip …          pip عبر أحدث مفسّر، بمخزن دائم وبلا أي سؤال
#   mh new …          إنشاء مشروع جاهز: py · bot · web · api · html · node
#   mh run …          تشغيل أي ملف عبر محرّك التشغيل الموحّد
#   mh cache          أحجام المخازن الدائمة (prune ينظّف المؤقت فقط)
#   mh version        إصدارات التطبيق والأدوات
#
# لا شبكة في الأوامر المعلوماتية، ولا سؤال في أي أمر، وكل فحص له مهلة قصيرة.
# ——————————————————————————————————————————————————————————————————————————————

LOCAL="${LOCAL:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
export LOCAL

if [ -f "$LOCAL/bin/utils" ]; then
  # shellcheck disable=SC1091
  . "$LOCAL/bin/utils" >/dev/null 2>&1
fi

# بدائل إن كان التثبيت قديمًا وبلا utils — الأمر يبقى صالحًا للعمل
if ! declare -f mahdi_heading >/dev/null 2>&1; then
  M_RESET='' M_BOLD='' M_DIM='' M_SKY='' M_TEAL='' M_MINT='' M_GOLD=''
  M_VIOLET='' M_CORAL='' M_MUTED='' M_WHITE=''
  mahdi_heading() { printf '%s\n' "$1"; }
  mahdi_rule() { printf '%s\n' '──────────────────────────────────'; }
  mahdi_kv() { printf '  %-16s %s\n' "$1" "$2"; }
  mahdi_status() {
    case "$1" in
      ok) printf '  ✔  %s · %s\n' "$2" "$3" ;;
      warn) printf '  !  %s · %s\n' "$2" "$3" ;;
      bad) printf '  ✖  %s · %s\n' "$2" "$3" ;;
      *) printf '  ·  %s · %s\n' "$2" "$3" ;;
    esac
  }
  mahdi_human_size() { printf '%s' "${1:-0} B"; }
  mahdi_path_bytes() { printf '0'; }
  mahdi_have() { command -v "$1" >/dev/null 2>&1; }
  mahdi_net_ok_fast() { return 1; }
  mahdi_ar_plural() { printf '%s %s' "$1" "${4:-${2:-}}"; }
  mahdi_os_name() {
    if [ -r /etc/os-release ]; then
      ( . /etc/os-release 2>/dev/null; printf '%s' "${PRETTY_NAME:-Linux}" )
    else
      printf 'Linux'
    fi
  }
  MAHDI_PY="${MAHDI_PY:-python3}"
  mahdi_python_version() {
    "${MAHDI_PY}" -c 'import sys;print("%d.%d.%d" % sys.version_info[:3])' 2>/dev/null || printf '—'
  }
  info() { printf '▸  %s\n' "$1"; }
  warn() { printf '⚠  %s\n' "$1"; }
  error() { printf '✖  %s\n' "$1"; }
  success() { printf '✔  %s\n' "$1"; }
fi

MH_TOOLS_VERSION="8.5.0"

# ————————————————————————————————— فحص البيئة —————————————————————————————————

_MH_OK=0 _MH_WARN=0 _MH_BAD=0

_mh_doc() {
  local tone="$1" label="$2" value="$3"
  mahdi_status "$tone" "$label" "$value"
  case "$tone" in
    ok) _MH_OK=$((_MH_OK + 1)) ;;
    warn) _MH_WARN=$((_MH_WARN + 1)) ;;
    bad) _MH_BAD=$((_MH_BAD + 1)) ;;
  esac
}

_mh_cores() {
  if command -v nproc >/dev/null 2>&1; then
    nproc 2>/dev/null && return 0
  fi
  grep -c '^processor' /proc/cpuinfo 2>/dev/null || printf '1'
}

_mh_mem() {
  [ -r /proc/meminfo ] || return 1
  awk '/^MemTotal:/ {t=$2} /^MemAvailable:/ {a=$2} END {
         if (t > 0) printf "%.1f متاح من %.1f GB", a/1048576, t/1048576
       }' /proc/meminfo
}

_mh_disk() {
  df -Pk / 2>/dev/null | awk 'NR == 2 { printf "%.1f متاح من %.1f GB", $4/1048576, $2/1048576 }'
}

_mh_other_pythons() {
  local c v out='' n=0
  for c in python3.9 python3.10 python3.11 python3.12 python3.13 python3.14; do
    command -v "$c" >/dev/null 2>&1 || continue
    [ "$c" = "${MAHDI_PY:-python3}" ] && continue
    v="$("$c" -c 'import sys;print("%d.%d" % sys.version_info[:2])' 2>/dev/null)"
    [ -z "$v" ] && continue
    [ -n "$out" ] && out="$out · "
    out="$out$v"
    n=$((n + 1))
    [ "$n" -ge 4 ] && break
  done
  printf '%s' "$out"
}

_mh_pip_version() {
  "${MAHDI_PY:-python3}" -m pip --version 2>/dev/null | awk '{print $2; exit}'
}

_mh_pkg_count() {
  "${MAHDI_PY:-python3}" - 2>/dev/null <<'PY'
import importlib.metadata as meta

try:
    print(len(list(meta.distributions())))
except Exception:
    print(0)
PY
}

# عنوان قسم داخل الفحص: سطر فارغ ثم العنوان، فتبقى الأقسام منفصلة بصريًا
_mh_head() {
  printf '\n'
  mahdi_heading "$1"
}

_mh_doctor_system() {
  _mh_head "النظام"
  _mh_doc ok "نظام التشغيل" "$(mahdi_os_name)"
  _mh_doc info "المعمارية" "$(uname -m 2>/dev/null || printf '—') · $(_mh_cores) أنوية"
  local mem disk
  mem="$(_mh_mem)"
  [ -n "$mem" ] && _mh_doc ok "الذاكرة" "$mem"
  disk="$(_mh_disk)"
  [ -n "$disk" ] && _mh_doc ok "التخزين" "$disk"
}

_mh_doctor_python() {
  _mh_head "بايثون"
  local py="${MAHDI_PY:-python3}"
  if ! command -v "$py" >/dev/null 2>&1; then
    _mh_doc bad "المفسّر" "غير مثبّت — شغّل: mh doctor --fix"
    return 0
  fi

  _mh_doc ok "المفسّر" "$py · $(mahdi_python_version 2>/dev/null || printf '—') · $(command -v "$py" 2>/dev/null)"

  local pipv
  pipv="$(_mh_pip_version)"
  if [ -n "$pipv" ]; then
    _mh_doc ok "pip" "$pipv"
  else
    _mh_doc bad "pip" "غير مثبّت — شغّل: mh doctor --fix"
  fi

  local pkgs
  pkgs="$(_mh_pkg_count)"
  if [ -n "$pkgs" ]; then
    _mh_doc ok "الحزم" "$(mahdi_ar_plural "$pkgs" "حزمة واحدة" "حزمتان" "حزم" "حزمة") في بيئة $py"
  fi

  local others
  others="$(_mh_other_pythons)"
  [ -n "$others" ] && _mh_doc info "إصدارات أخرى" "$others"
}

_mh_doctor_tools() {
  _mh_head "أدوات التطوير"
  local v
  if v="$(node --version 2>/dev/null)"; then
    _mh_doc ok "Node" "$v"
  else
    _mh_doc warn "Node" "غير مثبّت — يُثبَّت تلقائيًا عند تشغيل ملف JavaScript"
  fi
  v="$(npm --version 2>/dev/null)"
  [ -n "$v" ] && _mh_doc ok "npm" "$v"
  v="$(git --version 2>/dev/null)"
  [ -n "$v" ] && _mh_doc ok "Git" "${v#git version }"
  if command -v java >/dev/null 2>&1; then
    v="$(java -version 2>&1 | head -n 1)"
    [ -n "$v" ] && _mh_doc info "Java" "$v"
  fi
  return 0
}

_mh_doctor_net() {
  _mh_head "الشبكة"
  if mahdi_net_ok_fast 2>/dev/null; then
    _mh_doc ok "الاتصال" "متصل — تنزيل الحزم الجديدة متاح"
  else
    _mh_doc warn "الاتصال" "غير متاح — المثبَّت يعمل من المخزن الدائم بلا إنترنت"
  fi
}

_mh_doctor_cache() {
  _mh_head "المخازن الدائمة"
  local dir="${MAHDI_CACHE_DIR:-$HOME/.mahdi/cache}" total=0 n p
  for p in wheels pip npm; do
    n="$(mahdi_path_bytes "$dir/$p")"
    case "$n" in '' | *[!0-9]*) n=0 ;; esac
    total=$((total + n))
    _mh_doc info "$p" "$(mahdi_human_size "$n")"
  done
  _mh_doc ok "الإجمالي" "$(mahdi_human_size "$total") · $dir"
}

_MH_LSP_SERVERS="bash-language-server vscode-css-language-server vscode-eslint-language-server emmet-language-server vscode-html-language-server vscode-json-language-server vscode-markdown-language-server pyright-langserver tsc lemminx"

_mh_doctor_lsp() {
  _mh_head "خوادم اللغة"
  local s found=0 total=0
  for s in $_MH_LSP_SERVERS; do
    total=$((total + 1))
    if [ -x "/usr/bin/$s" ] || command -v "$s" >/dev/null 2>&1; then
      found=$((found + 1))
    fi
  done
  if [ "$found" -gt 0 ]; then
    _mh_doc ok "المثبّت" "$(mahdi_ar_plural "$found" "خادم واحد" "خادمان" "خوادم" "خادم") من $total — الإكمال والتشخيص جاهزان"
  else
    _mh_doc info "المثبّت" "لا شيء بعد — تُثبَّت من: اللغات ← خوادم اللغة"
  fi
}

_mh_doctor_fix() {
  _mh_head "الإصلاح التلقائي"

  if [ -f "$LOCAL/bin/auto_deps" ]; then
    # shellcheck disable=SC1091
    . "$LOCAL/bin/auto_deps" >/dev/null 2>&1
  fi

  if declare -f mahdi_ensure_pip >/dev/null 2>&1 && mahdi_ensure_pip >/dev/null 2>&1; then
    _mh_doc ok "pip" "$(_mh_pip_version)"
  else
    _mh_doc bad "pip" "تعذّر تجهيزه — تحقّق من الاتصال ثم أعد المحاولة"
  fi

  mkdir -p "$HOME/.mahdi/cache/wheels" "$HOME/.mahdi/cache/pip" "$HOME/.mahdi/cache/npm" 2>/dev/null &&
    _mh_doc ok "المخازن" "المجلدات جاهزة"

  # shellcheck disable=SC1091
  [ -f "$LOCAL/bin/aliases" ] && . "$LOCAL/bin/aliases" >/dev/null 2>&1
  if declare -f mahdi_install_aliases >/dev/null 2>&1 && mahdi_install_aliases --force >/dev/null 2>&1; then
    _mh_doc ok "الاختصارات" "أُعيد بناؤها: py · js · ts · run · pip · mh"
  fi

  if declare -f break_stale_apt_locks >/dev/null 2>&1; then
    break_stale_apt_locks >/dev/null 2>&1 && _mh_doc ok "أقفال apt" "سليمة"
  fi

  hash -r 2>/dev/null || true
  return 0
}

_mh_doctor_summary() {
  printf '\n'
  mahdi_rule 34
  printf "  ${M_MINT}✔ %d${M_RESET}   ${M_GOLD}! %d${M_RESET}   ${M_CORAL}✖ %d${M_RESET}\n" \
    "$_MH_OK" "$_MH_WARN" "$_MH_BAD"
  if [ "$_MH_BAD" -gt 0 ]; then
    printf "  ${M_SKY}↳${M_RESET}  %s\n" "أصلح الأساسيات الآن: mh doctor --fix"
  elif [ "$_MH_WARN" -gt 0 ]; then
    printf "  ${M_MUTED}↳  %s${M_RESET}\n" "ما ظهر بعلامة ! اختياري — التطبيق يعمل دونه"
  else
    printf "  ${M_MINT}↳${M_RESET}  %s\n" "كل شيء سليم — لا توجد مشكلة تحتاج تدخّلًا"
  fi
  printf '\n'
}

mh_doctor() {
  local fix=0 net=0 arg
  for arg in "$@"; do
    case "$arg" in
      --fix) fix=1 ;;
      --net) net=1 ;;
      -h | --help)
        mahdi_heading "mh doctor"
        printf "  %s\n" "فحص شامل بلا شبكة، ومهل قصيرة لا تُعلّق الشاشة."
        printf "  %s\n" "mh doctor --fix   يجهّز pip والمخازن والاختصارات تلقائيًا"
        printf "  %s\n" "mh doctor --net   يضيف فحص الاتصال بمهلة ثانيتين"
        return 0
        ;;
    esac
  done

  printf '\n'
  mahdi_rule 34
  mahdi_heading "فحص البيئة"

  _mh_doctor_system
  _mh_doctor_python
  _mh_doctor_tools
  if [ "$net" = "1" ]; then _mh_doctor_net; fi
  _mh_doctor_cache
  _mh_doctor_lsp

  [ "$fix" = "1" ] && _mh_doctor_fix

  _mh_doctor_summary
  return 0
}

# ————————————————————————————————— إنشاء المشاريع —————————————————————————————————

_mh_new_help() {
  mahdi_heading "mh new — مشروع جاهز في ثانية"
  printf "  %s\n" "mh new py   myapp   سكربت بايثون حديث (وسائط + سجلات)"
  printf "  %s\n" "mh new bot  mybot   بوت تلغرام غير متزامن (AsyncTeleBot)"
  printf "  %s\n" "mh new web  mysite  موقع Flask بصفحة عربية متجاوبة"
  printf "  %s\n" "mh new api  myserver  خدمة FastAPI مع /health و/items"
  printf "  %s\n" "mh new html mysite  صفحة HTML/CSS/JS بلا أي اعتماديات"
  printf "  %s\n" "mh new node mycli   مشروع Node.js حديث (ESM)"
  printf '\n'
  printf "  ${M_MUTED}%s${M_RESET}\n" "يُسمح بالحروف اللاتينية والأرقام و - _ . في الاسم."
}

_mh_new_common() {
  local dir="$1" name="$2"
  printf '%s\n' 'node_modules/' '__pycache__/' '*.pyc' '.venv/' '.env' '.DS_Store' 'dist/' 'build/' >"$dir/.gitignore"
  printf '# %s\n\nمشروع أُنشئ بأمر `mh new` داخل Mahdi-Editor.\n\n```bash\ncd %s\n```\n' \
    "$name" "$name" >"$dir/README.md"
}

_mh_new_py() {
  local dir="$1" name="$2"
  cat >"$dir/main.py" <<'PY'
#!/usr/bin/env python3
"""نقطة بداية نظيفة: وسائط، سجلات، ورموز خروج صحيحة."""
from __future__ import annotations

import argparse
import logging

log = logging.getLogger("app")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="سكربت بايثون حديث")
    parser.add_argument("--name", default="العالم", help="اسم للترحيب")
    parser.add_argument("--verbose", action="store_true", help="سجلات مفصّلة")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(levelname)s %(message)s",
    )
    log.info("مرحبًا %s", args.name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
PY
  printf '# لا اعتماديات خارجية — أضف ما تحتاجه هنا\n' >"$dir/requirements.txt"
  _mh_new_common "$dir" "$name"
  printf '\n```bash\nrun main.py\n```\n' >>"$dir/README.md"
}

_mh_new_bot() {
  local dir="$1" name="$2"
  cat >"$dir/bot.py" <<'PY'
#!/usr/bin/env python3
"""بوت تلغرام حديث — pyTelegramBotAPI 4.x مع AsyncTeleBot."""
from __future__ import annotations

import asyncio
import logging
import os

from telebot.async_telebot import AsyncTeleBot

log = logging.getLogger("bot")
TOKEN = os.environ.get("BOT_TOKEN", "").strip()

bot = AsyncTeleBot(TOKEN or "0:missing", parse_mode="HTML")


@bot.message_handler(commands=["start"])
async def start(message) -> None:
    await bot.reply_to(message, "أهلًا بك 👋 — البوت يعمل.")


@bot.message_handler(func=lambda message: True)
async def echo(message) -> None:
    await bot.reply_to(message, message.text or "")


async def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    if not TOKEN:
        raise SystemExit("ضع الرمز أولًا: export BOT_TOKEN=123456:ABC-DEF")
    me = await bot.get_me()
    log.info("يعمل باسم @%s", me.username)
    await bot.infinity_polling(skip_pending=True)


if __name__ == "__main__":
    asyncio.run(main())
PY
  printf 'pyTelegramBotAPI>=4.16.0\n' >"$dir/requirements.txt"
  _mh_new_common "$dir" "$name"
  cat >>"$dir/README.md" <<'MD'
## التشغيل

```bash
export BOT_TOKEN=123456:ABC-DEF
run bot.py
```

الرمز من [@BotFather](https://t.me/BotFather). المكتبة تُثبَّت تلقائيًا عند أول تشغيل.
MD
}

_mh_new_web() {
  local dir="$1" name="$2"
  cat >"$dir/app.py" <<'PY'
#!/usr/bin/env python3
"""موقع Flask صغير — صفحة عربية متجاوبة وفحص صحّة."""
from __future__ import annotations

import os

from flask import Flask, jsonify, render_template_string

app = Flask(__name__)

PAGE = """<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>مرحبًا</title>
<style>
  :root { --sky:#38bdf8; --night:#0a1b2e; --text:#dde3ee; }
  * { box-sizing: border-box; }
  body { margin:0; min-height:100vh; display:grid; place-items:center;
         font-family: system-ui, "Segoe UI", sans-serif;
         background: radial-gradient(circle at 20% 0%, #14324f, var(--night) 60%);
         color: var(--text); }
  main { padding:2rem; text-align:center; }
  h1 { font-size: clamp(1.6rem, 6vw, 2.6rem); margin:0 0 .6rem; color: var(--sky); }
  p { opacity:.85; line-height:1.9; margin:0; }
</style>
</head>
<body>
  <main>
    <h1>مرحبًا بك 👋</h1>
    <p>هذه صفحة Flask تعمل داخل Mahdi-Editor.</p>
  </main>
</body>
</html>"""


@app.get("/")
def index() -> str:
    return render_template_string(PAGE)


@app.get("/health")
def health():
    return jsonify(ok=True)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "5000")), debug=False)
PY
  printf 'flask>=3.0\n' >"$dir/requirements.txt"
  _mh_new_common "$dir" "$name"
  printf '\n```bash\nrun app.py\n```\n' >>"$dir/README.md"
}

_mh_new_api() {
  local dir="$1" name="$2"
  cat >"$dir/main.py" <<'PY'
#!/usr/bin/env python3
"""خدمة FastAPI حديثة — وثائق تلقائية على /docs."""
from __future__ import annotations

from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="__NAME__", version="1.0.0")


class Item(BaseModel):
    name: str
    quantity: int = 1


@app.get("/health")
def health() -> dict:
    return {"ok": True}


@app.get("/items")
def list_items() -> list[Item]:
    return [Item(name="عنصر تجريبي")]


@app.post("/items")
def create_item(item: Item) -> Item:
    return item
PY
  printf 'fastapi>=0.110\nuvicorn[standard]>=0.29\n' >"$dir/requirements.txt"
  _mh_new_common "$dir" "$name"
  cat >>"$dir/README.md" <<'MD'
## التشغيل

```bash
run main.py     # يشغّل uvicorn تلقائيًا على المنفذ 8000
```

الوثائق التفاعلية: `/docs`.
MD
}

_mh_new_html() {
  local dir="$1" name="$2"
  cat >"$dir/index.html" <<'HTML'
<!doctype html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>صفحة عربية</title>
  <style>
    :root { --sky:#38bdf8; --mint:#34d399; --night:#0a1b2e; --text:#dde3ee; }
    * { box-sizing: border-box; }
    body { margin:0; min-height:100vh; display:grid; place-items:center;
           font-family: system-ui, "Segoe UI", sans-serif; color:var(--text);
           background: radial-gradient(circle at 80% 0%, #14324f, var(--night) 60%); }
    main { width:min(92vw, 640px); padding:2rem; text-align:center; }
    h1 { color:var(--sky); font-size:clamp(1.6rem, 7vw, 2.8rem); margin:0 0 .8rem; }
    button { margin-top:1.4rem; padding:.8rem 1.6rem; border:0; border-radius:999px;
             font:inherit; font-weight:700; color:#062033; cursor:pointer;
             background:linear-gradient(120deg, var(--sky), var(--mint)); }
    #count { display:block; margin-top:1rem; font-size:2rem; color:var(--mint); }
  </style>
</head>
<body>
  <main>
    <h1>مرحبًا بك 👋</h1>
    <p>صفحة واحدة بلا اعتماديات — تفتح في معاينة التطبيق مباشرة.</p>
    <button id="tap">اضغط</button>
    <strong id="count">0</strong>
  </main>
  <script>
    let n = 0;
    const out = document.getElementById("count");
    document.getElementById("tap").addEventListener("click", () => {
      out.textContent = String(++n);
    });
  </script>
</body>
</html>
HTML
  _mh_new_common "$dir" "$name"
  printf '\nافتح `index.html` في معاينة المحرّر.\n' >>"$dir/README.md"
}

_mh_new_node() {
  local dir="$1" name="$2"
  cat >"$dir/index.js" <<'JS'
#!/usr/bin/env node
// مشروع Node.js حديث (ESM) بلا أي اعتماديات
import { argv } from "node:process";

const name = argv[2] ?? "العالم";
console.log(`مرحبًا ${name} 👋`);
JS
  printf '{\n  "name": "%s",\n  "version": "1.0.0",\n  "private": true,\n  "type": "module",\n  "main": "index.js",\n  "scripts": { "start": "node index.js" }\n}\n' \
    "$name" >"$dir/package.json"
  _mh_new_common "$dir" "$name"
  printf '\n```bash\nrun index.js\n```\n' >>"$dir/README.md"
}

mh_new() {
  local kind="${1:-}" name="${2:-}"

  case "$kind" in
    "" | -h | --help)
      _mh_new_help
      return 0
      ;;
    py | bot | web | api | html | node) ;;
    *)
      error "نوع غير معروف: $kind"
      _mh_new_help
      return 2
      ;;
  esac

  [ -z "$name" ] && name="$kind-app"
  case "$name" in
    *[!A-Za-z0-9._-]*)
      error "اسم المشروع يقبل الحروف اللاتينية والأرقام و - _ . فقط"
      return 2
      ;;
  esac

  local dir="./$name"
  if [ -e "$dir" ] && [ -n "$(ls -A "$dir" 2>/dev/null)" ]; then
    error "المجلد غير فارغ: $name"
    printf "  ${M_MUTED}%s${M_RESET}\n" "اختر اسمًا آخر: mh new $kind اسم-جديد"
    return 1
  fi

  mkdir -p "$dir" || {
    error "تعذّر إنشاء المجلد: $name"
    return 1
  }

  case "$kind" in
    py) _mh_new_py "$dir" "$name" ;;
    bot) _mh_new_bot "$dir" "$name" ;;
    web) _mh_new_web "$dir" "$name" ;;
    api) _mh_new_api "$dir" "$name" ;;
    html) _mh_new_html "$dir" "$name" ;;
    node) _mh_new_node "$dir" "$name" ;;
  esac

  sed -i "s/__NAME__/${name}/g" "$dir"/* 2>/dev/null || true

  local files size
  files="$(ls -A "$dir" 2>/dev/null | wc -l | tr -d ' ')"
  size="$(mahdi_human_size "$(mahdi_path_bytes "$dir")")"

  printf '\n'
  success "أُنشئ المشروع: ${name}"
  mahdi_kv "النوع" "$kind"
  mahdi_kv "الملفات" "$files · $size"
  printf "\n  ${M_SKY}↳${M_RESET}  ${M_BOLD}cd %s${M_RESET}\n" "$name"
  case "$kind" in
    py) printf "  ${M_SKY}↳${M_RESET}  ${M_BOLD}run main.py${M_RESET}\n" ;;
    bot) printf "  ${M_SKY}↳${M_RESET}  ${M_BOLD}export BOT_TOKEN=...${M_RESET}  ثم  ${M_BOLD}run bot.py${M_RESET}\n" ;;
    web) printf "  ${M_SKY}↳${M_RESET}  ${M_BOLD}run app.py${M_RESET}\n" ;;
    api) printf "  ${M_SKY}↳${M_RESET}  ${M_BOLD}run main.py${M_RESET}  ${M_MUTED}· الوثائق على /docs${M_RESET}\n" ;;
    html) printf "  ${M_SKY}↳${M_RESET}  ${M_BOLD}mh run index.html${M_RESET}\n" ;;
    node) printf "  ${M_SKY}↳${M_RESET}  ${M_BOLD}run index.js${M_RESET}\n" ;;
  esac
  printf '\n'
}

# ————————————————————————————————— أوامر مساعدة —————————————————————————————————

mh_cache() {
  local dir="${MAHDI_CACHE_DIR:-$HOME/.mahdi/cache}" total=0 n p

  mahdi_heading "المخازن الدائمة"
  for p in wheels pip npm; do
    n="$(mahdi_path_bytes "$dir/$p")"
    case "$n" in '' | *[!0-9]*) n=0 ;; esac
    total=$((total + n))
    mahdi_kv "$p" "$(mahdi_human_size "$n")"
  done
  mahdi_kv "الإجمالي" "$(mahdi_human_size "$total")"
  printf "  ${M_MUTED}%s${M_RESET}\n" "$dir"

  case "${1:-}" in
    prune)
      local freed=0 f
      freed=$((freed + $(mahdi_path_bytes "$HOME/.cache/pip")))
      rm -rf "$HOME/.cache/pip" 2>/dev/null || true
      for f in /tmp/mahdi-* /tmp/pip-* /tmp/get-pip.py; do
        [ -e "$f" ] || continue
        freed=$((freed + $(mahdi_path_bytes "$f")))
        rm -rf "$f" 2>/dev/null || true
      done
      printf '\n'
      success "حُرّر $(mahdi_human_size "$freed") من الملفات المؤقتة"
      printf "  ${M_MUTED}%s${M_RESET}\n" "المخازن الدائمة (wheels · pip · npm) لم تُمسّ — لا إعادة تنزيل."
      ;;
    *)
      printf "  ${M_SKY}↳${M_RESET}  %s\n" "mh cache prune   ينظّف المؤقت فقط"
      ;;
  esac
}

mh_version() {
  mahdi_heading "الإصدارات"
  mahdi_kv "التطبيق" "$(cat "$LOCAL/bin/.mahdi_version" 2>/dev/null || printf '—')"
  mahdi_kv "الأدوات" "$MH_TOOLS_VERSION"
  mahdi_kv "بايثون" "$(mahdi_python_version 2>/dev/null || printf '—')  ·  ${MAHDI_PY:-python3}"
  mahdi_kv "pip" "$(_mh_pip_version)"
  mahdi_kv "Node" "$(node --version 2>/dev/null || printf '—')"
  mahdi_kv "Git" "$(git --version 2>/dev/null | sed 's/^git version //' || printf '—')"
}

mh_pip() {
  if [ -f "$LOCAL/bin/auto_deps" ]; then
    # shellcheck disable=SC1091
    . "$LOCAL/bin/auto_deps" >/dev/null 2>&1
  fi
  if declare -f mahdi_ensure_pip >/dev/null 2>&1 && ! mahdi_ensure_pip >/dev/null 2>&1; then
    error "تعذّر تجهيز pip — تحقّق من الاتصال ثم: mh doctor --fix"
    return 1
  fi
  exec "${MAHDI_PY:-python3}" -m pip "$@"
}

mh_py() {
  if [ ! -x "$LOCAL/bin/mahdi-python" ]; then
    error "أداة بايثون غير موجودة — أعد تثبيت الطرفية من الإعدادات"
    return 127
  fi
  exec "$LOCAL/bin/mahdi-python" "$@"
}

mh_run() {
  if [ ! -f "$LOCAL/bin/universal_runner" ]; then
    error "محرّك التشغيل غير موجود — أعد تثبيت الطرفية"
    return 127
  fi

  # 8.5.0: داخل مجلد مشروع `mh run` وحدها تكفي — يختار نقطة البداية المعتادة
  if [ $# -lt 1 ]; then
    local candidate
    for candidate in main.py app.py bot.py index.js index.html main.ts; do
      if [ -f "$candidate" ]; then
        exec bash "$LOCAL/bin/universal_runner" "$candidate"
      fi
    done
    error "اكتب: mh run ملف"
    printf "  ${M_MUTED}%s${M_RESET}\n" "أو شغّل من داخل مجلد مشروع فيه main.py أو index.js"
    return 2
  fi

  exec bash "$LOCAL/bin/universal_runner" "$@"
}

mh_help() {
  printf '\n'
  mahdi_heading "مركز أدوات Mahdi-Editor"
  printf "  ${M_BOLD}%-14s${M_RESET} %s\n" "mh" "نظرة سريعة على البيئة"
  printf "  ${M_BOLD}%-14s${M_RESET} %s\n" "mh doctor" "فحص شامل ملوّن (--fix للإصلاح · --net للشبكة)"
  printf "  ${M_BOLD}%-14s${M_RESET} %s\n" "mh py" "إدارة مفسّرات بايثون (--list · --use · --upgrade)"
  printf "  ${M_BOLD}%-14s${M_RESET} %s\n" "mh pip" "pip عبر أحدث مفسّر وبمخزن دائم"
  printf "  ${M_BOLD}%-14s${M_RESET} %s\n" "mh new" "مشروع جاهز: py · bot · web · api · html · node"
  printf "  ${M_BOLD}%-14s${M_RESET} %s\n" "mh run" "تشغيل ملف — أو نقطة بداية المشروع بلا وسائط"
  printf "  ${M_BOLD}%-14s${M_RESET} %s\n" "mh cache" "أحجام المخازن الدائمة"
  printf "  ${M_BOLD}%-14s${M_RESET} %s\n" "mh version" "إصدارات التطبيق والأدوات"
  printf '\n'
  printf "  ${M_MUTED}%s${M_RESET}\n" "أي أمر يفهم أيضًا الاسم العربي: mh فحص · mh جديد · mh تشغيل"
  printf '\n'
}

mh_home() {
  printf '\n'
  mahdi_rule 34
  mahdi_heading "بيئة العمل"

  local py="${MAHDI_PY:-python3}" pipv nodev lsp=0 s
  if command -v "$py" >/dev/null 2>&1; then
    mahdi_status ok "بايثون" "$(mahdi_python_version 2>/dev/null || printf '—')  ·  $py"
  else
    mahdi_status bad "بايثون" "غير مثبّت — mh doctor --fix"
  fi
  pipv="$(_mh_pip_version)"
  if [ -n "$pipv" ]; then
    mahdi_status ok "pip" "$pipv"
  else
    mahdi_status bad "pip" "غير مثبّت — mh doctor --fix"
  fi
  nodev="$(node --version 2>/dev/null)"
  [ -n "$nodev" ] && mahdi_status ok "Node" "$nodev"
  for s in $_MH_LSP_SERVERS; do
    if [ -x "/usr/bin/$s" ] || command -v "$s" >/dev/null 2>&1; then lsp=$((lsp + 1)); fi
  done
  if [ "$lsp" -gt 0 ]; then
    mahdi_status info "خوادم اللغة" "$(mahdi_ar_plural "$lsp" "خادم واحد" "خادمان" "خوادم" "خادم")"
  else
    mahdi_status info "خوادم اللغة" "لا شيء بعد"
  fi
  mahdi_status info "المخزن" "$(mahdi_human_size "$(mahdi_path_bytes "${MAHDI_CACHE_DIR:-$HOME/.mahdi/cache}")")"

  printf '\n'
  printf "  ${M_SKY}mh doctor${M_RESET}  ${M_MUTED}فحص شامل${M_RESET}      ${M_SKY}mh new py app${M_RESET}  ${M_MUTED}مشروع جديد${M_RESET}\n"
  printf "  ${M_SKY}mh pip -V${M_RESET}  ${M_MUTED}مدير الحزم${M_RESET}     ${M_SKY}mh run main.py${M_RESET}  ${M_MUTED}تشغيل ملف${M_RESET}\n"
  printf "  ${M_MUTED}mh help — كل الأوامر${M_RESET}\n"
  printf '\n'
}

case "${1:-}" in
  "")
    mh_home
    ;;
  doctor | فحص)
    shift
    mh_doctor "$@"
    ;;
  py | python | بايثون)
    shift
    mh_py "$@"
    ;;
  pip)
    shift
    mh_pip "$@"
    ;;
  new | جديد)
    shift
    mh_new "$@"
    ;;
  run | تشغيل)
    shift
    mh_run "$@"
    ;;
  cache | مخزن)
    shift
    mh_cache "$@"
    ;;
  version | -v | --version | إصدار)
    mh_version
    ;;
  help | -h | --help | مساعدة)
    mh_help
    ;;
  *)
    error "أمر غير معروف: $1"
    mh_help
    exit 2
    ;;
esac
