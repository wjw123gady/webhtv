package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.player.lyrics.LyricsLine;
import com.fongmi.android.tv.player.lyrics.LyricsParser;
import com.fongmi.android.tv.player.lyrics.LyricsResult;
import com.google.android.material.textview.MaterialTextView;

import java.util.Collections;
import java.util.List;

public class LyricsOverlayView extends FrameLayout {

    private static final int ROWS = 5;

    private final LinearLayout box;
    private final MaterialTextView[] rows = new MaterialTextView[ROWS];
    private List<LyricsLine> lines = Collections.emptyList();
    private boolean compact;
    private int index = -1;

    public LyricsOverlayView(Context context) {
        this(context, null);
    }

    public LyricsOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(false);
        setFocusable(false);
        setVisibility(GONE);

        box = new LinearLayout(context);
        box.setGravity(Gravity.CENTER);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(10), dp(18), dp(10));

        for (int i = 0; i < ROWS; i++) {
            rows[i] = textView(context);
            box.addView(rows[i], new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(box, params);
        applyStyle();
    }

    public void setLyrics(LyricsResult result, List<LyricsLine> lines) {
        this.lines = lines == null ? Collections.emptyList() : lines;
        this.index = -1;
        setVisibility(this.lines.isEmpty() ? GONE : VISIBLE);
        if (!this.lines.isEmpty()) update(0);
    }

    public void update(long positionMs) {
        if (lines.isEmpty()) {
            clear();
            return;
        }
        int nextIndex = LyricsParser.findLine(lines, positionMs);
        if (nextIndex == index) return;
        index = nextIndex;
        int count = visibleRows();
        int center = count / 2;
        int offset = (ROWS - count) / 2;
        for (int i = 0; i < ROWS; i++) {
            MaterialTextView row = rows[i];
            if (i < offset || i >= offset + count) {
                row.setText("");
                row.setVisibility(GONE);
                continue;
            }
            int lineIndex = index + i - offset - center;
            if (lineIndex < 0 || lineIndex >= lines.size()) {
                row.setText("");
                row.setVisibility(INVISIBLE);
                continue;
            }
            row.setText(lines.get(lineIndex).getText());
            row.setVisibility(VISIBLE);
            style(row, Math.abs(i - offset - center));
        }
        setVisibility(VISIBLE);
    }

    public void clear() {
        lines = Collections.emptyList();
        index = -1;
        for (MaterialTextView row : rows) row.setText("");
        setVisibility(GONE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        boolean nextCompact = h > 0 && h < dp(300);
        if (compact == nextCompact && w == oldw && h == oldh) return;
        compact = nextCompact;
        applyStyle();
        if (!lines.isEmpty() && index >= 0) {
            int current = index;
            index = -1;
            update(lines.get(current).getTimeMs());
        }
    }

    private MaterialTextView textView(Context context) {
        MaterialTextView view = new MaterialTextView(context);
        view.setGravity(Gravity.CENTER);
        view.setMaxLines(2);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setIncludeFontPadding(false);
        view.setShadowLayer(4f, 0, 2f, 0xCC000000);
        view.setPadding(0, dp(3), 0, dp(3));
        return view;
    }

    private void applyStyle() {
        int count = visibleRows();
        int offset = (ROWS - count) / 2;
        int center = count / 2;
        for (int i = 0; i < ROWS; i++) {
            MaterialTextView row = rows[i];
            row.setVisibility(i < offset || i >= offset + count ? GONE : row.getVisibility());
            style(row, Math.abs(i - offset - center));
        }
    }

    private void style(MaterialTextView view, int distance) {
        boolean primary = distance == 0;
        int size = compact ? primary ? 18 : 13 : primary ? 28 : distance == 1 ? 19 : 16;
        int color = primary ? 0xFFFFD56A : distance == 1 ? Color.WHITE : 0xB8FFFFFF;
        view.setTextSize(size);
        view.setTextColor(color);
        view.setAlpha(primary ? 1f : distance == 1 ? 0.82f : 0.58f);
        view.setTypeface(Typeface.DEFAULT, primary ? Typeface.BOLD : Typeface.NORMAL);
        view.setMaxLines(primary ? 2 : 1);
        view.setMinHeight(compact ? primary ? dp(44) : dp(24) : primary ? dp(62) : dp(34));
    }

    private int visibleRows() {
        return compact ? 3 : ROWS;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
