package com.starmasti.app;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.UnityAdsShowOptions;

/**
 * Unity Interstitial (बिना इनाम वाला) विज्ञापन -> HTML पुल।
 * HTML window.Android.showUnityInterstitialAd() बुलाता है (यूट्यूब वीडियो / गाना / गेम ख़त्म होने पर)।
 * विज्ञापन ख़त्म होने, फेल होने या देर होने पर हमेशा window.onUnityInterstitialResult(ok, "कारण") चलता है,
 * ताकि वीडियो/गाना कभी अटका न रहे।
 */
public class UnityInterstitialHelper
        implements IUnityAdsInitializationListener, IUnityAdsLoadListener, IUnityAdsShowListener {

    private static final String TAG = "UnityInterstitial";

    private static final String GAME_ID = "800394722";
    private static final String PLACEMENT_ID = "BP_Interstitial_Android";
    private static final boolean TEST_MODE = false;   // UnityRewardedHelper वाली सेटिंग जैसी रखें
    private static final long WAIT_MS = 10000;        // 10 सेकंड में विज्ञापन तैयार न हो तो आगे बढ़ जाओ

    private final Activity activity;
    private final WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());

    private boolean initializing = false;
    private boolean loading = false;
    private boolean loaded = false;
    private boolean showing = false;
    private boolean wantShow = false;
    private Runnable timeout;

    public UnityInterstitialHelper(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    /** ऐप खुलते ही बुलाएँ: विज्ञापन पहले से तैयार रखता है. */
    public void start() {
        if (UnityAds.isInitialized()) { load(); return; }
        if (initializing) return;
        initializing = true;
        UnityAds.initialize(activity.getApplicationContext(), GAME_ID, TEST_MODE, this);
    }

    /** HTML से (मुख्य थ्रेड पर) बुलाया जाता है. */
    public void requestShow() {
        if (showing) return;
        wantShow = true;
        armTimeout();
        if (!UnityAds.isInitialized()) { start(); return; }
        if (loaded) showNow(); else load();
    }

    private void load() {
        if (loading || loaded || !UnityAds.isInitialized()) return;
        loading = true;
        UnityAds.load(PLACEMENT_ID, this);
    }

    private void showNow() {
        cancelTimeout();
        wantShow = false;
        showing = true;
        loaded = false;
        UnityAds.show(activity, PLACEMENT_ID, new UnityAdsShowOptions(), this);
    }

    private void armTimeout() {
        cancelTimeout();
        timeout = new Runnable() {
            @Override public void run() {
                if (wantShow && !showing) { wantShow = false; sendResult(false, "timeout"); }
            }
        };
        main.postDelayed(timeout, WAIT_MS);
    }

    private void cancelTimeout() {
        if (timeout != null) { main.removeCallbacks(timeout); timeout = null; }
    }

    private void sendResult(final boolean ok, String why) {
        Log.d(TAG, "result ok=" + ok + " why=" + why);
        final String js = "window.onUnityInterstitialResult&&window.onUnityInterstitialResult(" + ok + ","
                + org.json.JSONObject.quote(String.valueOf(why)) + ")";
        activity.runOnUiThread(new Runnable() {
            @Override public void run() { webView.evaluateJavascript(js, null); }
        });
    }

    // ---- init ----
    @Override public void onInitializationComplete() {
        initializing = false;
        load();
    }

    @Override public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
        initializing = false;
        Log.e(TAG, "init FAILED: " + error + " / " + message);
        if (wantShow) { wantShow = false; cancelTimeout(); sendResult(false, "init: " + message); }
    }

    // ---- load ----
    @Override public void onUnityAdsAdLoaded(String placementId) {
        loading = false; loaded = true;
        if (wantShow && !showing) showNow();
    }

    @Override public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) {
        loading = false; loaded = false;
        Log.e(TAG, "load FAILED: " + error + " / " + message);
        if (wantShow) { wantShow = false; cancelTimeout(); sendResult(false, "load: " + message); }
    }

    // ---- show ----
    @Override public void onUnityAdsShowStart(String placementId) { }
    @Override public void onUnityAdsShowClick(String placementId) { }

    @Override public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {
        showing = false;
        Log.e(TAG, "show FAILED: " + error + " / " + message);
        sendResult(false, "show: " + message);
        load();
    }

    @Override public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) {
        showing = false;
        sendResult(true, "complete " + state);
        load(); // अगला विज्ञापन पहले से तैयार करो
    }
        }
