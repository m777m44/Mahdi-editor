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
    i, n = 0, len(text)
    while i < n:
        two = text[i:i + 2]
        # التعليقات أولًا: داخل التعليق لا معنى للنصوص الحرفية، فعلامة `"`
        # في جملة مثل ("command's") كانت تفتح «نصًّا» يبتلع `*/` ويُنتج
        # إنذارًا كاذبًا. الترتيب هنا هو الفرق بين فحص صادق وفحص مزعج.
        if two == '//':
            while i < n and text[i] != '\n':
                i += 1
            continue
        if two == '/*':
            depth += 1
            i += 2
            continue
        if two == '*/':
            depth -= 1
            i += 2
            if depth < 0:
                return 0, True
            continue
        # نصّ ثلاثي العلامات
        if text[i:i + 3] == '"""':
            end_q = text.find('"""', i + 3)
            i = (end_q + 3) if end_q >= 0 else n
            continue
        # نصّ عادي (سطر واحد)
        if text[i] == '"':
            i += 1
            while i < n and text[i] != '"' and text[i] != '\n':
                if text[i] == '\\':
                    i += 1
                i += 1
            i += 1
            continue
        # حرف: محرف واحد أو محرف مهروب، لا يمتدّ عبر سطرين
        if text[i] == "'":
            if i + 3 <= n and text[i + 2] == "'" and text[i + 1] != '\n':
                i += 3
                continue
            if i + 4 <= n and text[i + 1] == '\\' and text[i + 3] == "'" and text[i + 2] != '\n':
                i += 4
                continue
            i += 1
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
