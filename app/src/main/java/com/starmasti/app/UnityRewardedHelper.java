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
 * Unity Rewarded Ad -> HTML (डेली चेक-इन) पुल।
 * HTML window.Android.showUnityRewardedAd() बुलाता है; विज्ञापन पूरा देखने पर यह
 * window.onUnityRewardedAdResult(true) चलाता है, वरना (false)।
 */
public class UnityRewardedHelper
        implements IUnityAdsInitializationListener, IUnityAdsLoadListener, IUnityAdsShowListener {

    private static final String TAG = "UnityRewardedHelper";

    // Unity Dashboard में ऐप Star Masti के नीचे दिखने वाली ID (Game ID). गलत हो तो init फेल होगा।
    private static final String GAME_ID = "800394722";
    private static final String PLACEMENT_ID = "BP_Rewarded_Android";
    // जाँच के समय true रखें (टेस्ट विज्ञापन हमेशा आते हैं). Play Store पर डालने से पहले false करें।
    private static final boolean TEST_MODE = true;

    private final Activity activity;
    private final WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());

    private boolean initialized = false;
    private boolean initializing = false;
    private boolean loading = false;
    private boolean loaded = false;
    private boolean showing = false;
    private boolean wantShow = false;
    private Runnable timeout;

    public UnityRewardedHelper(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    /** ऐप खुलते ही एक बार बुलाएँ (onCreate में). */
    public void start() {
        if (initialized || initializing) return;
        initializing = true;
        UnityAds.initialize(activity.getApplicationContext(), GAME_ID, TEST_MODE, this);
    }

    /** HTML के बटन से बुलाया जाता है (मुख्य थ्रेड पर). */
    public void requestShow() {
        if (showing) return;
        wantShow = true;
        armTimeout();
        if (!initialized) { start(); return; }
        if (loaded) { showNow(); } else { load(); }
    }

    private void load() {
        if (loading || loaded || !initialized) return;
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
        main.postDelayed(timeout, 20000); // 20 सेकंड में विज्ञापन तैयार न हो तो हार मान लो
    }

    private void cancelTimeout() {
        if (timeout != null) { main.removeCallbacks(timeout); timeout = null; }
    }

    private void sendResult(final boolean ok, String why) {
        Log.d(TAG, "result ok=" + ok + " why=" + why);
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                webView.evaluateJavascript("window.onUnityRewardedAdResult(" + ok + ")", null);
            }
        });
    }

    // ---- init ----
    @Override public void onInitializationComplete() {
        initialized = true; initializing = false;
        Log.d(TAG, "Unity Ads init OK");
        load(); // पहले से तैयार रखो ताकि बटन दबाते ही चले
    }

    @Override public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
        initialized = false; initializing = false;
        Log.e(TAG, "Unity Ads init FAILED: " + error + " / " + message);
        if (wantShow) { wantShow = false; cancelTimeout(); sendResult(false, "init: " + message); }
    }

    // ---- load ----
    @Override public void onUnityAdsAdLoaded(String placementId) {
        loading = false; loaded = true;
        Log.d(TAG, "Ad loaded: " + placementId);
        if (wantShow && !showing) showNow();
    }

    @Override public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) {
        loading = false; loaded = false;
        Log.e(TAG, "Ad load FAILED: " + error + " / " + message);
        if (wantShow) { wantShow = false; cancelTimeout(); sendResult(false, "load: " + message); }
    }

    // ---- show ----
    @Override public void onUnityAdsShowStart(String placementId) { }
    @Override public void onUnityAdsShowClick(String placementId) { }

    @Override public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {
        showing = false;
        Log.e(TAG, "Ad show FAILED: " + error + " / " + message);
        sendResult(false, "show: " + message);
        load();
    }

    @Override public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) {
        showing = false;
        boolean ok = (state == UnityAds.UnityAdsShowCompletionState.COMPLETED);
        sendResult(ok, "complete " + state);
        load(); // अगला विज्ञापन पहले से तैयार करो
    }
        }
