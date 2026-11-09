package com.firestick.aura.launcher.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

/** Keeps D-pad vertical navigation within a pane, including its boundary controls. */
public final class PaneFocus {
    private PaneFocus() { }
    public static boolean contains(View root, View view) {
        for (View current = view; current != null; ) {
            if (current == root) return true;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }
    public static void vertical(ViewGroup root, View current, int direction) {
        java.util.ArrayList<View> controls = root.getFocusables(View.FOCUS_FORWARD);
        controls.removeIf(view -> view == root || !view.isShown() || !view.isEnabled());
        int index = controls.indexOf(current);
        int nextIndex = direction == View.FOCUS_UP ? index - 1 : index + 1;
        View next = nextIndex >= 0 && nextIndex < controls.size() ? controls.get(nextIndex) : null;
        if (next != null) {
            next.requestFocus(direction);
        } else if (root instanceof ScrollView) {
            ScrollView scroll = (ScrollView) root;
            scroll.smoothScrollBy(0, direction == View.FOCUS_UP ? -120 : 120);
        }
    }
}
