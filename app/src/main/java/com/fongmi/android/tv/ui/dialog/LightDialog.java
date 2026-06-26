package com.fongmi.android.tv.ui.dialog;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.view.Window;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.R;

public final class LightDialog {

    private LightDialog() {
    }

    public static void apply(AlertDialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        Drawable background = ContextCompat.getDrawable(dialog.getContext(), R.drawable.shape_shell_proxy_dialog);
        if (background == null) return;
        int verticalInset = (int) (dialog.getContext().getResources().getDisplayMetrics().density * 24);
        window.setBackgroundDrawable(new InsetDrawable(background, 0, verticalInset, 0, verticalInset));
    }
}
