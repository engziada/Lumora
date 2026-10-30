package com.firestick.aura.launcher.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.model.AppPreviewSamples;
import com.firestick.aura.launcher.model.AppPreviewSamples.Provider;
import com.firestick.aura.launcher.model.AppPreviewSamples.Title;

/** A passive, offline demo panel. The activity owns focus eligibility and cancels on pause. */
public final class AppPreview {
    private static final long DWELL_MS = 600L, ROTATE_MS = 6000L;
    private final View panel;
    private final ImageView art;
    private final TextView service, title, category, description, position;
    private Provider provider;
    private String packageName;
    private int index;

    private final Runnable reveal = this::reveal;

    private void reveal() {
        if (provider == null) return;
        render();
        panel.setAlpha(0f);
        panel.setVisibility(View.VISIBLE);
        panel.animate().alpha(1f).setDuration(180L).start();
        panel.postDelayed(this.rotate, ROTATE_MS);
    }

    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (provider == null || panel.getVisibility() != View.VISIBLE) return;
            index = (index + 1) % AppPreviewSamples.count(provider);
            render();
            panel.postDelayed(this, ROTATE_MS);
        }
    };

    public AppPreview(View panel) {
        this.panel = panel;
        art = panel.findViewById(R.id.previewArt);
        service = panel.findViewById(R.id.previewService);
        title = panel.findViewById(R.id.previewTitle);
        category = panel.findViewById(R.id.previewCategory);
        description = panel.findViewById(R.id.previewDescription);
        position = panel.findViewById(R.id.previewPosition);
    }

    public void focus(String packageName, String label) {
        Provider next = AppPreviewSamples.providerFor(packageName);
        if (next != null && packageName.equals(this.packageName)) return;
        hide();
        if (next == null) return;
        this.packageName = packageName;
        provider = next;
        service.setText(panel.getContext().getString(R.string.preview_service, label));
        panel.postDelayed(reveal, DWELL_MS);
    }

    public void hide() {
        panel.removeCallbacks(reveal);
        panel.removeCallbacks(rotate);
        panel.animate().cancel();
        panel.setVisibility(View.GONE);
        provider = null;
        packageName = null;
        index = 0;
    }

    private void render() {
        Title sample = AppPreviewSamples.title(provider, index);
        title.setText(sample.name);
        category.setText(sample.category);
        description.setText(sample.description);
        position.setText(panel.getContext().getString(R.string.preview_position,
                index + 1, AppPreviewSamples.count(provider)));
        int[] colors = provider == Provider.NETFLIX ? new int[]{0xFF602932, 0xFF20121F}
                : provider == Provider.PRIME ? new int[]{0xFF214A68, 0xFF101E30}
                : new int[]{0xFF77472B, 0xFF291B23};
        art.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors));
        art.setImageResource(index == 0 ? R.drawable.ic_preview_orbit
                : index == 1 ? R.drawable.ic_preview_city : R.drawable.ic_preview_landscape);
    }
}
