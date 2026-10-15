package com.starmasti.app;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.view.Window;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.webkit.WebViewAssetLoader;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    /** खाली छोड़ेंगे तो ऐप के अंदर रखी assets/index.html चलेगी।
     *  अगर आपकी साइट ऑनलाइन (hosting) है तो उसका पूरा लिंक यहाँ डालें, जैसे "https://aapka-site.web.app/" */
    private static final String START_URL = "https://murga7178-cyber.github.io/Partyroom/";
    private static final String ASSET_URL = "https://appassets.androidplatform.net/assets/index.html";

    private static final int REQ_FILE = 11, REQ_PERM = 12;

    private WebView webView;
    private UnityRewardedHelper unity;
    private UnityInterstitialHelper unityInter;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private ValueCallback<Uri[]> filePathCb;
    private PermissionRequest pendingPerm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // फ़ोन घुमाने पर ऐप न घूमे
        webView = new WebView(this);
        setContentView(webView);
        setBarColor(Color.parseColor("#F7F4FF")); // शुरुआती रंग: ऐप का हल्का बैकग्राउंड

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setLoadWithOverviewMode(true);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String host = u.getHost() == null ? "" : u.getHost();
                String startHost = START_URL.isEmpty() ? "appassets.androidplatform.net" : Uri.parse(START_URL).getHost();
                boolean web = "http".equals(u.getScheme()) || "https".equals(u.getScheme());
                if (web && !host.equals(startHost)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> handleWebPermission(request));
            }
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCb != null) filePathCb.onReceiveValue(null);
                filePathCb = cb;
                try { startActivityForResult(params.createIntent(), REQ_FILE); }
                catch (Exception e) { filePathCb = null; return false; }
                return true;
            }
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view; customCb = callback;
                ((FrameLayout) getWindow().getDecorView()).addView(view,
                        new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                webView.setVisibility(View.GONE);
            }
            @Override
            public void onHideCustomView() { hideCustomView(); }
        });

        webView.addJavascriptInterface(new Bridge(), "Android");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (customView != null) hideCustomView();
                else if (webView.canGoBack()) webView.goBack();
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
            }
        });

        unity = new UnityRewardedHelper(this, webView);
        unity.start();
        unityInter = new UnityInterstitialHelper(this, webView);
        unityInter.start();

        webView.loadUrl(START_URL.isEmpty() ? ASSET_URL : START_URL);
    }

    /** HTML से बुलाए जाने वाले फ़ंक्शन: window.Android.showToast(..) और window.Android.showUnityRewardedAd() */
    private class Bridge {
        @JavascriptInterface
        public void showToast(final String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, String.valueOf(msg), Toast.LENGTH_SHORT).show());
        }
        @JavascriptInterface
        public void showUnityRewardedAd() {
            runOnUiThread(() -> unity.requestShow());
        }
        @JavascriptInterface
        public void setStatusBarColor(final String hex) {
            runOnUiThread(() -> { try { setBarColor(Color.parseColor(hex)); } catch (Exception ignored) { } });
        }
        @JavascriptInterface
        public void showUnityInterstitialAd() {
            runOnUiThread(() -> unityInter.requestShow());
        }
    }

    private void handleWebPermission(PermissionRequest request) {
        List<String> need = new ArrayList<>();
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) need.add(Manifest.permission.RECORD_AUDIO);
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) need.add(Manifest.permission.CAMERA);
        }
        List<String> missing = new ArrayList<>();
        for (String p : need) if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (missing.isEmpty()) { request.grant(request.getResources()); return; }
        pendingPerm = request;
        ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQ_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_PERM && pendingPerm != null) {
            boolean ok = results.length > 0;
            for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) pendingPerm.grant(pendingPerm.getResources()); else pendingPerm.deny();
            pendingPerm = null;
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE && filePathCb != null) {
            filePathCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            filePathCb = null;
        }
    }

    private void hideCustomView() {
        if (customView == null) return;
        ((FrameLayout) getWindow().getDecorView()).removeView(customView);
        customView = null;
        if (customCb != null) customCb.onCustomViewHidden();
        customCb = null;
        webView.setVisibility(View.VISIBLE);
    }

    /** स्टेटस-बार (टावर/बैटरी/घड़ी) दिखती रहती है; उसका रंग पेज के सबसे ऊपरी हिस्से के रंग जैसा किया जाता है,
     *  ताकि वह अलग पट्टी न लगे। आइकन का रंग (काला/सफ़ेद) रंग की चमक देखकर अपने आप चुना जाता है। */
    private void setBarColor(int color) {
        Window w = getWindow();
        w.setStatusBarColor(color);
        double lum = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        WindowCompat.getInsetsController(w, w.getDecorView()).setAppearanceLightStatusBars(lum > 0.55);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
