package com.firestick.aura.launcher.ui;

import android.app.Activity;
import android.app.Dialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.firestick.aura.launcher.R;

/** Glass dialog with one text field, used for the API key and a custom model id. */
public final class TextPrompt {
    public interface OnSubmit {
        void onSubmit(String value);
    }

    private TextPrompt() {
    }

    public static Dialog show(Activity activity, CharSequence title, CharSequence message, String initial,
                              boolean secret, OnSubmit onSubmit) {
        Dialog dialog = Glass.overlay(activity, R.layout.dialog_text_input, Gravity.CENTER);
        ((TextView) dialog.findViewById(R.id.promptTitle)).setText(title);
        TextView body = dialog.findViewById(R.id.promptMessage);
        if (message == null) body.setVisibility(View.GONE);
        else body.setText(message);
        EditText field = dialog.findViewById(R.id.promptField);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : 0));
        if (initial != null) field.setText(initial);
        Button save = dialog.findViewById(R.id.btnPromptSave);
        Button cancel = dialog.findViewById(R.id.btnPromptCancel);
        Runnable submit = () -> {
            String value = field.getText().toString().trim();
            if (value.isEmpty()) return;
            dialog.dismiss();
            onSubmit.onSubmit(value);
        };
        save.setOnClickListener(v -> submit.run());
        cancel.setOnClickListener(v -> dialog.dismiss());
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.run();
                return true;
            }
            return false;
        });
        dialog.show();
        field.requestFocus();
        return dialog;
    }
}
