#!/usr/bin/env python3
"""9.8.0 — يبني حزم المحرّر المورَّدة من VS Code (ثيمات + أيقونات أنواع الملفات).

نأخذ أصول `m777m44/vs-code-heroes` (‏.apk مُفكَّك، الأصول فقط) ونُعيد إنتاجها
بصيغة نظيفة يقرأها Mahdi-Editor مباشرة:

  core/main/src/main/assets/vscode/themes/<id>.json     ثيمات جاهزة (خاناتنا + رموز TextMate)
  core/main/src/main/assets/vscode/icons/manifest.json  حزمة أيقونات أنواع الملفات
  core/main/src/main/assets/vscode/icons/*.svg          أيقونات SVG (تُرسم بـAndroidSVG)

التشغيل:
  python3 tools/vscode-import/import_assets.py --source "/path/to/vs code mabile/vs code mabile/assets"

مصدران مدعومان:
  * ثيم VS Code بصيغة `{"colors": {...}, "tokenColors": [...]}`
  * ثيم TextMate بصيغة plist (‏`<key>settings</key><array>`)

لا نستورد أي مفتاح توقيع أو dex أو jar — الأصول المرئية فقط.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "core/main/src/main/assets/vscode")

# ————————————————————————————————— أدوات لون —————————————————————————————————


def parse_hex(raw):
    """#RGB · #RGBA · #RRGGBB · #RRGGBBAA ← int 0xAARRGGBB (None إن كان غير صالح)."""
    if not raw:
        return None
    v = str(raw).strip().lstrip("#")
    if len(v) == 3:
        v = "".join(c * 2 for c in v)
    if len(v) == 4:
        v = "".join(c * 2 for c in v)
    if len(v) == 6:
        v = "FF" + v
    if len(v) != 8 or any(c not in "0123456789abcdefABCDEF" for c in v):
        return None
    # VS Code يكتب RRGGBBAA، tmTheme يكتب AARRGGBB. إن كانت الشفافية في الطرف نُقدّمها.
    if v[6:].upper() in ("FF", "00") and v[0:2].upper() not in ("FF",):
        v = v[6:] + v[0:6]
    return int(v, 16)


def to_hex(value):
    return "#%08X" % (value & 0xFFFFFFFF)


def lerp(a, b, t):
    ar, ag, ab = (a >> 16) & 255, (a >> 8) & 255, a & 255
    br, bg, bb = (b >> 16) & 255, (b >> 8) & 255, b & 255
    return (
        0xFF000000
        | (int(ar + (br - ar) * t) << 16)
        | (int(ag + (bg - ag) * t) << 8)
        | int(ab + (bb - ab) * t)
    )


def is_dark(value):
    r, g, b = (value >> 16) & 255, (value >> 8) & 255, value & 255
    return (0.299 * r + 0.587 * g + 0.114 * b) < 140


# ————————————————————————————————— قراءة الثيمات —————————————————————————————————

PLIST_PAIR = re.compile(r"<key>\s*([^<]+?)\s*</key>\s*<(?:string|integer)>\s*([^<]*?)\s*</(?:string|integer)>", re.S)


def read_tmtheme(text):
    """يُعيد (name, settings[]) من plist، حيث settings أقوام: scope/foreground/background/fontStyle."""
    name = None
    m = re.search(r"<key>\s*name\s*</key>\s*<string>([^<]*)</string>", text)
    if m:
        name = m.group(1).strip()
    blocks = re.findall(r"<dict>(.*?)</dict>", text, re.S)
    settings = []
    for block in blocks:
        pairs = {}
        for k, v in PLIST_PAIR.findall(block):
            pairs[k.strip()] = v.strip()
        # أزواج القاموس الداخلي (‏settings) تُرفع للخارج لأننا نُسطّح
        inner_start = block.find("<key>settings</key>")
        if inner_start != -1:
            inner = block[inner_start:]
            for k, v in PLIST_PAIR.findall(inner):
                if k.strip() in ("foreground", "background", "fontStyle", "caret", "selection"):
                    pairs.setdefault(k.strip(), v.strip())
        entry = {}
        if "scope" in pairs:
            scopes = [s.strip() for s in pairs["scope"].split(",") if s.strip()]
            entry["scope"] = scopes
        values = {}
        for key in ("foreground", "background", "fontStyle"):
            if key in pairs and pairs[key]:
                values[key] = pairs[key]
        if values:
            entry["settings"] = values
        if entry.get("scope") or entry.get("settings"):
            settings.append(entry)
    # ترتيب tmTheme حاسم: القاعدة العامة أولًا ثم الخاصة، وهو ما تفعله الملفات أصلًا.
    return name, settings


def read_vscode_json(text):
    """يقرأ صيغتَي JSON: صيغة VS Code (`colors`+`tokenColors`) وصيغة tmTheme-as-JSON (`settings[]`)."""
    data = json.loads(text)
    name = data.get("name")
    settings = []
    colors = data.get("colors") or {}
    for key in ("editor.background", "editor.foreground", "editorLineNumber.foreground",
                "editor.selectionBackground", "editorCursor.foreground"):
        if key in colors:
            settings.append({"_ui": key, "value": colors[key]})

    raw_entries = []
    if isinstance(data.get("settings"), list):
        raw_entries = data["settings"]
    elif isinstance(data.get("tokenColors"), list):
        raw_entries = data["tokenColors"]

    for entry in raw_entries:
        if not isinstance(entry, dict):
            continue
        scope = entry.get("scope")
        if isinstance(scope, str):
            scope = [s.strip() for s in scope.split(",") if s.strip()]
        out = {}
        if scope:
            out["scope"] = scope
        if entry.get("settings"):
            out["settings"] = entry["settings"]
        if out:
            settings.append(out)
    return name, settings, colors


def build_theme(path):
    text = open(path, encoding="utf-8", errors="replace").read()
    colors = {}
    if text.lstrip().startswith("{"):
        name, settings, colors = read_vscode_json(text)
    else:
        name, settings = read_tmtheme(text)

    # استخراج القاعدة العامة: أول إدخال بلا scope يحمل background
    bg = fg = None
    for entry in settings:
        if entry.get("_ui") == "editor.background":
            bg = parse_hex(entry["value"])
        elif entry.get("_ui") == "editor.foreground":
            fg = parse_hex(entry["value"])
        elif "scope" not in entry and "settings" in entry:
            s = entry["settings"]
            bg = bg or parse_hex(s.get("background"))
            fg = fg or parse_hex(s.get("foreground"))
    if bg is None:
        for key, val in colors.items():
            if key.endswith("background") or key in ("editor.background",):
                bg = parse_hex(val)
                if bg:
                    break
    if bg is None:
        # بعض الثيمات تُلوّن كل قاعدة بلا قاعدة عامة → نستنتج من الغالبيّة
        bg = 0xFF1E1E1E
    if fg is None:
        fg = 0xFFDDDDDD if parse_hex and is_dark(bg) else 0xFF222222

    dark = is_dark(bg)
    accent = parse_hex(colors.get("button.background")) or parse_hex(colors.get("focusBorder"))
    accent = accent or lerp(bg, fg, 0.55)
    surface = parse_hex(colors.get("sideBar.background")) or parse_hex(colors.get("activityBar.background"))
    surface = surface or lerp(bg, fg, 0.07)
    secondary = parse_hex(colors.get("statusBar.background")) or lerp(bg, accent, 0.30)
    outline = parse_hex(colors.get("editorLineNumber.foreground")) or lerp(bg, fg, 0.22)

    # رموز TextMate بصيغة VS Code (تُمرَّر إلى محرّك soraX)
    tokens = [s for s in settings if "scope" in s or ("settings" in s and "_ui" not in s)]
    # الرموز التي تستخدم foreground/background داخل settings تُطبَّع للشكل الذي يفهمه المحرّك
    tokens_out = []
    for entry in tokens:
        clean = {"settings": {k: v for k, v in entry.get("settings", {}).items() if k in ("foreground", "background", "fontStyle")}}
        if entry.get("scope"):
            clean["scope"] = entry["scope"]
        if clean.get("settings") or clean.get("scope"):
            tokens_out.append(clean)
    tokens_out.sort(key=lambda e: 0 if "scope" not in e else 1)

    # لون الشخصية: إن لم يُصرّح الثيم بألوان واجهة، نأخذه من قواعد التلوين نفسها
    # (لون الكلمات المفتاحية = الأساسي، لون النصوص = الثانوي) — فيبقى للثيم هويّته
    # (دراكولا بنفسجي، الهاوية أزرق) بدل رمادي عام.
    def scope_color(*needles):
        for entry in tokens_out:
            scopes = entry.get("scope") or []
            if any(n in sc for sc in scopes for n in needles):
                fg_hex = entry.get("settings", {}).get("foreground")
                val = parse_hex(fg_hex)
                if val is not None:
                    return val
        return None

    ac = accent if colors.get("button.background") or colors.get("focusBorder") else None
    ac = ac or scope_color("keyword", "storage.type", "entity.name.function", "constant.numeric")
    sec = scope_color("string", "constant.character", "entity.name.tag")
    if ac is not None:
        accent = ac
        primary = ac
    if sec is not None:
        secondary = sec
    # تمييز قراءة الواجهة: إن كان الأساسي قريبًا من الخلفية نُبعده
    if abs(((accent >> 16) & 255) - ((bg >> 16) & 255)) < 24 and abs(((accent >> 8) & 255) - ((bg >> 8) & 255)) < 24:
        accent = lerp(bg, fg, 0.55)
        primary = accent
    surface = surface if colors.get("sideBar.background") or colors.get("activityBar.background") else lerp(bg, fg, 0.10)
    outline = outline if colors.get("editorLineNumber.foreground") else lerp(bg, fg, 0.26)

    return {
        "name": name or os.path.splitext(os.path.basename(path))[0],
        "background": to_hex(bg),
        "surface": to_hex(surface),
        "primary": to_hex(accent),
        "secondary": to_hex(secondary),
        "accent": to_hex(accent),
        "text": to_hex(fg),
        "outline": to_hex(outline),
        "dark": dark,
        "tokenColors": tokens_out,
    }


# ————————————————————————————————— الأيقونات —————————————————————————————————

# اسم ملف المصدر ← (اسم الملف/الامتدادات المستهدفة، معرّف داخلي)
ICON_MAP = {
    "pyfile.svg": (["py", "pyw", "pyi"], "python"),
    "cfile.svg": (["c", "h"], "c"),
    "cppfile.svg": (["cpp", "cc", "cxx", "hpp", "hxx", "hh"], "cpp"),
    "cssfile.svg": (["css", "scss", "sass", "less"], "css"),
    "dartfile.svg": (["dart"], "dart"),
    "htmlfile.svg": (["html", "htm", "xhtml"], "html"),
    "jsfile.svg": (["js", "mjs", "cjs", "jsx"], "javascript"),
    "jsonfile.svg": (["json", "json5", "jsonc"], "json"),
    "javafile.svg": (["java", "kt", "kts", "gradle"], "java"),
    "xmlfile.svg": (["xml", "xsd", "plist", "svg"], "xml"),
    "shellfile.svg": (["sh", "bash", "zsh", "fish"], "shell"),
    "filepdf.svg": (["pdf"], "pdf"),
    "filefont.svg": (["ttf", "otf", "woff", "woff2"], "font"),
    "jarfile.svg": (["jar", "class"], "jar"),
    "dexfile.svg": (["dex", "apk", "aab"], "android"),
    "ini.svg": (["ini", "cfg", "conf", "properties", "toml"], "config"),
    "filegradle.svg": (["gradle", "pro"], "gradle"),
    "ninjafile.svg": (["ninja"], "ninja"),
    "vsproj.svg": (["sln", "csproj", "vbproj"], "project"),
    "vs.svg": (["code-workspace"], "vscode"),
    "swb.svg": (["swb"], "swb"),
    "SkpL.svg": (["skp"], "sketchup"),
    "foldervs.svg": (["vscode"], "folder-vscode"),
}

FOLDER_ICON = "folder.svg"
DEFAULT_ICON = "default_file.svg"

# الملفات بأسماء ثابتة (‏VS Code يُلوّنها) — تُكتب بصيغة الحزمة القياسية
FILE_NAME_ICONS = {
    "package.json": "json", "package-lock.json": "json", "tsconfig.json": "json",
    "requirements.txt": "text", "readme.md": "text", ".gitignore": "text",
    "build.gradle": "gradle", "build.gradle.kts": "gradle", "settings.gradle": "gradle",
    "main.py": "python", "app.py": "python", "setup.py": "python",
    "dockerfile": "text", "makefile": "text", "cmakelists.txt": "text",
    "androidmanifest.xml": "xml", "pyproject.toml": "config", "setup.cfg": "config",
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", required=True, help="مجلد assets في حزمة vs-code-heroes")
    args = ap.parse_args()

    src = args.source
    if not os.path.isdir(src):
        print("✖ مجلد المصدر غير موجود:", src, file=sys.stderr)
        return 2

    os.makedirs(os.path.join(OUT, "themes"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "icons"), exist_ok=True)

    # ——— الثيمات ———
    theme_specs = [
        ("darcula", "textmate/darcula.json", "دراكولا · Darcula"),
        ("abyss", "textmate/abyss-color-theme.json", "الهاوية · Abyss"),
        ("quiet-light", "textmate/QuietLight.tmTheme", "الضوء الهادئ · Quiet Light"),
    ]
    written = []
    for theme_id, rel, label in theme_specs:
        path = os.path.join(src, rel)
        if not os.path.isfile(path):
            print("  تخطّي (غير موجود):", rel)
            continue
        data = build_theme(path)
        data["id"] = theme_id
        data["label"] = label
        out = os.path.join(OUT, "themes", theme_id + ".json")
        with open(out, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
        written.append((theme_id, len(data["tokenColors"]), os.path.getsize(out)))
    for tid, ntok, size in written:
        print(f"  ثيم {tid}: {ntok} قاعدة تلوين، {size} بايت")

    # ——— الأيقونات ———
    ext_map, name_map, copied = {}, {}, []
    for src_name, (exts, icon_id) in ICON_MAP.items():
        s = os.path.join(src, src_name)
        if not os.path.isfile(s):
            print("  تخطّي أيقونة (غير موجودة):", src_name)
            continue
        dst_name = icon_id + ".svg"
        with open(s, encoding="utf-8", errors="replace") as f:
            svg = f.read()
        with open(os.path.join(OUT, "icons", dst_name), "w", encoding="utf-8") as f:
            f.write(svg)
        copied.append(dst_name)
        for ext in exts:
            ext_map[ext] = dst_name

    for src_name, icon_id in ((FOLDER_ICON, "folder"), (DEFAULT_ICON, "default")):
        s = os.path.join(src, src_name)
        if os.path.isfile(s):
            with open(s, encoding="utf-8", errors="replace") as f:
                svg = f.read()
            with open(os.path.join(OUT, "icons", icon_id + ".svg"), "w", encoding="utf-8") as f:
                f.write(svg)
            copied.append(icon_id + ".svg")

    # أيقونة نصّ افتراضية بسيطة (لا توجد في المصدر) — تُرسم بمسارات فقط
    text_svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">'
        '<path d="M20.4 2H5v28h22V8.6zM7 28V4h12v6h6v18z" style="fill:#9aa4b2"/>'
        '<path d="M10 15h12v2H10zm0 4h12v2H10zm0-8h6v2h-6z" style="fill:#c9d1dc"/></svg>'
    )
    with open(os.path.join(OUT, "icons", "text.svg"), "w", encoding="utf-8") as f:
        f.write(text_svg)
    copied.append("text.svg")

    for file_name, icon_id in FILE_NAME_ICONS.items():
        name_map[file_name] = icon_id + ".svg"

    manifest = {
        "id": "vscode-heroes",
        "name": "أيقونات VS Code · VS Code Heroes",
        "author": {"displayName": "Mahdi-Editor", "github": "m777m44"},
        "version": "1.0.0",
        "description": "أيقونات أنواع الملفات بأسلوب VS Code: بايثون وسي وجافا وJSON والمجلّدات. "
                       "أصول مرئية مأخوذة من github.com/m777m44/vs-code-heroes "
                       "(نصوص SVG فقط — بلا dex ولا jar ولا مفاتيح توقيع).",
        "tags": ["vscode", "icons", "file-types", "colored"],
        "license": "MIT",
        "applyTint": False,
        "icons": {
            "defaultFile": "default.svg",
            "defaultFolder": "folder.svg",
            "defaultFolderExpanded": "folder.svg",
            "folderNames": {"vscode": "folder-vscode.svg", ".vscode": "folder-vscode.svg"},
            "folderNamesExpanded": {"vscode": "folder-vscode.svg", ".vscode": "folder-vscode.svg"},
            "fileNames": dict(sorted(name_map.items())),
            "fileExtensions": dict(sorted(ext_map.items())),
        },
    }
    with open(os.path.join(OUT, "icons", "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)

    print(f"  أيقونات: {len(copied)} ملف SVG، {len(ext_map)} امتدادًا، {len(name_map)} اسمًا")
    print("✔ تمّت الكتابة في", OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
