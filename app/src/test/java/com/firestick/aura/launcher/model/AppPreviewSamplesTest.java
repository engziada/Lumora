package com.firestick.aura.launcher.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AppPreviewSamplesTest {
    @Test public void recognizesTvAndFireTvPackages() {
        assertEquals(AppPreviewSamples.Provider.YOUTUBE,
                AppPreviewSamples.providerFor("com.amazon.firetv.youtube"));
        assertEquals(AppPreviewSamples.Provider.YOUTUBE,
                AppPreviewSamples.providerFor("com.google.android.youtube.tv"));
        assertEquals(AppPreviewSamples.Provider.PRIME,
                AppPreviewSamples.providerFor("com.amazon.amazonvideo.livingroom"));
        assertEquals(AppPreviewSamples.Provider.NETFLIX,
                AppPreviewSamples.providerFor("com.netflix.ninja"));
    }

    @Test public void rejectsUnrelatedAndLookalikePackages() {
        assertNull(AppPreviewSamples.providerFor(null));
        assertNull(AppPreviewSamples.providerFor(""));
        assertNull(AppPreviewSamples.providerFor("com.netflix.ninja.unofficial"));
        assertNull(AppPreviewSamples.providerFor("org.example.youtube"));
        assertNull(AppPreviewSamples.providerFor("com.android.settings"));
    }

    @Test public void carouselWrapsInBothDirectionsForEveryProvider() {
        for (AppPreviewSamples.Provider provider : AppPreviewSamples.Provider.values()) {
            int count = AppPreviewSamples.count(provider);
            assertEquals(AppPreviewSamples.title(provider, 0), AppPreviewSamples.title(provider, count));
            assertEquals(AppPreviewSamples.title(provider, count - 1), AppPreviewSamples.title(provider, -1));
        }
    }
}
