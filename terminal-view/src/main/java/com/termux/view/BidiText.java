package com.termux.view;

/**
 * تقسيم سطر الطرفية إلى مقاطع حسب اتجاه الكتابة (عربي/لاتيني).
 *
 * الطرفية شبكة خانات ثابتة العرض، ولذلك كانت الحروف العربية تُرسم من اليسار لليمين
 * وبشكل منفصل: «ةيبرعلا» أو «ت ي ب ث ت» بدل «العربية» و«تثبيت».
 *
 * هذه الفئة تُنتج المقاطع بترتيبها المنطقي، ويقوم {@link TerminalRenderer} برسم كل مقطع
 * بالاتجاه الصحيح (RTL/LTR) مع تشكيل الحروف الأصلي من Android، فتظهر الكلمات متّصلة ومرتّبة.
 */
final class BidiText {

    private BidiText() {}

    /** مقطع نصّي متّصل الاتجاه. */
    static final class Segment {
        final int start;
        final int length;
        final boolean rtl;

        Segment(int start, int length, boolean rtl) {
            this.start = start;
            this.length = length;
            this.rtl = rtl;
        }
    }

    private static final Segment[] EMPTY = new Segment[0];
    private static final int NEUTRAL = -1;
    private static final int LTR = 0;
    private static final int RTL = 1;

    /** هل يحتوي النص على حرف عربي/عبري؟ (الطريق السريع يبقى كما هو إن لم يوجد) */
    static boolean hasRtl(char[] text, int start, int count) {
        final int end = start + count;
        for (int i = start; i < end; i++) {
            if (isRtlChar(text[i])) return true;
        }
        return false;
    }

    private static boolean isRtlChar(char c) {
        return (c >= 0x0590 && c <= 0x05FF)      // العبرية
                || (c >= 0x0600 && c <= 0x06FF)  // العربية
                || (c >= 0x0750 && c <= 0x077F)  // العربية الإضافية
                || (c >= 0x08A0 && c <= 0x08FF)  // العربية الممتدة-أ
                || (c >= 0xFB50 && c <= 0xFDFF)  // صور العرض العربية-أ
                || (c >= 0xFE70 && c <= 0xFEFF); // صور العرض العربية-ب
    }

    private static boolean isLtrChar(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= 0x00C0 && c <= 0x024F)  // لاتينية ممتدة
                || (c >= 0x0370 && c <= 0x04FF); // يونانية/سيريلية
    }

    /** نهاية السطر بعد تجاهل الفراغات الزائدة. */
    static int trimmedEnd(char[] text, int start, int count) {
        int end = start + count;
        while (end > start && text[end - 1] == ' ') end--;
        return end;
    }

    /**
     * هل السطر عربي أساسًا؟ (أغلب حروفه القوية عربية)
     *
     * السطر العربي يُعرض من اليمين لليسار ويُحاذى إلى اليمين، فيُقرأ بشكل طبيعي ومرتّب
     * حتى لو بدأ بشارة لاتينية مثل `INFO`.
     */
    static boolean isRtlBase(char[] text, int start, int count) {
        final int end = trimmedEnd(text, start, count);
        int rtl = 0;
        int ltr = 0;
        for (int i = start; i < end; i++) {
            if (isRtlChar(text[i])) rtl++;
            else if (isLtrChar(text[i])) ltr++;
        }
        return rtl > ltr;
    }

    /**
     * مقاطع السطر بالترتيب المنطقي (منطقي = ترتيب الكتابة الأصلي).
     * المسافات وعلامات الترقيم تبقى مع المقطع الحالي حتى لا تتقطّع الكلمات.
     */
    static Segment[] segments(char[] text, int start, int count) {
        final int end = trimmedEnd(text, start, count);
        if (end <= start) return EMPTY;

        Segment[] out = new Segment[end - start];
        int count2 = 0;

        int currentDir = NEUTRAL;
        int currentStart = start;

        for (int i = start; i < end; i++) {
            final char c = text[i];
            final int dir;
            if (isRtlChar(c)) dir = RTL;
            else if (isLtrChar(c)) dir = LTR;
            else dir = NEUTRAL;

            if (dir == NEUTRAL) continue;

            if (currentDir == NEUTRAL) {
                currentDir = dir;
                currentStart = i;
            } else if (dir != currentDir) {
                out[count2++] = new Segment(currentStart, i - currentStart, currentDir == RTL);
                currentStart = i;
                currentDir = dir;
            }
        }

        if (currentDir != NEUTRAL) {
            out[count2++] = new Segment(currentStart, end - currentStart, currentDir == RTL);
        } else {
            // سطر بلا حروف قوية (مسافات/رموز فقط)
            out[count2++] = new Segment(start, end - start, false);
        }

        // المسافات/الفواصل في نهاية مقطع تُنقل إلى المقطع التالي حتى تبقى ملتصقة بالنص الذي يليها
        // (مثال: «الطرفية: /home/x» تظهر «: /home/x» كاملة بترتيبها الصحيح).
        for (int i = 0; i < count2 - 1; i++) {
            Segment current = out[i];
            Segment next = out[i + 1];
            int length = current.length;
            while (length > 0 && isNeutral(text[current.start + length - 1])) length--;
            if (length > 0 && length < current.length) {
                int moved = current.length - length;
                out[i] = new Segment(current.start, length, current.rtl);
                out[i + 1] = new Segment(next.start - moved, next.length + moved, next.rtl);
            }
        }

        if (count2 < out.length) {
            Segment[] trimmed = new Segment[count2];
            System.arraycopy(out, 0, trimmed, 0, count2);
            return trimmed;
        }
        return out;
    }

    private static boolean isNeutral(char c) {
        return c == ' ' || c == '	' || c == ':' || c == '/' || c == '\\' || c == ',' || c == '.'
                || c == ';' || c == '|' || c == '(' || c == ')' || c == '[' || c == ']'
                || c == '-' || c == '_' || c == '=' || c == '+';
    }
}
