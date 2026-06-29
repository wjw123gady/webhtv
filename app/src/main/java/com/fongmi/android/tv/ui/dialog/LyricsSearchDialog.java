package com.fongmi.android.tv.ui.dialog;

import android.text.InputType;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LyricsSearchDialog {

    public interface Listener {
        void onSearch(String keyword);
    }

    public static void show(FragmentActivity activity, String keyword, Listener listener) {
        if (activity == null || activity.isFinishing()) return;
        TextInputLayout layout = new TextInputLayout(activity);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setHint(activity.getString(R.string.player_lyrics_keyword));

        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setMaxLines(1);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setText(TextUtils.isEmpty(keyword) ? "" : keyword);
        if (input.getText() != null) input.setSelection(input.getText().length());
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = ResUtil.dp2px(20);
        root.setPadding(pad, ResUtil.dp2px(8), pad, 0);
        root.addView(layout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.player_lyrics_reload)
                .setView(root)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.play_search, null)
                .create();
        dialog.setOnShowListener(d -> {
            input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
                submit(dialog, input, listener);
                return true;
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submit(dialog, input, listener));
            Util.showKeyboard(input);
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private static void submit(AlertDialog dialog, TextInputEditText input, Listener listener) {
        String keyword = input.getText() == null ? "" : input.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            input.setError(input.getContext().getString(R.string.player_lyrics_keyword_required));
            return;
        }
        Util.hideKeyboard(input);
        dialog.dismiss();
        if (listener != null) listener.onSearch(keyword);
    }
}
