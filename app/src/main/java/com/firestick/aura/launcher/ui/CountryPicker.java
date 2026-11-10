package com.firestick.aura.launcher.ui;

import android.app.Activity;
import android.app.Dialog;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.core.content.ContextCompat;
import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.model.IptvCountry;
import java.util.*;
import java.util.function.Consumer;

/** TV country picker with a focusable alphabetical index alongside the country list. */
public final class CountryPicker {
    private CountryPicker() { }

    public static Dialog show(Activity activity, List<IptvCountry> countries, String selectedCode,
                              Consumer<IptvCountry> selected) {
        Dialog dialog = Glass.overlay(activity, R.layout.dialog_country_picker, Gravity.CENTER);
        ListView list = dialog.findViewById(R.id.countryPickerList);
        LinearLayout rail = dialog.findViewById(R.id.countryLetters);
        List<String> labels = new ArrayList<>();
        Map<String, Integer> sections = new LinkedHashMap<>();
        int initial = 0;
        for (int i = 0; i < countries.size(); i++) {
            IptvCountry country = countries.get(i);
            labels.add((country.flag.isEmpty() ? "" : country.flag + "  ") + country.name);
            String letter = "ALL".equals(country.code) ? "#" : country.name.substring(0, 1).toUpperCase(Locale.ROOT);
            if (!sections.containsKey(letter)) sections.put(letter, i);
            if (country.code.equals(selectedCode)) initial = i;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, recycled, parent);
                row.setTextColor(ContextCompat.getColor(activity, R.color.nova_text));
                row.setTextSize(14);
                row.setSingleLine();
                row.setEllipsize(TextUtils.TruncateAt.END);
                row.setMinHeight(dp(activity, 42));
                return row;
            }
        };
        list.setAdapter(adapter);
        for (Map.Entry<String, Integer> section : sections.entrySet()) {
            Button letter = new Button(activity);
            letter.setId(View.generateViewId());
            letter.setTag("letter:" + section.getKey());
            letter.setText("#".equals(section.getKey()) ? activity.getString(R.string.country_all_short) : section.getKey());
            letter.setAllCaps(false);
            letter.setTextSize(12);
            letter.setPadding(0, 0, 0, 0);
            letter.setMinimumWidth(0);
            letter.setMinWidth(0);
            letter.setTextColor(ContextCompat.getColor(activity, R.color.nova_text));
            letter.setBackgroundResource(R.drawable.bg_option_selector);
            letter.setContentDescription(activity.getString(R.string.country_jump_letter, letter.getText()));
            letter.setNextFocusRightId(R.id.countryPickerList);
            Runnable jump = () -> {
                list.setSelection(section.getValue());
                list.setNextFocusLeftId(letter.getId());
            };
            letter.setOnFocusChangeListener((view, focused) -> { if (focused) jump.run(); });
            letter.setOnClickListener(view -> { jump.run(); list.requestFocus(); });
            rail.addView(letter, new LinearLayout.LayoutParams(-1, dp(activity, 36)));
        }
        list.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            selected.accept(countries.get(position));
        });
        if (rail.getChildCount() > 0) list.setNextFocusLeftId(rail.getChildAt(0).getId());
        dialog.show();
        list.requestFocus();
        list.setSelection(initial);
        return dialog;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
