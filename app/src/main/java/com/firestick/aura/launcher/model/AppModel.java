package com.firestick.aura.launcher.model;

import android.graphics.drawable.Drawable;

public class AppModel {
    private final String packageName;
    private final String activityName;
    private final String label;
    private final Drawable icon;
    private boolean isFavorite;
    private boolean isHidden;

    public AppModel(String packageName, String activityName, String label, Drawable icon) {
        this.packageName = packageName;
        this.activityName = activityName;
        this.label = label;
        this.icon = icon;
        this.isFavorite = false;
        this.isHidden = false;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getActivityName() {
        return activityName;
    }

    public String getLabel() {
        return label;
    }

    public Drawable getIcon() {
        return icon;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public boolean isHidden() {
        return isHidden;
    }

    public void setHidden(boolean hidden) {
        isHidden = hidden;
    }
}
