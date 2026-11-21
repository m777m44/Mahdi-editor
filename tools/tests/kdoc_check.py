#!/usr/bin/env python3
"""9.8.0 — حرس تعليقات Kotlin.

كوتلن **تُداخل** تعليقات الكتل: `/*` داخل `/** … */` يفتح تعليقًا جديدًا،
فإن لم يوجد `*/` مقابل يمتدّ التعليق إلى نهاية الملف ويختفي كل ما بعده من
الشيفرة. حدث هذا فعلًا في 9.8.0: مسار `assets/vscode/themes/*.json` داخل
توثيق صنف أدّى إلى ١٢ خطأ «Unresolved reference» في ملفات أخرى — رسائل
لا تشير إلى السبب الحقيقي إطلاقًا.

هذا الفحص يقرأ كل ملف Kotlin ويتتبّع عمق التعليقات مع تجاهل النصوص الحرفية،
فيكشف التعليق غير المغلق أو الزائد عن الإغلاق قبل أن يصل إلى المترجم.

الاستخدام: python3 tools/tests/kdoc_check.py [ملفات أو مجلدات…]
يعيد 0 إن كانت كلها سليمة، و1 مع قائمة المواضع غير السليمة.
"""
import os
import sys


def scan(path, text):
    """يعيد (عمق التعليق عند نهاية الملف، هل أُغلق تعليق زيادةً)."""
    depth = 0
    i, n = 0, text.__len__()
    while i < n:
        # نصّ ثلاثي العلامات: يُتجاهل كاملًا (يسمح بـ /* بداخله)
        if text[i:i + 3] == '"""':
            end = text.find('"""', i + 3)
            i = (end + 3) if end >= 0 else n
            continue
        c = text[i]
        # نصّ عادي: يُتجاهل كاملًا
        if c == '"':
            i += 1
            while i < n and text[i] != '"':
                if text[i] == '\\':
                    i += 1
                i += 1
            i += 1
            continue
        # حرف: يُتجاهل
        if c == "'":
            i += 1
            while i < n and text[i] != "'":
                if text[i] == '\\':
                    i += 1
                i += 1
            i += 1
            continue
        if text[i:i + 2] == '//':
            while i < n and text[i] != '\n':
                i += 1
            continue
        if text[i:i + 2] == '/*':
            depth += 1
            i += 2
            continue
        if text[i:i + 2] == '*/':
            depth -= 1
            i += 2
            if depth < 0:
                return 0, True
            continue
        i += 1
    return depth, False


def kotlin_files(targets):
    for target in targets:
        if os.path.isfile(target):
            if target.endswith('.kt'):
                yield target
            continue
        for root, _dirs, files in os.walk(target):
            for name in files:
                if name.endswith('.kt'):
                    yield os.path.join(root, name)


def main(argv):
    targets = argv[1:] or ['core', 'features', 'app', 'soraX']
    problems = []
    checked = 0
    for path in kotlin_files(targets):
        try:
            text = open(path, encoding='utf-8', errors='ignore').read()
        except OSError:
            continue
        checked += 1
        depth, over_closed = scan(path, text)
        if over_closed:
            problems.append(f'{path}: «*/» زيادة عن الفتح')
        elif depth != 0:
            problems.append(f'{path}: تعليق كتلة غير مغلق (العمق {depth})')

    print(f'فحص {checked} ملف Kotlin.')
    if problems:
        for line in problems:
            print(f'::error title=تعليقات Kotlin::{line}')
        return 1
    print('كل تعليقات الكتل مغلقة بشكل سليم.')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
