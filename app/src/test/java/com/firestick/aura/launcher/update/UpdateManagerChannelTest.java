package com.firestick.aura.launcher.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class UpdateManagerChannelTest {
    private static String releases(String... names) {
        try {
            JSONArray array = new JSONArray();
            for (String name : names) {
                JSONObject release = new JSONObject();
                release.put("draft", name.startsWith("draft"));
                JSONArray assets = new JSONArray();
                if (!name.startsWith("skip")) {
                    JSONObject asset = new JSONObject();
                    asset.put("name", "update.json");
                    asset.put("browser_download_url",
                            "https://github.com/engziada/Lumora/releases/download/" + name + "/update.json");
                    assets.put(asset);
                }
                release.put("assets", assets);
                array.put(release);
            }
            return array.toString();
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    @Test
    public void picksTheNewestReleaseFirst() {
        assertEquals("https://github.com/engziada/Lumora/releases/download/r2/update.json",
                UpdateManager.pickReleasesUpdateUrl(releases("r2", "r1")));
    }

    @Test
    public void preReleaseBuildsAreAccepted() {
        assertEquals("https://github.com/engziada/Lumora/releases/download/r3-beta.1/update.json",
                UpdateManager.pickReleasesUpdateUrl(releases("r3-beta.1", "r2")));
    }

    @Test
    public void draftsAreSkipped() {
        assertEquals("https://github.com/engziada/Lumora/releases/download/r2/update.json",
                UpdateManager.pickReleasesUpdateUrl(releases("draft-one", "r2")));
    }

    @Test
    public void releasesWithoutTheAssetAreSkipped() {
        assertEquals("https://github.com/engziada/Lumora/releases/download/r1/update.json",
                UpdateManager.pickReleasesUpdateUrl(releases("skip-bare", "r1")));
    }

    @Test
    public void foreignRepositoriesAreRejected() {
        assertNull(UpdateManager.pickReleasesUpdateUrl(
                "[{\"assets\":[{\"name\":\"update.json\","
                        + "\"browser_download_url\":\"https://evil.example/Lumora/releases/download/x/update.json\"}]}]"));
    }

    @Test
    public void malformedJsonYieldsNull() {
        assertNull(UpdateManager.pickReleasesUpdateUrl("not json"));
        assertNull(UpdateManager.pickReleasesUpdateUrl("[]"));
    }
}
