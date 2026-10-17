package com.starmasti.app;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

    /** More Video में वीडियो पर पहली बार क्लिक करने पर वह अपने आप शुरू न हो (रुका रहे); दोबारा टैप करने पर ही चले।
     *  सिर्फ़ सामान्य वीडियो (watch?v=...) पर लागू; Shorts पहले जैसे चलते हैं। */
    private static final String MV_HOLD_JS =
            "(function(){" +
            " if(window.__pkHold) return; window.__pkHold=1;" +
            " var held=false, settle=0, lastId='', sx=0, sy=0;" +
            " function vid(){ var m=location.href.match(/[?&]v=([\\w-]{11})/); return m?m[1]:''; }" +
            " function pauseAll(){ var v=document.querySelectorAll('video'); for(var i=0;i<v.length;i++){ try{ if(!v[i].paused) v[i].pause(); }catch(e){} } }" +
            " function check(){ var id=vid(); if(id&&id!==lastId){ lastId=id; held=true; settle=Date.now()+900; pauseAll(); } if(!id) lastId=''; if(held) pauseAll(); }" +
            " function release(){ if(held&&Date.now()>settle) held=false; }" +
            " function stop(e){ if(held&&e.target&&e.target.pause){ try{ e.target.pause(); }catch(x){} } }" +
            " document.addEventListener('play',stop,true);" +
            " document.addEventListener('playing',stop,true);" +
            " document.addEventListener('touchstart',function(e){ var t=e.touches&&e.touches[0]; if(t){ sx=t.clientX; sy=t.clientY; } },true);" +
            " document.addEventListener('touchend',function(e){ var t=e.changedTouches&&e.changedTouches[0]; if(t&&Math.abs(t.clientX-sx)<10&&Math.abs(t.clientY-sy)<10) release(); },true);" +
            " document.addEventListener('click',release,true);" +
            " setInterval(check,250); check();" +
            "})();";

    /** More Video की सूची/सर्च पर: लाल बटन दबाने पर स्क्रीन के वीडियो पर "▶ Room" बटन आते हैं; उस बटन से वीडियो रूम में जाकर चलता है */
    private static final String MV_PICK_JS =
            "(function(){ " +
            " if(window.__pkMv) return; window.__pkMv=1; " +
            " var on=false, layer=null, btns={}; " +
            " function vid(h){ var s=String(h); var m=s.match(/[?&]v=([\\w-]{11})/)||s.match(/\\/shorts\\/([\\w-]{11})/); return m?m[1]:''; } " +
            " function onWatch(){ return /[?&]v=[\\w-]{11}/.test(location.href)||/\\/shorts\\//.test(location.href); } " +
            " function layerEl(){ if(layer&&layer.isConnected) return layer; layer=document.createElement('div'); layer.id='pkMvLayer'; layer.style.cssText='position:fixed;left:0;top:0;width:100%;height:100%;pointer-events:none;z-index:2147483647;'; document.documentElement.appendChild(layer); return layer; } " +
            " function clearAll(){ if(layer) layer.innerHTML=''; btns={}; } " +
            " function mk(id,isShort){ " +
            "  var b=document.createElement('button'); b.type='button'; b.textContent='▶ Room'; " +
            "  b.style.cssText='position:absolute;pointer-events:auto;height:34px;padding:0 14px;border:0;border-radius:17px;background:#E11D48;color:#fff;font:800 13px/34px sans-serif;box-shadow:0 3px 10px rgba(0,0,0,.5);'; " +
            "  b.addEventListener('click',function(e){ e.preventDefault(); e.stopPropagation(); try{ AndroidMV.send('https://www.youtube.com/'+(isShort?'shorts/':'watch?v=')+id); }catch(x){} },true); " +
            "  layerEl().appendChild(b); return b; " +
            " } " +
            " function scan(){ " +
            "  if(!on) return; " +
            "  if(onWatch()){ on=false; clearAll(); return; } " +
            "  var vh=window.innerHeight, vw=window.innerWidth, best={}; " +
            "  var as=document.querySelectorAll('a[href*=\"/watch?v=\"],a[href*=\"/shorts/\"]'); " +
            "  for(var i=0;i<as.length;i++){ " +
            "   var a=as[i], h=a.getAttribute('href')||'', v=vid(h); if(!v) continue; " +
            "   var r=a.getBoundingClientRect(); " +
            "   if(r.width<110||r.height<60||r.bottom<60||r.top>vh-30||r.right<20||r.left>vw-20) continue; " +
            "   var ar=r.width*r.height; " +
            "   if(!best[v]||ar>best[v].ar) best[v]={r:r,ar:ar,s:/\\/shorts\\//.test(h)}; " +
            "  } " +
            "  for(var k in btns){ if(!best[k]){ if(btns[k].parentNode) btns[k].parentNode.removeChild(btns[k]); delete btns[k]; } } " +
            "  for(var id in best){ " +
            "   var o=best[id], b=btns[id]||(btns[id]=mk(id,o.s)); " +
            "   var top=Math.min(Math.max(o.r.top+8,64),o.r.bottom-42); " +
            "   b.style.top=Math.round(top)+'px'; b.style.left=Math.round(Math.max(o.r.left+8,4))+'px'; " +
            "  } " +
            " } " +
            " window.__pkMvToggle=function(){ on=!on; if(!on) clearAll(); else scan(); return on?'on':'off'; }; " +
            " window.__pkMvOff=function(){ on=false; clearAll(); }; " +
            " var t=0; function sched(){ if(t) return; t=requestAnimationFrame(function(){ t=0; scan(); }); } " +
            " window.addEventListener('scroll',sched,true); window.addEventListener('touchmove',sched,true); window.addEventListener('resize',sched); " +
            " setInterval(scan,350); " +
            "})(); ";

    // More Video: अलग पूरे पेज में खुलने वाला दूसरा WebView
    private FrameLayout mvRoot;
    private WebView mvWeb;
    private View mvLoading;      // More Video खुलते समय की लोडिंग
    private TextView mvSendBtn;  // नीचे का लाल बटन
    private boolean mvPickOn = false;
    private View splash;         // ऐप खुलते समय "स्टार मस्ती" वाली स्क्रीन
    // शुरुआती एनिमेशन: आइकन उभरता है, तारा उड़कर आता है, बिल्ली चलकर आती है और डांस करती है
    private boolean splashAnimated = false, splashRunning = false, splashHidePending = false;
    private long splashStartMs = 0;
    private int splashSz;
    private FrameLayout splashStage;
    private ImageView splashEmblem, splashStar, splashCat;
    private TextView splashTitle;
    private final List<Animator> splashAnims = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // फ़ोन घुमाने पर ऐप न घूमे
        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#F7F4FF")); // पेज आने से पहले सफ़ेद की जगह हल्का बैंगनी
        FrameLayout root = new FrameLayout(this);
        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        splash = buildSplash();
        root.addView(splash, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
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
            public void onPageFinished(WebView view, String url) {
                // "स्टार मस्ती" यहाँ नहीं हटती: वह तब हटती है जब HTML बताए कि Home/Login तैयार है (Android.appReady()).
                // सुरक्षा: HTML से संकेत न आए तो पेज लोड के 8 सेकंड बाद हटा दो।
                view.postDelayed(MainActivity.this::hideSplash, 8000);
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) hideSplash(); // नेट न हो तो भी स्क्रीन अटकी न रहे
            }
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
        webView.postDelayed(this::hideSplash, 30000); // सुरक्षा: 30 सेकंड बाद हर हाल में हटा दो
    }

    // ---------------------------------------------------------------------------------------------
    //  शुरुआती स्क्रीन: पेज लोड होने तक सफ़ेद की जगह "स्टार मस्ती"
    // ---------------------------------------------------------------------------------------------
    private View buildSplash() {
        float d = getResources().getDisplayMetrics().density;
        int purple = Color.parseColor("#4A148C");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.parseColor("#F7F4FF")); // स्टेटस-बार जैसा ही रंग, ताकि अलग पट्टी न दिखे
        box.setClickable(true);

        // ऊपर आपका लोगो (res/drawable-nodpi/splash_logo.png). फ़ाइल न मिले तो पहले जैसा ⭐ दिखेगा।
        int logoRes = getResources().getIdentifier("splash_logo", "drawable", getPackageName());
        int idE = getResources().getIdentifier("splash_emblem", "drawable", getPackageName());
        int idS = getResources().getIdentifier("splash_star", "drawable", getPackageName());
        int idC = getResources().getIdentifier("splash_cat", "drawable", getPackageName());
        View top = null;
        float pulseTo = 1.06f;
        if (idE != 0 && idS != 0 && idC != 0) {
            // तीन परतें: पीछे आइकन, उसके ऊपर तारा, सबसे ऊपर बिल्ली (तीनों एक ही नाप की पारदर्शी PNG)
            splashSz = (int) (230 * d);
            FrameLayout stage = new FrameLayout(this);
            stage.setClipChildren(false);
            box.setClipChildren(false);
            splashEmblem = mkLayer(idE);
            splashStar = mkLayer(idS);
            splashCat = mkLayer(idC);
            stage.addView(splashEmblem, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            stage.addView(splashStar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            stage.addView(splashCat, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            box.addView(stage, new LinearLayout.LayoutParams(splashSz, splashSz));
            splashStage = stage;
            splashAnimated = true;
        } else if (logoRes != 0) {
            ImageView logo = new ImageView(this);
            logo.setImageResource(logoRes);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            box.addView(logo, new LinearLayout.LayoutParams((int) (200 * d), (int) (200 * d)));
            top = logo;
            pulseTo = 1.06f;
        } else {
            TextView star = new TextView(this);
            star.setText("⭐");
            star.setTextSize(64);
            star.setGravity(Gravity.CENTER);
            box.addView(star);
            top = star;
            pulseTo = 1.18f;
        }
        // लोगो हल्का-हल्का धड़कता रहता है
        ScaleAnimation pulse = new ScaleAnimation(1f, pulseTo, 1f, pulseTo,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        pulse.setDuration(800);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        if (top != null) top.startAnimation(pulse);

        // बड़ा, मोटा, रंगीन (गुलाबी → बैंगनी → नारंगी) "स्टार मस्ती" हल्की चमक (glow) के साथ
        TextView title = new TextView(this);
        title.setText("स्टार मस्ती");
        title.setTextSize(48);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        title.setLetterSpacing(0.05f);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.parseColor("#8A2BE2"));
        float tw = title.getPaint().measureText("स्टार मस्ती");
        title.getPaint().setShader(new LinearGradient(0, 0, tw, 0,
                new int[]{Color.parseColor("#FF2D95"), Color.parseColor("#8A2BE2"), Color.parseColor("#FF8A00")},
                null, Shader.TileMode.CLAMP));
        title.setShadowLayer(14 * d, 0, 3 * d, Color.parseColor("#66B06BE0"));
        splashTitle = title;
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpTitle.topMargin = (int) (18 * d);
        box.addView(title, lpTitle);

        TextView sub = new TextView(this);
        sub.setText("लोड हो रहा है…");
        sub.setTextColor(Color.parseColor("#7B6A99"));
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpSub.topMargin = (int) (6 * d);
        box.addView(sub, lpSub);

        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminate(true);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Color.parseColor("#7C3AED")));
        LinearLayout.LayoutParams lpPb = new LinearLayout.LayoutParams((int) (34 * d), (int) (34 * d));
        lpPb.topMargin = (int) (22 * d);
        box.addView(pb, lpPb);
        if (splashAnimated) startSplashAnim();
        return box;
    }

    private ImageView mkLayer(int res) {
        ImageView v = new ImageView(this);
        v.setImageResource(res);
        v.setScaleType(ImageView.ScaleType.FIT_XY);
        return v;
    }

    /** पूरा क्रम: आइकन उभरे → तारा घूमता-उड़ता आकर अपनी जगह लगे (चमक के साथ) → बिल्ली दाएँ से चलकर आए → डांस करती रहे */
    private void startSplashAnim() {
        final float d = getResources().getDisplayMetrics().density;
        final int sz = splashSz;
        splashRunning = true;
        splashStartMs = SystemClock.uptimeMillis();

        splashEmblem.setAlpha(0f);
        splashEmblem.setScaleX(0.5f);
        splashEmblem.setScaleY(0.5f);

        splashStar.setPivotX(0.372f * sz);
        splashStar.setPivotY(0.559f * sz);
        splashStar.setAlpha(0f);
        splashStar.setScaleX(0.3f);
        splashStar.setScaleY(0.3f);
        splashStar.setRotation(-540f);
        splashStar.setTranslationX(-0.7f * sz);
        splashStar.setTranslationY(-1.0f * sz);

        splashCat.setPivotX(0.755f * sz);   // पैरों के पास से झूलेगी
        splashCat.setPivotY(0.957f * sz);
        splashCat.setTranslationX(330 * d); // स्क्रीन के दाएँ बाहर से शुरू

        if (splashTitle != null) { splashTitle.setAlpha(0f); splashTitle.setTranslationY(14 * d); }

        // 1) आइकन उभरता है
        splashEmblem.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(450).setInterpolator(new OvershootInterpolator(1.3f)).start();

        // 2) तारा उड़कर आता है और अपनी जगह लग जाता है
        splashStar.animate().alpha(1f).translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).rotation(0f)
                .setStartDelay(350).setDuration(800).setInterpolator(new DecelerateInterpolator(1.4f))
                .withEndAction(() -> {
                    if (!splashRunning) return;
                    splashStar.animate().scaleX(1.25f).scaleY(1.25f).setStartDelay(0).setDuration(110).withEndAction(() -> {
                        if (splashRunning) splashStar.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                    }).start();
                    sparkleBurst(0.372f * sz, 0.559f * sz, d);
                    if (splashTitle != null) splashTitle.animate().alpha(1f).translationY(0f).setDuration(500).start();
                }).start();

        // 3) बिल्ली चलकर आती है (ऊपर-नीचे उछलते और हल्का झूलते कदम)
        final ObjectAnimator walk = ObjectAnimator.ofFloat(splashCat, View.TRANSLATION_X, 330 * d, 0f);
        walk.setDuration(1700);
        walk.setStartDelay(900);
        walk.setInterpolator(new DecelerateInterpolator(0.9f));
        final ObjectAnimator bob = ObjectAnimator.ofFloat(splashCat, View.TRANSLATION_Y, 0f, -9 * d);
        bob.setDuration(190);
        bob.setStartDelay(900);
        bob.setRepeatMode(ValueAnimator.REVERSE);
        bob.setRepeatCount(ValueAnimator.INFINITE);
        final ObjectAnimator wad = ObjectAnimator.ofFloat(splashCat, View.ROTATION, -3f, 3f);
        wad.setDuration(190);
        wad.setStartDelay(900);
        wad.setRepeatMode(ValueAnimator.REVERSE);
        wad.setRepeatCount(ValueAnimator.INFINITE);
        walk.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                bob.cancel();
                wad.cancel();
                if (splashRunning) startCatDance(d);   // पहुँचते ही डांस; ऐप तैयार होने तक चलता रहेगा
            }
        });
        splashAnims.add(walk);
        splashAnims.add(bob);
        splashAnims.add(wad);
        walk.start();
        bob.start();
        wad.start();
    }

    private void startCatDance(float d) {
        splashCat.setTranslationY(0f);
        splashCat.setRotation(0f);
        ObjectAnimator sway = ObjectAnimator.ofFloat(splashCat, View.ROTATION, -9f, 9f);
        sway.setDuration(380);
        sway.setRepeatMode(ValueAnimator.REVERSE);
        sway.setRepeatCount(ValueAnimator.INFINITE);
        ObjectAnimator hop = ObjectAnimator.ofFloat(splashCat, View.TRANSLATION_Y, 0f, -16 * d);
        hop.setDuration(190);
        hop.setInterpolator(new LinearInterpolator());
        hop.setRepeatMode(ValueAnimator.REVERSE);
        hop.setRepeatCount(ValueAnimator.INFINITE);
        splashAnims.add(sway);
        splashAnims.add(hop);
        sway.start();
        hop.start();
    }

    /** तारा लगते ही चारों तरफ़ छोटे चमकते सितारे बिखरते हैं */
    private void sparkleBurst(float cx, float cy, float d) {
        if (splashStage == null) return;
        final FrameLayout stage = splashStage;
        int[] cols = {Color.parseColor("#FFD54F"), Color.parseColor("#FF2D95"), Color.parseColor("#8A2BE2"), Color.parseColor("#FF8A00")};
        for (int i = 0; i < 8; i++) {
            final TextView t = new TextView(this);
            t.setText("✦");
            t.setTextSize(16);
            t.setTextColor(cols[i % cols.length]);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = (int) (cx - 8 * d);
            lp.topMargin = (int) (cy - 12 * d);
            stage.addView(t, lp);
            double ang = i * Math.PI * 2 / 8;
            float dist = (46 + (i % 3) * 16) * d;
            t.animate().translationX((float) Math.cos(ang) * dist).translationY((float) Math.sin(ang) * dist)
                    .alpha(0f).rotation(180f).setDuration(700)
                    .withEndAction(() -> stage.removeView(t)).start();
        }
    }

    /** ऐप तैयार होने पर बुलाया जाता है. एनिमेशन वाली स्क्रीन कम से कम ~2.6 सेकंड दिखती है ताकि बिल्ली आकर डांस कर सके. */
    private void hideSplash() {
        if (splash == null) return;
        if (splashAnimated && splashStartMs > 0) {
            long left = 2600 - (SystemClock.uptimeMillis() - splashStartMs);
            if (left > 0) {
                if (!splashHidePending) { splashHidePending = true; splash.postDelayed(this::hideSplashNow, left); }
                return;
            }
        }
        hideSplashNow();
    }

    private void hideSplashNow() {
        final View v = splash;
        if (v == null) return;
        splash = null;
        splashRunning = false;
        for (Animator a : new ArrayList<>(splashAnims)) a.cancel();   // चलती एनिमेशन बंद
        splashAnims.clear();
        v.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            ViewGroup p = (ViewGroup) v.getParent();
            if (p != null) p.removeView(v);
        }).start();
    }

    // More Video खुलते समय की लोडिंग (गहरा बैकग्राउंड + लाल घूमता गोला), पेज आते ही हट जाती है
    private View buildMvLoading() {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.parseColor("#0F0F12"));
        box.setClickable(true);

        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminate(true);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Color.parseColor("#FF2D55")));
        box.addView(pb, new LinearLayout.LayoutParams((int) (44 * d), (int) (44 * d)));

        TextView t = new TextView(this);
        t.setText("More Video लोड हो रहा है…");
        t.setTextColor(Color.WHITE);
        t.setTextSize(14);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpT.topMargin = (int) (14 * d);
        box.addView(t, lpT);
        return box;
    }

    private void hideMvLoading() {
        final View v = mvLoading;
        if (v == null) return;
        mvLoading = null;
        v.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            ViewGroup p = (ViewGroup) v.getParent();
            if (p != null) p.removeView(v);
        }).start();
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
        /** HTML का पहला पेज (Home/Login) तैयार होने पर बुलाया जाता है; तब "स्टार मस्ती" स्क्रीन हटती है */
        @JavascriptInterface
        public void appReady() {
            runOnUiThread(() -> hideSplash());
        }
        /** More Video: HTML से window.Android.openMoreVideo("https://www.youtube.com/") बुलाने पर अलग पूरे पेज में खुलता है */
        @JavascriptInterface
        public void openMoreVideo(final String url) {
            runOnUiThread(() -> openMoreVideoPage(url));
        }
    }

    /** YouTube पेज पर आए "▶ Room" बटन इसे बुलाते हैं (सिर्फ़ youtube का वीडियो/Short लिंक मान्य) */
    private class MvBridge {
        @JavascriptInterface
        public void send(final String url) {
            runOnUiThread(() -> {
                String clean = cleanYoutubeUrl(url);
                if (clean != null) sendCleanToRoom(clean);
            });
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
        mvWeb.setBackgroundColor(Color.parseColor("#0F0F12"));
        WebSettings ws = mvWeb.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        mvWeb.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String u) {
                view.evaluateJavascript(MV_HOLD_JS, null); // पहले क्लिक पर वीडियो अपने आप न चले
                view.evaluateJavascript(MV_PICK_JS, null); // सूची के वीडियो पर "▶ Room" बटन दिखाने वाली स्क्रिप्ट
                view.postDelayed(MainActivity.this::hideMvLoading, 250);
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) hideMvLoading();
            }
            @Override
            public void doUpdateVisitedHistory(WebView view, String u, boolean isReload) {
                resetPickLabel(); // दूसरे पेज पर जाने पर बटन छुपाने की स्थिति वापस सामान्य
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String sc = request.getUrl().getScheme();
                // सिर्फ़ http/https यहीं खुलें; youtube:// या intent:// जैसे लिंक अनदेखे कर दो
                return !("http".equals(sc) || "https".equals(sc));
            }
        });
        mvWeb.setWebChromeClient(chrome);
        mvWeb.addJavascriptInterface(new MvBridge(), "AndroidMV");
        mvRoot.addView(mvWeb, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mvLoading = buildMvLoading();
        mvRoot.addView(mvLoading, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mvLoading.postDelayed(this::hideMvLoading, 12000); // सुरक्षा: 12 सेकंड बाद हर हाल में हटा दो

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
        send.setOnClickListener(v -> onMvSendTapped());
        mvSendBtn = send;
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
        mvLoading = null;
        mvSendBtn = null;
        mvPickOn = false;
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

    /** नीचे के लाल बटन का टैप:
     *  - वीडियो/Short खुला हो → वही रूम में भेज दो
     *  - सूची/सर्च खुली हो → स्क्रीन के वीडियो पर "▶ Room" बटन दिखाओ/छुपाओ */
    private void onMvSendTapped() {
        if (mvWeb == null) return;
        String clean = cleanYoutubeUrl(mvWeb.getUrl());
        if (clean != null) { sendCleanToRoom(clean); return; }
        mvWeb.evaluateJavascript("(function(){return window.__pkMvToggle?window.__pkMvToggle():'none';})()", value -> {
            boolean on = value != null && value.contains("on");
            mvPickOn = on;
            if (mvSendBtn != null) mvSendBtn.setText(on ? "✕  बटन छुपाएँ" : "▶  Room में भेजें  🪙");
            if (on) Toast.makeText(this, "जिस वीडियो को रूम में चलाना है, उसके लाल \"▶ Room\" बटन को दबाइए", Toast.LENGTH_LONG).show();
            else if (value != null && value.contains("none")) Toast.makeText(this, "पेज अभी लोड हो रहा है, थोड़ी देर बाद दबाइए", Toast.LENGTH_SHORT).show();
        });
    }

    private void resetPickLabel() {
        mvPickOn = false;
        if (mvSendBtn != null) mvSendBtn.setText("▶  Room में भेजें  🪙");
    }

    private void sendCleanToRoom(String clean) {
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
