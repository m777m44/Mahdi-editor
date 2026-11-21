#!/usr/bin/env python3
"""9.9.0 — حرس أصول أيقونات VS Code.

لماذا هذا الفحص موجود
----------------------
في 9.8.0 أُضيفت حزمة أيقونات SVG، وكانت أيقونة المجلّد تُرسم في التطبيق
**رقمًا من النقاط المبتورة** لا مجلّدًا. السبب لم يكن في كود Compose ولا في
Coil: ملفّ `folder.svg` نفسه كان يحمل

    width="100%" height="100%"

وأربعة مرشّحات `<filter>` لتوهّج نيون. و`androidsvg` (المستخدم في `SvgDecoder`)
**لا يدعم مرشّحات SVG** ولا يحلّ الأبعاد النسبية بلا حاوية، فكان يرسم الشكل
عشوائيًا داخل مربّع 20dp. النتيجة: ركام أيقونات متكرّر في كل صفّ — عيب بصري
يبدو كخطأ في الواجهة، ومصدره ملفّ أصل واحد.

الفحص هنا يمنع تكراره: يتحقّق أن كل SVG في حزمة الأيقونات

  * يُحلَّل كـ XML سليم،
  * يملك `viewBox`،
  * يملك `width` و`height` **بالبكسل** (لا نسبة مئوية: `androidsvg` لا يحلّها)،
  * خالٍ من `<filter>` و`filter="url(…)"` (غير مدعومة في androidsvg)،
  * وموجود في `manifest.json` (ملفّ غير مُشار إليه = وزن بلا فائدة).

الاستخدام: python3 tools/tests/svg_assets_check.py
يعيد 0 إن كانت كل الأصول سليمة، و1 مع قائمة المشاكل.
"""
import glob
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ICON_DIR = os.path.join(ROOT, 'core', 'main', 'src', 'main', 'assets', 'vscode', 'icons')
MANIFEST = os.path.join(ICON_DIR, 'manifest.json')

PERCENT = re.compile(r'%')


def check_icon(path):
    """يعيد قائمة مشاكل الملف (فارغة = سليم)."""
    name = os.path.basename(path)
    problems = []

    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        return [f'{name}: XML غير صالح — {exc}']

    if not root.get('viewBox'):
        problems.append(f'{name}: لا يوجد viewBox (لا يمكن تحجيمه)')

    width = root.get('width')
    height = root.get('height')
    if not width or not height:
        problems.append(f'{name}: width/height مفقودان — androidsvg لا يستنتجهما')
    elif PERCENT.search(width) or PERCENT.search(height):
        problems.append(
            f'{name}: width="{width}" height="{height}" نسبة مئوية — '
            'androidsvg لا يحلّها فتُرسم الأيقونة عشوائيًا'
        )

    text = open(path, encoding='utf-8').read()
    if '<filter' in text or re.search(r'filter\s*=\s*"url\(', text):
        problems.append(f'{name}: يحتوي مرشّح SVG — غير مدعوم في androidsvg (يُرسم ركامًا)')

    return problems


def manifest_refs():
    with open(MANIFEST, encoding='utf-8') as handle:
        data = json.load(handle)
    refs = set()
    icons = data.get('icons', {})
    for key in ('defaultFile', 'defaultFolder', 'defaultFolderExpanded'):
        if icons.get(key):
            refs.add(icons[key])
    for key in ('folderNames', 'folderNamesExpanded', 'fileNames', 'fileExtensions', 'languageNames'):
        refs.update((icons.get(key) or {}).values())
    return refs


def main():
    if not os.path.isdir(ICON_DIR):
        print(f'❌ مجلد الأيقونات غير موجود: {ICON_DIR}')
        return 1

    files = sorted(glob.glob(os.path.join(ICON_DIR, '*.svg')))
    if not files:
        print('❌ لا توجد أيقونات SVG في الحزمة')
        return 1

    problems = []
    for path in files:
        problems.extend(check_icon(path))

    if os.path.exists(MANIFEST):
        referenced = manifest_refs()
        on_disk = {os.path.basename(p) for p in files}
        missing = sorted(referenced - on_disk)
        orphans = sorted(on_disk - referenced)
        for item in missing:
            problems.append(f'{item}: مشار إليه في manifest.json وغير موجود على القرص')
        for item in orphans:
            problems.append(f'{item}: موجود على القرص وغير مشار إليه في manifest.json')

    if problems:
        print(f'❌ {len(problems)} مشكلة في أصول الأيقونات:')
        for problem in problems:
            print(f'   • {problem}')
        return 1

    print(f'✅ {len(files)} أيقونة SVG سليمة (أبعاد بكسل · بلا مرشّحات · مطابقة للبيان)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
