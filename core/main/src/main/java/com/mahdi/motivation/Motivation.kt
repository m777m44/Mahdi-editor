package com.mahdi.motivation

import android.content.Context
import android.content.SharedPreferences
import com.mahdi.utils.application
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** إنجاز يمكن فتحه. */
data class Achievement(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val emoji: String,
)

/** حالة التحفيز الكاملة المعروضة في الواجهة. */
data class MotivationState(
    val streakDays: Int = 0,
    val bestStreak: Int = 0,
    val todaySaves: Int = 0,
    val todayLines: Int = 0,
    val totalSaves: Int = 0,
    val totalRuns: Int = 0,
    val unlocked: Set<String> = emptySet(),
) {
    val dailyGoal: Int
        get() = DAILY_SAVE_GOAL

    val goalProgress: Float
        get() = (todaySaves.toFloat() / DAILY_SAVE_GOAL).coerceIn(0f, 1f)

    val goalReached: Boolean
        get() = todaySaves >= DAILY_SAVE_GOAL

    companion object {
        const val DAILY_SAVE_GOAL = 5
    }
}

/** إنجاز مفتوح حديثًا — يُعرض في بطاقة احتفال مع قصاصات ملوّنة. */
data class Celebration(val achievement: Achievement, val at: Long = System.currentTimeMillis())

/**
 * محرّك التحفيز: سلسلة الأيام، الهدف اليومي، والإنجازات.
 *
 * كل شيء محفوظ محليًا في SharedPreferences، ولا يُرسل أي بيانات خارج الجهاز.
 */
object Motivation {

    private const val PREFS = "mahdi_motivation"
    private const val KEY_DAY = "day"
    private const val KEY_STREAK = "streak"
    private const val KEY_BEST = "best"
    private const val KEY_TODAY_SAVES = "today_saves"
    private const val KEY_TODAY_LINES = "today_lines"
    private const val KEY_TOTAL_SAVES = "total_saves"
    private const val KEY_TOTAL_RUNS = "total_runs"
    private const val KEY_UNLOCKED = "unlocked"

    val catalog: List<Achievement> =
        listOf(
            Achievement("first_save", "البداية", "First save", "احفظ ملفك الأول", "Save your first file", "🎯"),
            Achievement("saves_10", "مثابر", "Persistent", "احفظ 10 ملفات", "Save 10 files", "💪"),
            Achievement("saves_100", "محترف", "Professional", "احفظ 100 ملف", "Save 100 files", "🏅"),
            Achievement("lines_100", "خطّاط", "Scribe", "اكتب 100 سطر في يوم واحد", "Write 100 lines in one day", "✍️"),
            Achievement("streak_3", "ثلاثة أيام", "3-day streak", "استخدم التطبيق 3 أيام متتالية", "Use the app 3 days in a row", "🔥"),
            Achievement("streak_7", "أسبوع كامل", "Full week", "7 أيام متتالية", "7 days in a row", "⚡"),
            Achievement("streak_30", "شهر من الإبداع", "A month of creation", "30 يومًا متتالية", "30 days in a row", "👑"),
            Achievement("first_run", "أول تشغيل", "First run", "شغّل سكربتًا بنجاح", "Run a script successfully", "▶️"),
            Achievement("goal_5", "هدف اليوم", "Daily goal", "أنجز هدف اليوم (5 عمليات حفظ)", "Complete today's goal (5 saves)", "🌟"),
        )

    private val prefs: SharedPreferences?
        get() = application?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(MotivationState())
    val state: StateFlow<MotivationState> = _state.asStateFlow()

    private val _celebration = MutableStateFlow<Celebration?>(null)
    val celebration: StateFlow<Celebration?> = _celebration.asStateFlow()

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun today(): String = dayFormat.format(Date())

    private fun yesterday(): String =
        Calendar.getInstance()
            .apply { add(Calendar.DAY_OF_YEAR, -1) }
            .time
            .let { dayFormat.format(it) }

    /** يُقرأ عند الإقلاع (وعند فتح الشاشة الرئيسية). يحدّث السلسلة إن انقطعت. */
    fun refresh() {
        val p = prefs ?: return
        val savedDay = p.getString(KEY_DAY, null)
        val today = today()

        var streak = p.getInt(KEY_STREAK, 0)
        var todaySaves = p.getInt(KEY_TODAY_SAVES, 0)
        var todayLines = p.getInt(KEY_TODAY_LINES, 0)

        if (savedDay != today) {
            // يوم جديد: إن لم يكن أمس متصلًا تنقطع السلسلة (تُصفَّر الآن لا عند أول حفظ)
            if (savedDay != null && savedDay != yesterday()) streak = 0
            todaySaves = 0
            todayLines = 0
        }

        _state.value =
            MotivationState(
                streakDays = streak,
                bestStreak = p.getInt(KEY_BEST, 0),
                todaySaves = todaySaves,
                todayLines = todayLines,
                totalSaves = p.getInt(KEY_TOTAL_SAVES, 0),
                totalRuns = p.getInt(KEY_TOTAL_RUNS, 0),
                unlocked = p.getStringSet(KEY_UNLOCKED, emptySet()) ?: emptySet(),
            )
    }

    private fun persist() {
        val p = prefs ?: return
        val s = _state.value
        p.edit()
            .putString(KEY_DAY, today())
            .putInt(KEY_STREAK, s.streakDays)
            .putInt(KEY_BEST, maxOf(s.bestStreak, s.streakDays))
            .putInt(KEY_TODAY_SAVES, s.todaySaves)
            .putInt(KEY_TODAY_LINES, s.todayLines)
            .putInt(KEY_TOTAL_SAVES, s.totalSaves)
            .putInt(KEY_TOTAL_RUNS, s.totalRuns)
            .putStringSet(KEY_UNLOCKED, s.unlocked)
            .apply()
    }

    /** يُستدعى بعد كل حفظ ناجح. [lines] عدد أسطر الملف المحفوظ (لقياس الإنتاج). */
    fun onSave(lines: Int = 0) {
        val p = prefs ?: return
        refresh()

        val savedDay = p.getString(KEY_DAY, null)
        val today = today()
        val s = _state.value

        val streak =
            when {
                savedDay == today -> maxOf(s.streakDays, 1)
                savedDay == yesterday() -> s.streakDays + 1
                else -> 1
            }

        _state.value =
            s.copy(
                streakDays = streak,
                bestStreak = maxOf(s.bestStreak, streak),
                todaySaves = s.todaySaves + 1,
                todayLines = s.todayLines + lines.coerceIn(0, 20_000),
                totalSaves = s.totalSaves + 1,
            )
        persist()
        checkAchievements()
    }

    /** يُستدعى بعد كل تشغيل ناجح لسكربت. */
    fun onRun() {
        refresh()
        _state.value = _state.value.copy(totalRuns = _state.value.totalRuns + 1)
        persist()
        checkAchievements()
    }

    private fun checkAchievements() {
        val s = _state.value
        val now = mutableSetOf<String>()

        fun unlockIf(id: String, condition: Boolean) {
            if (condition && id !in s.unlocked) now += id
        }

        unlockIf("first_save", s.totalSaves >= 1)
        unlockIf("saves_10", s.totalSaves >= 10)
        unlockIf("saves_100", s.totalSaves >= 100)
        unlockIf("lines_100", s.todayLines >= 100)
        unlockIf("streak_3", s.streakDays >= 3)
        unlockIf("streak_7", s.streakDays >= 7)
        unlockIf("streak_30", s.streakDays >= 30)
        unlockIf("first_run", s.totalRuns >= 1)
        unlockIf("goal_5", s.todaySaves >= MotivationState.DAILY_SAVE_GOAL)

        if (now.isEmpty()) return

        _state.value = s.copy(unlocked = s.unlocked + now)
        persist()

        // إنجاز واحد لكل مرة حتى لا تتزاحم البطاقات
        now.firstNotNullOfOrNull { id -> catalog.firstOrNull { it.id == id } }?.let {
            _celebration.value = Celebration(it)
        }
    }

    /** يُستدعى بعد إغلاق بطاقة الاحتفال. */
    fun consumeCelebration() {
        _celebration.value = null
    }

    fun resetStreak() {
        _state.value = _state.value.copy(streakDays = 0)
        persist()
    }

    /** اسم اليوم الحالي بالعربية للعرض. */
    fun greetingAr(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 5..11 -> "صباح الخير"
            hour in 12..16 -> "طاب نهارك"
            hour in 17..21 -> "مساء الخير"
            else -> "سهرة موفقة"
        }
    }

    fun greetingEn(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 5..11 -> "Good morning"
            hour in 12..16 -> "Good afternoon"
            hour in 17..21 -> "Good evening"
            else -> "Happy coding"
        }
    }
}
