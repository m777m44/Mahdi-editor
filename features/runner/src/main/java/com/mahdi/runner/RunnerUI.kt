package com.mahdi.runner

import kotlinx.coroutines.flow.MutableStateFlow

object RunnerUI {
    val runnersToShow = MutableStateFlow<List<RunnableOption>>(emptyList())

    val showRunnerDialog = MutableStateFlow(false)

    /**
     * 9.2.0 — «معاينة رابط»: نافذة إدخال عنوان تُفتح داخل متصفّح المعاينة.
     *
     * العنوان المطلوب يُمرَّر هنا (من الحافظة تلقائيًا إن كانت تحتوي رابطًا)،
     * وتُقرأ القيمة مرّة واحدة عند فتح النافذة.
     */
    val showLinkDialog = MutableStateFlow(false)

    var pendingLink: String = ""
}
