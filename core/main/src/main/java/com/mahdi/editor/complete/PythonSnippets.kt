package com.mahdi.editor.complete

/**
 * 9.7.0 — مقتطفات بايثون الجاهزة (نظير «Snippets» في Pydroid).
 *
 * كل مقتطف نصّ كامل يعمل عند لصقه كما هو: لا `pass` ولا `TODO` ولا مسافة
 * بادئة خاطئة. تُطلب من **لوحة الأوامر** (`Ctrl+Shift+P` ← «مقتطف»)،
 * فالمستخدم لا يحفظ اختصارات ولا يفتح نافذة إضافية.
 *
 * الإزاحة (`indent`) هي **عدد المسافات البادئة** لكل سطر من سطور المقتطف
 * داخل الكود الحالي — تُضاف عند الإدراج كي لا ينكسر الملف داخل دالة.
 * عدد المسافات يُشتق من إعدادات المحرّر (`Settings.tab_size`) لا من ثابت هنا.
 */
object PythonSnippets {

    /**
     * مقتطف واحد جاهز.
     *
     * @property title العنوان العربي كما يظهر في لوحة الأوامر.
     * @property hint سطر تلميح قصير (متى تستخدمه).
     * @property body نصّ المقتطف بلا إزاحة خارجية.
     * @property cursorOffset موضع المؤشر من بداية النص بعد الإدراج (نسبةً إلى آخر occurrence من [anchor])…
     *   للتبسيط: نضع المؤشر في نهاية آخر سطر من المقتطف افتراضيًا.
     */
    data class Snippet(
        val title: String,
        val hint: String,
        val body: String,
    ) {
        /** أوّل سطر يُستخدم كمعرّف مستقر في لوحة الأوامر والاختبارات. */
        val key: String get() = body.lineSequence().first().trim().take(40)
    }

    /** كل المقتطفات، مرتّبة من الأكثر استخدامًا إلى الأخصّ. */
    val ALL: List<Snippet> =
        listOf(
            Snippet(
                title = "طباعة مع تنسيق",
                hint = "print مع f-string جاهز",
                body = "name = \"Mahdi\"\nprint(f\"مرحبًا {name}\")",
            ),
            Snippet(
                title = "دالة مع نوع الإرجاع",
                hint = "def + type hints + docstring",
                body = "def function_name(arg: str) -> str:\n    \"\"\"وصف الدالة.\"\"\"\n    return arg",
            ),
            Snippet(
                title = "صنف كامل",
                hint = "__init__ و self و __repr__",
                body =
                    "class MyClass:\n" +
                        "    \"\"\"وصف الصنف.\"\"\"\n\n" +
                        "    def __init__(self, value: int = 0) -> None:\n" +
                        "        self.value = value\n\n" +
                        "    def __repr__(self) -> str:\n" +
                        "        return f\"MyClass({self.value})\"",
            ),
            Snippet(
                title = "قراءة ملف نصي",
                hint = "with open — إغلاق تلقائي آمن",
                body = "with open(\"file.txt\", \"r\", encoding=\"utf-8\") as f:\n    content = f.read()",
            ),
            Snippet(
                title = "كتابة ملف نصي",
                hint = "with open — كتابة بترميز واضح",
                body = "with open(\"file.txt\", \"w\", encoding=\"utf-8\") as f:\n    f.write(\"text\")",
            ),
            Snippet(
                title = "طلب HTTP",
                hint = "requests.get مع فحص الحالة والاستثناء",
                body =
                    "import requests\n\n" +
                        "try:\n" +
                        "    response = requests.get(\"https://example.com\", timeout=15)\n" +
                        "    response.raise_for_status()\n" +
                        "    data = response.json()\n" +
                        "except requests.RequestException as error:\n" +
                        "    print(f\"فشل الطلب: {error}\")",
            ),
            Snippet(
                title = "حلقة مع عدّاد",
                hint = "enumerate بدل range(len())",
                body = "for index, item in enumerate(items, start=1):\n    print(index, item)",
            ),
            Snippet(
                title = "معالجة استثناء",
                hint = "try/except/finally مع رسالة واضحة",
                body =
                    "try:\n" +
                        "    result = risky_call()\n" +
                        "except ValueError as error:\n" +
                        "    print(f\"قيمة غير صحيحة: {error}\")\n" +
                        "finally:\n" +
                        "    cleanup()",
            ),
            Snippet(
                title = "نقطة دخول السكربت",
                hint = "‏if __name__ == \"__main__\"",
                body = "def main() -> None:\n    print(\"start\")\n\n\nif __name__ == \"__main__\":\n    main()",
            ),
            Snippet(
                title = "قاعدة بيانات SQLite",
                hint = "اتصال + جدول + إدخال + استعلام",
                body =
                    "import sqlite3\n\n" +
                        "with sqlite3.connect(\"data.db\") as connection:\n" +
                        "    cursor = connection.cursor()\n" +
                        "    cursor.execute(\"CREATE TABLE IF NOT EXISTS items (id INTEGER PRIMARY KEY, name TEXT)\")\n" +
                        "    cursor.execute(\"INSERT INTO items (name) VALUES (?)\", (\"عنصر\",))\n" +
                        "    for row in cursor.execute(\"SELECT id, name FROM items\"):\n" +
                        "        print(row)",
            ),
            Snippet(
                title = "بوت تلغرام (getUpdates)",
                hint = "long polling بلا مكتبات خارجية",
                body =
                    "import json\n" +
                        "import urllib.request\n\n" +
                        "TOKEN = \"ضع_التوكن_هنا\"\n" +
                        "API = f\"https://api.telegram.org/bot{TOKEN}\"\n\n\n" +
                        "def get_updates(offset: int | None = None) -> list[dict]:\n" +
                        "    url = f\"{API}/getUpdates?timeout=30\"\n" +
                        "    if offset is not None:\n" +
                        "        url += f\"&offset={offset}\"\n" +
                        "    with urllib.request.urlopen(url, timeout=40) as response:\n" +
                        "        return json.load(response).get(\"result\", [])\n\n\n" +
                        "offset = None\n" +
                        "while True:\n" +
                        "    for update in get_updates(offset):\n" +
                        "        offset = update[\"update_id\"] + 1\n" +
                        "        message = update.get(\"message\", {})\n" +
                        "        text = message.get(\"text\")\n" +
                        "        chat = message.get(\"chat\", {}).get(\"id\")\n" +
                        "        if text and chat:\n" +
                        "            print(f\"رسالة من {chat}: {text}\")",
            ),
            Snippet(
                title = "خيط خلفي آمن",
                hint = "threading.Thread مع daemon",
                body =
                    "import threading\n\n\n" +
                        "def worker() -> None:\n" +
                        "    print(\"working\")\n\n\n" +
                        "thread = threading.Thread(target=worker, daemon=True)\n" +
                        "thread.start()\n" +
                        "thread.join()",
            ),
            Snippet(
                title = "قراءة الوسائط",
                hint = "argparse مع أوصاف عربية",
                body =
                    "import argparse\n\n" +
                        "parser = argparse.ArgumentParser(description=\"وصف البرنامج\")\n" +
                        "parser.add_argument(\"path\", help=\"مسار الملف\")\n" +
                        "args = parser.parse_args()\n" +
                        "print(args.path)",
            ),
            Snippet(
                title = "اختبار سريع",
                hint = "دالة assert بلا إطار اختبار",
                body = "def test_example() -> None:\n    assert 1 + 1 == 2\n\n\ntest_example()\nprint(\"نجحت الاختبارات\")",
            ),
        )

    /** مقتطف بالمعرّف المستقر (أول سطر)، للاختبارات والاستدعاء البرمجي. */
    fun byKey(key: String): Snippet? = ALL.firstOrNull { it.key == key }

    /** عناوين فريدة — تُستخدم كمعرّفات في لوحة الأوامر. */
    fun uniqueTitles(): Boolean = ALL.map { it.title }.distinct().size == ALL.size

    /**
     * يُزيح كل سطر من [body] بمقدار [indent] مسافة، وينظّف الأسطر الفارغة من
     * المسافات الزائدة (وإلا صارت مسافات معلّقة تُغضب `flake8`).
     */
    fun indented(
        body: String,
        indent: Int,
    ): String {
        if (indent <= 0) return body
        val pad = " ".repeat(indent)
        return body
            .lineSequence()
            .joinToString("\n") { line -> if (line.isBlank()) "" else pad + line }
    }
}
