package com.firestick.aura.launcher.manager;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreviewVideoShapeTest {
    @Test public void rejectsPortraitSquareUnknownAndUltrawide() {
        assertFalse(PreviewVideoShape.isWide(1080, 1920));
        assertFalse(PreviewVideoShape.isWide(1080, 1080));
        assertFalse(PreviewVideoShape.isWide(0, 1080));
        assertFalse(PreviewVideoShape.isWide(2560, 1080));
        assertTrue(PreviewVideoShape.isWide(1920, 1080));
    }
    @Test public void readsVideoDimensionsInsteadOfThumbnailDimensions() {
        assertFalse(PreviewVideoShape.fits("ytInitialPlayerResponse = {\"streamingData\":{\"formats\":[{\"width\":1080,\"height\":1920}]},\"thumbnail\":{\"width\":1920,\"height\":1080}};"));
        assertTrue(PreviewVideoShape.fits("ytInitialPlayerResponse = {\"streamingData\":{\"adaptiveFormats\":[{\"mimeType\":\"audio/mp4\"},{\"width\":1920,\"height\":1080}]}};"));
    }
    @Test public void missingOrBrokenMetadataIsExcluded() {
        assertFalse(PreviewVideoShape.fits("consent page"));
        assertFalse(PreviewVideoShape.fits("ytInitialPlayerResponse = {}"));
    }
}
