package com.termux.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;
import com.termux.terminal.WcWidth;

/**
 * Renderer of a {@link TerminalEmulator} into a {@link Canvas}.
 * <p/>
 * Saves font metrics, so needs to be recreated each time the typeface or font size changes.
 */
public final class TerminalRenderer {

    final int mTextSize;
    final Typeface mTypeface;
    private final Paint mTextPaint = new Paint();

    /**
     * The width of a single mono spaced character obtained by {@link Paint#measureText(String)} on a single 'X'.
     */
    final float mFontWidth;
    /**
     * The {@link Paint#getFontSpacing()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png
     */
    final int mFontLineSpacing;
    /**
     * The {@link Paint#ascent()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png
     */
    private final int mFontAscent;
    /**
     * The {@link #mFontLineSpacing} + {@link #mFontAscent}.
     */
    final int mFontLineSpacingAndAscent;

    private final float[] asciiMeasures = new float[127];

    public TerminalRenderer(int textSize, Typeface typeface) {
        mTextSize = textSize;
        mTypeface = typeface;

        mTextPaint.setTypeface(typeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(textSize);

        mFontLineSpacing = (int) Math.ceil(mTextPaint.getFontSpacing());
        mFontAscent = (int) Math.ceil(mTextPaint.ascent());
        mFontLineSpacingAndAscent = mFontLineSpacing + mFontAscent;
        mFontWidth = mTextPaint.measureText("X");

        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < asciiMeasures.length; i++) {
            sb.setCharAt(0, (char) i);
            asciiMeasures[i] = mTextPaint.measureText(sb, 0, 1);
        }
    }

    /**
     * Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection.
     */
    public void render(TerminalEmulator mEmulator, Canvas canvas, int topRow,
                       int selectionY1, int selectionY2, int selectionX1, int selectionX2) {
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int endRow = topRow + mEmulator.mRows;
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible();
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();

        if (reverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);

        float heightOffset = mFontLineSpacingAndAscent;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += mFontLineSpacing;

            final int cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1) selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : mEmulator.mColumns;
            }

            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final char[] line = lineObject.mText;
            final int charsUsedInLine = lineObject.getSpaceUsed();

            // سطر يحتوي عربيًا/عبريًا: نرسمه بمقاطع ثنائية الاتجاه حتى تظهر الكلمات متّصلة ومرتّبة
            if (charsUsedInLine > 0 && BidiText.hasRtl(line, 0, charsUsedInLine)) {
                drawBidiRow(canvas, lineObject, line, charsUsedInLine, palette, columns, heightOffset,
                        selx1, selx2, cursorX, cursorShape, reverseVideo);
                continue;
            }

            long lastRunStyle = 0;
            boolean lastRunInsideCursor = false;
            boolean lastRunInsideSelection = false;
            int lastRunStartColumn = -1;
            int lastRunStartIndex = 0;
            boolean lastRunFontWidthMismatch = false;
            int currentCharIndex = 0;
            float measuredWidthForRun = 0.f;

            for (int column = 0; column < columns; ) {
                final char charAtIndex = line[currentCharIndex];
                final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                final int codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                final int codePointWcWidth = WcWidth.width(codePoint);
                final boolean insideCursor = (cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1));
                final boolean insideSelection = column >= selx1 && column <= selx2;
                final long style = lineObject.getStyle(column);

                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                final float measuredCodePointWidth = (codePoint < asciiMeasures.length) ? asciiMeasures[codePoint] : mTextPaint.measureText(line,
                        currentCharIndex, charsForCodePoint);
                final boolean fontWidthMismatch = Math.abs(measuredCodePointWidth / mFontWidth - codePointWcWidth) > 0.01;

                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                    if (column == 0) {
                        // Skip first column as there is nothing to draw, just record the current style.
                    } else {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertCursorTextColor = lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
                        drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun,
                                lastRunStartIndex, charsSinceLastRun, measuredWidthForRun,
                                cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
                    }
                    measuredWidthForRun = 0.f;
                    lastRunStyle = style;
                    lastRunInsideCursor = insideCursor;
                    lastRunInsideSelection = insideSelection;
                    lastRunStartColumn = column;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = fontWidthMismatch;
                }
                measuredWidthForRun += measuredCodePointWidth;
                column += codePointWcWidth;
                currentCharIndex += charsForCodePoint;
                while (currentCharIndex < charsUsedInLine && WcWidth.width(line, currentCharIndex) <= 0) {
                    // Eat combining chars so that they are treated as part of the last non-combining code point,
                    // instead of e.g. being considered inside the cursor in the next run.
                    currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                }
            }

            final int columnWidthSinceLastRun = columns - lastRunStartColumn;
            final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
            int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
            boolean invertCursorTextColor = lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
            drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                    measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
        }
    }

    /** لونان محسوبان لمقطع نصّي (يُعاد استخدامهما لتفادي تخصيص كائنات في كل إطار). */
    private final int[] mScratchColors = new int[2];

    /**
     * يرسم سطرًا يحتوي نصًا عربيًا/عبريًا.
     *
     * الطرفية شبكة خانات ثابتة العرض، فتقطيع السطر إلى «مقاطع» ثم رسم كل مقطع باتجاهه
     * هو ما يجعل الكلمات متّصلة (تثبيت) بدل منفصلة (ت ي ب ث ت) أو معكوسة (ةيبرعلا).
     */
    private void drawBidiRow(Canvas canvas, TerminalRow lineObject, char[] line, int charsUsedInLine,
                             int[] palette, int columns, float y, int selx1, int selx2,
                             int cursorX, int cursorShape, boolean reverseVideo) {
        final int end = BidiText.trimmedEnd(line, 0, Math.min(charsUsedInLine, line.length));
        if (end <= 0) return;

        // خريطة: فهرس الحرف → الخانة + نمط الخانة (لألوان كل مقطع)
        final long[] charStyle = new long[end];
        final int[] charColumn = new int[end];
        int ci = 0;
        int column = 0;
        long lastStyle = 0;
        while (ci < end && column < columns) {
            lastStyle = lineObject.getStyle(column);
            final char c = line[ci];
            final boolean surrogate = Character.isHighSurrogate(c) && ci + 1 < end;
            final int chars = surrogate ? 2 : 1;
            final int codePoint = surrogate ? Character.toCodePoint(c, line[ci + 1]) : c;
            int width = WcWidth.width(codePoint);
            if (width <= 0) width = 1;
            for (int k = 0; k < chars && ci + k < end; k++) {
                charStyle[ci + k] = lastStyle;
                charColumn[ci + k] = column;
            }
            ci += chars;
            column += width;
        }
        for (int k = ci; k < end; k++) {
            charStyle[k] = lastStyle;
            charColumn[k] = column;
        }

        final BidiText.Segment[] segments = BidiText.segments(line, 0, end);
        if (segments.length == 0) return;

        final float[] segWidth = new float[segments.length];
        float total = 0f;
        for (int i = 0; i < segments.length; i++) {
            segWidth[i] = Math.max(0f, mTextPaint.measureText(line, segments[i].start, segments[i].length));
            total += segWidth[i];
        }
        if (total <= 0f) return;

        // إن تجاوز النص عرض الشاشة نضغطه أفقيًا بدل أن يُقتطع
        final float available = columns * mFontWidth;
        final float scale = (available > 0f && total > available) ? available / total : 1f;

        final boolean rtlBase = BidiText.isRtlBase(line, 0, end);
        final float textY = y - mFontLineSpacingAndAscent;
        final float rectTop = y - mFontLineSpacingAndAscent + mFontAscent;
        final float rectBottom = y;

        boolean savedMatrix = false;
        if (Math.abs(scale - 1f) > 0.01f) {
            canvas.save();
            canvas.scale(scale, 1f);
            savedMatrix = true;
        }

        // السطر العربي يُحاذى إلى اليمين (كتابة عربية طبيعية)، واللاتيني يبقى يسارًا
        float x = rtlBase ? Math.max(0f, available / (scale <= 0f ? 1f : scale) - total) : 0f;
        for (int k = 0; k < segments.length; k++) {
            final int index = rtlBase ? (segments.length - 1 - k) : k;
            final BidiText.Segment segment = segments[index];
            final float width = segWidth[index];
            final long style = charStyle[Math.min(segment.start, end - 1)];
            final int effect = TextStyle.decodeEffect(style);
            final boolean insideSelection = selx1 >= 0
                    && charColumn[Math.min(segment.start, end - 1)] >= selx1
                    && charColumn[Math.min(segment.start, end - 1)] <= selx2;
            final boolean invert = reverseVideo
                    ^ ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != 0)
                    ^ insideSelection;
            resolveColors(style, palette, invert);

            if (mScratchColors[1] != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
                mTextPaint.setColor(mScratchColors[1]);
                canvas.drawRect(x, rectTop, x + width, rectBottom, mTextPaint);
            }

            if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
                mTextPaint.setFakeBoldText((effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0);
                mTextPaint.setUnderlineText((effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0);
                mTextPaint.setTextSkewX((effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0 ? -0.35f : 0f);
                mTextPaint.setStrikeThruText((effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0);
                mTextPaint.setColor(mScratchColors[0]);
                canvas.drawTextRun(line, segment.start, segment.length, segment.start, segment.length,
                        x, textY, segment.rtl, mTextPaint);
            }

            x += width;
        }

        // مؤشّر الكتابة: شريط رفيع عند خانة المؤشّر (لا يغطّي النص العربي)
        if (cursorX >= 0 && cursorX <= columns) {
            mTextPaint.setColor(palette[TextStyle.COLOR_INDEX_CURSOR]);
            final float cursorWidth = Math.max(2f, mFontWidth / 5f);
            final float effectiveWidth = available / (scale <= 0f ? 1f : scale);
            // 2.8.2: في السطر العربي يكون النص مُحاذًى إلى اليمين، فخانة المؤشّر المنطقية
            // تُعكس أفقيًا. بلا هذا العكس كان المؤشّر يُرسم في وسط الكلام بدل نهاية النص
            // حيث يكتب المستخدم فعليًا.
            float cursorLeft = rtlBase ? (effectiveWidth - cursorX * mFontWidth) : (cursorX * mFontWidth);
            if (rtlBase) {
                cursorLeft = Math.max(0f, Math.min(cursorLeft, effectiveWidth - cursorWidth));
            }
            if (cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) {
                canvas.drawRect(cursorLeft, rectBottom - Math.max(2f, (rectBottom - rectTop) / 8f),
                        cursorLeft + mFontWidth, rectBottom, mTextPaint);
            } else {
                canvas.drawRect(cursorLeft, rectTop, cursorLeft + cursorWidth, rectBottom, mTextPaint);
            }
        }

        if (savedMatrix) canvas.restore();
    }

    /** يحسب لون النص والخلفية لنمط خانة، مع عكس الألوان عند التحديد. */
    private void resolveColors(long textStyle, int[] palette, boolean reverse) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        if ((foreColor & 0xff000000) != 0xff000000) {
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }
        if ((backColor & 0xff000000) != 0xff000000) backColor = palette[backColor];
        if (reverse) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }
        mScratchColors[0] = foreColor;
        mScratchColors[1] = backColor;
    }

    private void drawTextRun(Canvas canvas, char[] text, int[] palette, float y, int startColumn, int runWidthColumns,
                             int startCharIndex, int runWidthChars, float mes, int cursor, int cursorStyle,
                             long textStyle, boolean reverseVideo) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean underline = (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
        final boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        final boolean strikeThrough = (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;

        if ((foreColor & 0xff000000) != 0xff000000) {
            // Let bold have bright colors if applicable (one of the first 8):
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }

        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }

        // Reverse video here if _one and only one_ of the reverse flags are set:
        final boolean reverseVideoHere = reverseVideo ^ (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)) != 0;
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }

        float left = startColumn * mFontWidth;
        float right = left + runWidthColumns * mFontWidth;

        mes = mes / mFontWidth;
        boolean savedMatrix = false;
        if (Math.abs(mes - runWidthColumns) > 0.01) {
            canvas.save();
            canvas.scale(runWidthColumns / mes, 1.f);
            left *= mes / runWidthColumns;
            right *= mes / runWidthColumns;
            savedMatrix = true;
        }

        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            // Only draw non-default background.
            mTextPaint.setColor(backColor);
            canvas.drawRect(left, y - mFontLineSpacingAndAscent + mFontAscent, right, y, mTextPaint);
        }

        if (cursor != 0) {
            mTextPaint.setColor(cursor);
            float cursorHeight = mFontLineSpacingAndAscent - mFontAscent;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) cursorHeight /= 4.;
            else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR)
                right -= ((right - left) * 3) / 4.;
            canvas.drawRect(left, y - cursorHeight, right, y, mTextPaint);
        }

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
            if (dim) {
                int red = (0xFF & (foreColor >> 16));
                int green = (0xFF & (foreColor >> 8));
                int blue = (0xFF & foreColor);
                // Dim color handling used by libvte which in turn took it from xterm
                // (https://bug735245.bugzilla-attachments.gnome.org/attachment.cgi?id=284267):
                red = red * 2 / 3;
                green = green * 2 / 3;
                blue = blue * 2 / 3;
                foreColor = 0xFF000000 + (red << 16) + (green << 8) + blue;
            }

            mTextPaint.setFakeBoldText(bold);
            mTextPaint.setUnderlineText(underline);
            mTextPaint.setTextSkewX(italic ? -0.35f : 0.f);
            mTextPaint.setStrikeThruText(strikeThrough);
            mTextPaint.setColor(foreColor);

            // The text alignment is the default Paint.Align.LEFT.
            canvas.drawTextRun(text, startCharIndex, runWidthChars, startCharIndex, runWidthChars, left, y - mFontLineSpacingAndAscent, false, mTextPaint);
        }

        if (savedMatrix) canvas.restore();
    }

    public float getFontWidth() {
        return mFontWidth;
    }

    public int getFontLineSpacing() {
        return mFontLineSpacing;
    }
}
