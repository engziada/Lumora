package com.firestick.aura.launcher.ui;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.PreviewFeedRepository;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.List;
import org.json.JSONArray;
import com.firestick.aura.launcher.model.AppPreviewSamples;
import com.firestick.aura.launcher.model.AppPreviewSamples.Provider;

/**
 * Home preview panel. What it shows follows {@link PreferenceManager#getPanelContent()}:
 * the generative particles/plasma ambient (offline), the public-video player from the provider
 * feeds, or nothing. The WebView keeps reading public feeds only; the generator never touches it.
 */
public final class AppPreview {
    private final View panel;
    private final WebView player;
    private final PreviewParticlesView particles;
    private final TextView service, status;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final PreviewFeedRepository feeds;
    private final PreferenceManager preferences;
    private Future<?> pending;
    private int generation;
    private String current;
    private String country;
    private Provider provider;
    private final Runnable reveal = this::reveal;

    @SuppressLint("SetJavaScriptEnabled")
    public AppPreview(View panel) {
        this.panel = panel;
        feeds = new PreviewFeedRepository(panel.getContext());
        preferences = new PreferenceManager(panel.getContext());
        player = panel.findViewById(R.id.previewPlayer);
        particles = panel.findViewById(R.id.previewParticles);
        service = panel.findViewById(R.id.previewService);
        status = panel.findViewById(R.id.previewStatus);
        // Transparent surface: particles mode renders on views behind; video mode's HTML paints
        // its own black frame. An opaque WebView would cover the generator with a black box.
        player.setBackgroundColor(Color.TRANSPARENT);
        player.getSettings().setJavaScriptEnabled(true);
        player.getSettings().setDomStorageEnabled(true);
        player.getSettings().setMediaPlaybackRequiresUserGesture(false);
        player.getSettings().setAllowFileAccess(false);
        player.getSettings().setAllowContentAccess(false);
        player.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true; // Playback stays embedded; navigation never takes focus from the ribbon.
            }
        });
        player.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                String text = message.message();
                if (text.startsWith("PREVIEW:")) {
                    Log.i("LumoraPreview", text);
                    if (text.startsWith("PREVIEW:error") || text.contains("blocked"))
                        { status.setVisibility(View.VISIBLE); status.setText("Video unavailable or autoplay blocked"); }
                    else if (text.startsWith("PREVIEW:playing"))
                        status.setVisibility(View.GONE);
                }
                return true;
            }
        });
    }

    public void focus(String packageName, String label) {
        int content = preferences.getPanelContent();
        if (content == PreferenceManager.PANEL_CONTENT_OFF) {
            hide();
            return;
        }
        Provider next = AppPreviewSamples.providerFor(packageName);
        if (next != null && packageName.equals(current) && preferences.getShowsCountry().equals(country)) return;
        hide();
        current = packageName;
        provider = next;
        country = preferences.getShowsCountry();
        if (content == PreferenceManager.PANEL_CONTENT_PARTICLES) {
            // Offline generative ambient: no provider mapping and no network are involved.
            panel.postDelayed(reveal, 150L);
        } else if (next != null) {
            service.setText(label + " · Public video preview");
            panel.postDelayed(reveal, 600L);
        } else {
            // The package has no provider feed; leave the panel closed.
            current = null;
            provider = null;
        }
    }

    private void reveal() {
        if (provider == null && preferences.getPanelContent() != PreferenceManager.PANEL_CONTENT_PARTICLES) return;
        if (preferences.getPanelContent() == PreferenceManager.PANEL_CONTENT_PARTICLES) {
            // Offline generative ambient: never let the WebView paint over the generator.
            player.setVisibility(View.GONE);
            status.setVisibility(View.GONE);
            particles.setVisibility(View.VISIBLE);
            panel.setVisibility(View.VISIBLE);
            particles.start(current);
            return;
        }
        player.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        status.setText("Loading public videos…");
        panel.setVisibility(View.VISIBLE);
        int ticket = generation;
        Provider selected = provider;
        String selectedCountry = country;
        pending = io.submit(() -> {
            try {
                List<String> videos = feeds.load(selected, selectedCountry);
                panel.post(() -> {
                    if (ticket != generation) return;
                    if (videos.isEmpty()) { status.setText("No widescreen previews available"); return; }
                    Log.i("LumoraPreview", "FEED:" + selected + " country=" + selectedCountry + " videos=" + videos.size());
                    play(new JSONArray(videos).toString());
                });
            } catch (Exception error) {
                panel.post(() -> { if (ticket == generation) status.setText("Public feed unavailable. Try again later."); });
            }
        });
    }

    private void play(String ids) {
        // No API key, account cookies, stream extraction, or JavaScript-to-native bridge.
        String html = "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body,#p{margin:0;width:100%;height:100%;background:#000;overflow:hidden}</style>"
                + "<div id='p'></div><script>var p,ids=" + ids + ",n=0,timer,failures=0;"
                + "function log(s){console.log('PREVIEW:'+s)}"
                + "function next(){clearTimeout(timer);n=(n+1)%ids.length;p.mute();p.loadVideoById(ids[n]);}"
                + "function onYouTubeIframeAPIReady(){p=new YT.Player('p',{videoId:ids[0],playerVars:{autoplay:1,controls:0,playsinline:1,hl:" + (java.util.Arrays.asList("EG","AE","SA").contains(country) ? "'ar'" : "'en'") + ",origin:location.origin},events:{"
                + "onReady:function(){p.mute();p.playVideo()},"
                + "onAutoplayBlocked:function(){log('blocked')},"
                + "onError:function(e){log('error '+e.data);clearTimeout(timer);if(++failures<ids.length)timer=setTimeout(next,1500);else{p.stopVideo();log('blocked all videos')}},"
                + "onStateChange:function(e){if(e.data===1){failures=0;log('playing '+ids[n]);clearTimeout(timer);timer=setTimeout(next,15000)}else if(e.data===0)next()}"
                + "}})}setInterval(function(){if(p&&p.getCurrentTime)log('time '+p.getCurrentTime()+' muted '+p.isMuted())},5000);"
                + "</script><script src='https://www.youtube.com/iframe_api'></script>";
        player.onResume();
        player.loadDataWithBaseURL("https://" + panel.getContext().getPackageName() + "/", html, "text/html", "UTF-8", null);
    }

    public void hide() {
        if (particles != null) particles.stop();
        if (current != null) Log.i("LumoraPreview", "PREVIEW:stopped " + current);
        generation++;
        if (pending != null) pending.cancel(true);
        panel.removeCallbacks(reveal);
        player.loadUrl("about:blank");
        player.onPause();
        panel.setVisibility(View.GONE);
        current = null;
        provider = null;
    }

    public void destroy() {
        hide();
        io.shutdownNow();
        player.destroy();
    }
}
