#!/usr/bin/env python3
"""9.7.0 — يحوّل نتائج اختبارات الوحدة الفاشلة إلى تعليقات في تبويب Checks.

سبب وجود هذا الملف: كان فشل الاختبارات في CI يظهر كسطر واحد «unit tests failed»
بلا اسم اختبار ولا رسالة، فيضيع وقت طويل في قراءة سجل بناء كامل. GitHub يقرأ
سطور `::error title=…::message` من مخرجات الخطوة ويحوّلها إلى annotations
مرتبطة بالملف والسطر — أي أن سبب الفشل يظهر أمام المراجع مباشرة.

يقرأ تقارير JUnit XML من `core/main/build/test-results/testDebugUnitTest/`.
لا يعتمد على أي حزمة خارجية، ويعمل محليًا أيضًا:

    python3 tools/ci/annotate_tests.py
"""

from __future__ import annotations

import glob
import os
import sys
import xml.etree.ElementTree as ET

RESULTS_GLOB = "core/main/build/test-results/testDebugUnitTest/*.xml"
MAX_SUMMARY = 700


def fail_cases(path: str):
    """يولّد (اسم الاختبار، الرسالة) لكل حالة فاشلة في تقرير JUnit واحد."""
    try:
        root = ET.parse(path).getroot()
    except Exception as error:  # تقرير تالف أو غير مكتمل
        print(f"::error title=test-results::{os.path.basename(path)}: {error}")
        return

    for case in root.iter("testcase"):
        failure = case.find("failure")
        if failure is None:
            failure = case.find("error")
        if failure is None:
            continue

        classname = case.get("classname") or "unknown"
        name = case.get("name") or "unknown"
        message = failure.get("message") or failure.text or "فشل بلا رسالة"
        message = " ".join(message.split())[:MAX_SUMMARY]
        # `::error` سطر واحد فقط: أي سطر جديد يفسد التنسيق، وقد نُظِّف أعلاه.
        print(f"::error title=unit-test::{classname}.{name} — {message}")


def main() -> int:
    reports = sorted(glob.glob(RESULTS_GLOB))
    if not reports:
        print("::error title=unit-test::لم تُنتج أي تقارير — فشل Gradle قبل تشغيل الاختبارات")
        return 0

    before = len(reports)
    for path in reports:
        fail_cases(path)
    print(f"عدد تقارير الاختبار المقروءة: {before}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
