package com.starmasti.app;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.webkit.WebViewAssetLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    /** खाली छोड़ेंगे तो ऐप के अंदर रखी assets/index.html चलेगी।
     *  अगर आपकी साइट ऑनलाइन (hosting) है तो उसका पूरा लिंक यहाँ डालें, जैसे "https://aapka-site.web.app/" */
    private static final String START_URL = "https://murga7178-cyber.github.io/Partyroom/";
    private static final String ASSET_URL = "https://appassets.androidplatform.net/assets/index.html";

    private static final int REQ_FILE = 11, REQ_PERM = 12;

    private WebView webView;
    private WebChromeClient chrome;          // मुख्य WebView और More Video पेज दोनों यही इस्तेमाल करते हैं (फ़ुल-स्क्रीन वीडियो के लिए)
    private UnityRewardedHelper unity;
    private UnityInterstitialHelper unityInter;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private ValueCallback<Uri[]> filePathCb;
    private PermissionRequest pendingPerm;

    // More Video: अलग पूरे पेज में खुलने वाला दूसरा WebView
    private FrameLayout mvRoot;
    private WebView mvWeb;

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

        chrome = new WebChromeClient() {
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
                if (mvRoot != null) mvRoot.setVisibility(View.GONE);
            }
            @Override
            public void onHideCustomView() { hideCustomView(); }
        };
        webView.setWebChromeClient(chrome);

        webView.addJavascriptInterface(new Bridge(), "Android");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (customView != null) hideCustomView();
                else if (mvWeb != null) {
                    if (mvWeb.canGoBack()) mvWeb.goBack();
                    else closeMoreVideoPage();
                }
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
        /** More Video: HTML से window.Android.openMoreVideo("https://www.youtube.com/") बुलाने पर अलग पूरे पेज में खुलता है */
        @JavascriptInterface
        public void openMoreVideo(final String url) {
            runOnUiThread(() -> openMoreVideoPage(url));
        }
    }

    // ---------------------------------------------------------------------------------------------
    //  More Video पेज: ऐप के ऊपर एक अलग WebView (असली YouTube, iframe वाली रोक नहीं लगती)
    //  नीचे लाल "Room में भेजें" बटन: जो वीडियो/Short खुला है उसका लिंक रूम में भेज देता है (कॉइन HTML वाला ऐप काटता है)
    //  ऊपर दाएँ ✕ या फ़ोन का Back: बंद करने के लिए
    // ---------------------------------------------------------------------------------------------
    private void openMoreVideoPage(String url) {
        if (mvRoot != null) return;
        if (url == null || url.trim().isEmpty()) url = "https://www.youtube.com/";

        mvRoot = new FrameLayout(this);
        mvRoot.setBackgroundColor(Color.BLACK);
        mvRoot.setClickable(true);

        mvWeb = new WebView(this);
        WebSettings ws = mvWeb.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        mvWeb.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String sc = request.getUrl().getScheme();
                // सिर्फ़ http/https यहीं खुलें; youtube:// या intent:// जैसे लिंक अनदेखे कर दो
                return !("http".equals(sc) || "https".equals(sc));
            }
        });
        mvWeb.setWebChromeClient(chrome);
        mvRoot.addView(mvWeb, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        float d = getResources().getDisplayMetrics().density;

        // लाल बटन: Room में भेजें
        TextView send = new TextView(this);
        send.setText("▶  Room में भेजें  🪙");
        send.setTextColor(Color.WHITE);
        send.setTextSize(14);
        send.setTypeface(null, android.graphics.Typeface.BOLD);
        send.setGravity(Gravity.CENTER);
        send.setPadding((int) (18 * d), 0, (int) (18 * d), 0);
        GradientDrawable red = new GradientDrawable();
        red.setColor(Color.parseColor("#E11D48"));
        red.setCornerRadius(24 * d);
        send.setBackground(red);
        send.setElevation(8 * d);
        FrameLayout.LayoutParams lpSend = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (46 * d));
        lpSend.gravity = Gravity.BOTTOM | Gravity.END;
        lpSend.setMargins(0, 0, (int) (14 * d), (int) (86 * d));
        send.setOnClickListener(v -> sendCurrentVideoToRoom());
        mvRoot.addView(send, lpSend);

        // ✕ बंद करें
        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(Color.WHITE);
        close.setTextSize(16);
        close.setGravity(Gravity.CENTER);
        GradientDrawable dark = new GradientDrawable();
        dark.setShape(GradientDrawable.OVAL);
        dark.setColor(Color.parseColor("#B3000000"));
        close.setBackground(dark);
        close.setElevation(8 * d);
        FrameLayout.LayoutParams lpClose = new FrameLayout.LayoutParams((int) (38 * d), (int) (38 * d));
        lpClose.gravity = Gravity.TOP | Gravity.END;
        lpClose.setMargins(0, (int) (8 * d), (int) (8 * d), 0);
        close.setOnClickListener(v -> closeMoreVideoPage());
        mvRoot.addView(close, lpClose);

        ViewGroup content = findViewById(android.R.id.content);
        content.addView(mvRoot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mvWeb.loadUrl(url);
    }

    private void closeMoreVideoPage() {
        if (mvRoot == null) return;
        try {
            if (mvWeb != null) { mvWeb.stopLoading(); mvWeb.loadUrl("about:blank"); }
            ViewGroup content = findViewById(android.R.id.content);
            content.removeView(mvRoot);
            if (mvWeb != null) { mvRoot.removeView(mvWeb); mvWeb.destroy(); }
        } catch (Exception ignored) { }
        mvWeb = null;
        mvRoot = null;
    }

    /** खुले हुए YouTube पेज से वीडियो/Short की ID निकालकर साफ़ लिंक बनाता है; न मिले तो null */
    private static String cleanYoutubeUrl(String cur) {
        if (cur == null) return null;
        Matcher sh = Pattern.compile("/shorts/([A-Za-z0-9_-]{11})").matcher(cur);
        if (sh.find()) return "https://www.youtube.com/shorts/" + sh.group(1);
        Matcher m = Pattern.compile("(?:[?&]v=|youtu\\.be/|/embed/)([A-Za-z0-9_-]{11})").matcher(cur);
        if (m.find()) return "https://www.youtube.com/watch?v=" + m.group(1);
        return null;
    }

    private void sendCurrentVideoToRoom() {
        String clean = cleanYoutubeUrl(mvWeb == null ? null : mvWeb.getUrl());
        if (clean == null) {
            Toast.makeText(this, "पहले कोई वीडियो खोलिए, फिर Room में भेजिए", Toast.LENGTH_SHORT).show();
            return;
        }
        closeMoreVideoPage();
        // ऐप के HTML को वही संदेश भेजते हैं जो पहले More Video का पेज भेजता था; कॉइन कटना और प्ले होना वहीं होता है
        final String js = "window.postMessage({type:'PARTY_ROOM_YOUTUBE_SELECTED',url:'" + clean + "'},'*');";
        webView.evaluateJavascript(js, null);
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
        if (mvRoot != null) mvRoot.setVisibility(View.VISIBLE);
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
        closeMoreVideoPage();
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
