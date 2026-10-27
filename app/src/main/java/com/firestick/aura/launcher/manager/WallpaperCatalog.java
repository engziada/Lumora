package com.firestick.aura.launcher.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Curated wallpaper sources. Space images come from ESA/Hubble (CC BY 4.0) at the 1280 px
 * "screen" size; night-sky and desert stills come from Unsplash, cropped to 1280 x 720.
 * Every image is colour-graded warm on the device, see {@link WallpaperManager}.
 */
public final class WallpaperCatalog {
    public static final String NEBULAE = "nebulae";
    public static final String GALAXIES = "galaxies";
    public static final String NIGHT_SKY = "night_sky";
    public static final String DESERT = "desert";
    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(NEBULAE, GALAXIES, NIGHT_SKY, DESERT));
    public static final List<String> DEFAULTS = Collections.unmodifiableList(Arrays.asList(NEBULAE, GALAXIES, NIGHT_SKY));

    private static final String ESA = "https://cdn.esahubble.org/archives/images/screen/";
    private static final String UNSPLASH = "https://images.unsplash.com/";
    private static final String CROP = "?w=1280&h=720&fit=crop&q=80";

    private WallpaperCatalog() {
    }

    public static List<String> urls(String category) {
        List<String> out = new ArrayList<>();
        switch (category) {
            case NEBULAE:
                for (String id : new String[]{"heic1509a", "heic2007a", "heic1501a", "heic0601a", "heic1608a", "heic1307a"}) {
                    out.add(ESA + id + ".jpg");
                }
                break;
            case GALAXIES:
                for (String id : new String[]{"heic0506a", "heic0602a", "heic0715a", "heic2017a"}) {
                    out.add(ESA + id + ".jpg");
                }
                break;
            case NIGHT_SKY:
                for (String id : new String[]{"photo-1419242902214-272b3f66ee7a", "photo-1444703686981-a3abbc4d4fe3",
                        "photo-1506318137071-a8e063b4bec0", "photo-1475274047050-1d0c0975c63e",
                        "photo-1462331940025-496dfbfc7564"}) {
                    out.add(UNSPLASH + id + CROP);
                }
                break;
            case DESERT:
                for (String id : new String[]{"photo-1509316785289-025f5b846b35", "photo-1547234935-80c7145ec969",
                        "photo-1473580044384-7ba9967e16a0"}) {
                    out.add(UNSPLASH + id + CROP);
                }
                break;
            default:
                break;
        }
        return out;
    }

    /** All URLs of the selected categories, in catalog order. */
    public static List<String> urls(Set<String> categories) {
        List<String> out = new ArrayList<>();
        for (String category : ALL) {
            if (categories.contains(category)) out.addAll(urls(category));
        }
        return out;
    }
}
