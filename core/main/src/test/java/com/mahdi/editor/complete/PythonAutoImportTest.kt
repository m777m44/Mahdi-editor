package com.mahdi.editor.complete

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 9.8.0 — اختبارات «الاستيراد التلقائي وترتيبه» في محرّر بايثون.
 *
 * كل حالة هنا هي سيناريو حقيقي من الكتابة على الهاتف: كتبتَ `requests.get`
 * فطلع `NameError` عند التشغيل، أو نسختَ كودًا من الإنترنت فصار فيه
 * `import os` مرّات، أو استوردت وحدة داخلها `"json"` كنصّ فظنّناها مستخدمة.
 */
class PythonAutoImportTest {

    // ————————————————————— كشف النقص —————————————————————

    @Test
    fun missingModuleIsDetected() {
        val scan = PythonAutoImport.scan("r = requests.get(\"https://example.com\")")
        assertEquals(listOf("import requests"), scan.missing)
    }

    @Test
    fun alreadyImportedIsNotSuggested() {
        val scan = PythonAutoImport.scan("import json\ndata = json.loads(\"{}\")")
        assertTrue("json مستورد أصلًا", scan.missing.none { it.contains("json") })
    }

    @Test
    fun aliasImportCounts() {
        // `import json as j`: الوحدة json متاحة، فلا نقترح إضافة ثانية
        val scan = PythonAutoImport.scan("import json as j\ndata = json.loads(\"{}\")")
        assertTrue(scan.missing.none { it == "import json" })
    }

    @Test
    fun fromImportCounts() {
        val scan = PythonAutoImport.scan("from json import loads\ndata = json.loads(\"1\")")
        assertTrue(scan.missing.none { it == "import json" })
    }

    @Test
    fun stringLiteralIsNotUsage() {
        // `"json"` داخل نصّ لا يعني استعمال وحدة json
        val scan = PythonAutoImport.scan("name = \"json\"\nprint(name)")
        assertTrue(scan.missing.isEmpty())
    }

    @Test
    fun commentIsNotUsage() {
        val scan = PythonAutoImport.scan("# نستخدم json لاحقًا\nx = 1")
        assertTrue(scan.missing.isEmpty())
    }

    @Test
    fun memberAccessDoesNotImport() {
        // `self.json` عضو في الصنف، ليس استيرادًا لـjson
        val code = "class C:\n    def __init__(self, json):\n        self.json = json\n"
        val scan = PythonAutoImport.scan(code)
        assertTrue("self.json لا يستورد شيئًا: ${scan.missing}", scan.missing.none { it == "import json" })
    }

    @Test
    fun definedFunctionIsNotImport() {
        val scan = PythonAutoImport.scan("def requests():\n    pass\n\nrequests()")
        assertTrue(scan.missing.isEmpty())
    }

    @Test
    fun multipleMissingImportsPreservePriority() {
        val scan =
            PythonAutoImport.scan(
                "a = json.loads('{}')\nr = requests.get('http://x')\np = Path('/tmp')",
            )
        assertEquals(3, scan.missing.size)
        // الترتيب بحسب ترتيب الظهور في الكود: json ثم requests ثم Path
        assertEquals("import json", scan.missing[0])
        assertEquals("import requests", scan.missing[1])
        assertEquals("from pathlib import Path", scan.missing[2])
    }

    @Test
    fun builtinsAreNotImported() {
        val scan = PythonAutoImport.scan("print(len([1, 2, 3]))\nx = str(5) + json.dumps({})")
        assertEquals(listOf("import json"), scan.missing)
    }

    @Test
    fun typeHintsImportIsDetected() {
        val scan = PythonAutoImport.scan("from typing import List\n\ndef f(x: List[int]) -> None:\n    pass")
        assertTrue(scan.missing.isEmpty())
    }

    // ————————————————————— الإضافة —————————————————————

    @Test
    fun importAddedAfterExistingImports() {
        val code = "import os\nimport sys\n\ndata = json.loads('{}')\n"
        val out = PythonAutoImport.addMissing(code)
        assertTrue(out.lines().contains("import json"))
        // يجب أن تُضاف قبل الاستخدام وقبل الأسطر الفارغة الأولى
        val indexOfJson = out.lines().indexOf("import json")
        val indexOfUse = out.lines().indexOfFirst { it.contains("json.loads") }
        assertTrue("الاستيراد قبل الاستخدام: $indexOfJson < $indexOfUse", indexOfJson in 0 until indexOfUse)
    }

    @Test
    fun importAddedAtTopWhenNoImportsExist() {
        val code = "data = json.loads('{}')\nprint(data)"
        val out = PythonAutoImport.addMissing(code)
        assertEquals("import json", out.lines().first())
    }

    @Test
    fun importAddedAfterModuleDocstring() {
        val code = "\"\"\"وصف الوحدة.\"\"\"\ndata = json.loads('{}')"
        val lines = PythonAutoImport.addMissing(code).lines()
        assertEquals("\"\"\"وصف الوحدة.\"\"\"", lines[0])
        assertEquals("import json", lines[1])
    }

    @Test
    fun importAddedAfterShebangAndEncoding() {
        val code = "#!/usr/bin/env python3\n# -*- coding: utf-8 -*-\ndata = json.loads('{}')"
        val lines = PythonAutoImport.addMissing(code).lines()
        assertEquals("#!/usr/bin/env python3", lines[0])
        assertEquals("# -*- coding: utf-8 -*-", lines[1])
        assertEquals("import json", lines[2])
    }

    @Test
    fun noChangeWhenNothingMissing() {
        val code = "import json\ndata = json.loads('{}')"
        assertEquals(code, PythonAutoImport.addMissing(code))
    }

    @Test
    fun addedImportIsUsableInOrder() {
        // ترتيب الأسطر بعد الإضافة: بعد آخر import وقبل أول استخدام
        val code = "import os\n\nx = request_helper()\ndef request_helper():\n    return 1\n"
        val out = PythonAutoImport.addMissing(code)
        assertEquals(code, out) // request_helper معرَّفة في الملف، لا استيراد
    }

    // ————————————————————— الترتيب —————————————————————

    @Test
    fun stdlibComesBeforeThirdParty() {
        val sorted =
            PythonAutoImport.sortImportLines(
                listOf("import requests", "import os", "import numpy"),
            )
        // os قياسية (0) فتجيء أولًا؛ numpy وrequests خارجيتان فبعدها أبجديًا
        assertEquals(listOf("import os", "import numpy", "import requests"), sorted)
    }

    @Test
    fun groupedByOrigin() {
        val sorted =
            PythonAutoImport.sortImportLines(
                listOf("from .models import User", "import requests", "import json", "import mylib"),
            )
        assertEquals(0, PythonAutoImport.groupOf(sorted[0])) // قياسية
        assertEquals("import json", sorted[0])
        assertEquals("import mylib", sorted[1]) // خارجية
        assertEquals("import requests", sorted[2]) // خارجية وبعد mylib أبجديًا
        assertEquals("from .models import User", sorted[3]) // نسبية أخيرًا
    }

    @Test
    fun duplicatesAreRemoved() {
        val sorted = PythonAutoImport.sortImportLines(listOf("import os", "import os", "import sys"))
        assertEquals(listOf("import os", "import sys"), sorted)
    }

    @Test
    fun sortImportsKeepsCommentAndCodeOrder() {
        val code = "#!/usr/bin/env python3\nimport requests\nimport os\n# ملاحظة\nx = 1\n"
        val out = PythonAutoImport.sortImports(code)
        val lines = out.lines()
        assertEquals("#!/usr/bin/env python3", lines[0])
        assertEquals("import os", lines[1])
        assertEquals("import requests", lines[2])
        assertTrue(lines.contains("# ملاحظة"))
        assertTrue(lines.contains("x = 1"))
    }

    @Test
    fun sortImportsLeavesSingleImportUntouched() {
        val code = "# تعليق\nimport os\nprint(os.getcwd())\n"
        assertEquals(code, PythonAutoImport.sortImports(code))
    }

    @Test
    fun sortImportsHandlesFromImports() {
        val code = "from flask import Flask\nimport json\nimport os\n\napp = Flask(__name__)\n"
        val out = PythonAutoImport.sortImports(code)
        val lines = out.lines()
        assertEquals("import json", lines[0])
        assertEquals("import os", lines[1])
        assertEquals("from flask import Flask", lines[2])
    }

    @Test
    fun sortImportsIdempotent() {
        val once = PythonAutoImport.sortImports("import requests\nimport os\nimport json\n")
        val twice = PythonAutoImport.sortImports(once)
        assertEquals(once, twice)
    }

    // ————————————————————— أدوات —————————————————————

    @Test
    fun stripRemovesStringsAndComments() {
        val stripped = PythonAutoImport.stripStringsAndComments("x = \"json\" # requests\n")
        assertFalse(stripped.contains("json"))
        assertFalse(stripped.contains("requests"))
    }

    @Test
    fun stripPreservesLineCount() {
        val code = "a = 1\nb = \"two\"\nc = 3\n"
        assertEquals(code.lines().size, PythonAutoImport.stripStringsAndComments(code).lines().size)
    }

    @Test
    fun tripleQuoteBlockIsStripped() {
        val code = "x = \"\"\"\nimport json\nrequests\n\"\"\"\ny = 1"
        val scan = PythonAutoImport.scan(code)
        assertTrue("لا استيراد من داخل نصّ ثلاثي: ${scan.missing}", scan.missing.isEmpty())
    }

    @Test
    fun indentedImportsAreNeverHoisted() {
        // استيراد داخل دالة تأخير تحميل مقصود — رفعه يغيّر معنى البرنامج
        val code = "import sys\n\n\ndef slow():\n    import requests\n    return requests.get('/x')\n"
        assertEquals(code, PythonAutoImport.sortImports(code))
        assertFalse(PythonAutoImport.isTopLevelImport("    import requests"))
        assertTrue(PythonAutoImport.isTopLevelImport("import requests"))
    }

    @Test
    fun indentedImportsSurviveSortingOfTopLevelOnes() {
        val code = "import requests\nimport os\n\n\ndef f():\n    import json\n    return json\n"
        val out = PythonAutoImport.sortImports(code)
        // السطر المزاح باقٍ في مكانه حرفيًا
        assertTrue(out.lines().any { it == "    import json" })
        assertEquals("import os", out.lines()[0])
        assertEquals("import requests", out.lines()[1])
    }

    @Test
    fun importLineDetection() {
        assertTrue(PythonAutoImport.isImportLine("import os"))
        assertTrue(PythonAutoImport.isImportLine("  from json import loads"))
        assertFalse(PythonAutoImport.isImportLine("# import os"))
        assertFalse(PythonAutoImport.isImportLine("x = import_os()"))
        assertFalse(PythonAutoImport.isImportLine("importlib.reload(x)"))
    }
}
