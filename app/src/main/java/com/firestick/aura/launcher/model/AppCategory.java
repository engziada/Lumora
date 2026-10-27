package com.firestick.aura.launcher.model;

import java.util.ArrayList;
import java.util.List;

public class AppCategory {
    private final String id;
    private final String title;
    private final int accentColor;
    private final List<AppModel> apps;

    public AppCategory(String id, String title, int accentColor) {
        this.id = id;
        this.title = title;
        this.accentColor = accentColor;
        this.apps = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getAccentColor() {
        return accentColor;
    }

    public List<AppModel> getApps() {
        return apps;
    }

    public void setApps(List<AppModel> apps) {
        this.apps.clear();
        if (apps != null) {
            this.apps.addAll(apps);
        }
    }
}
