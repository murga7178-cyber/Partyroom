package com.starmasti.app;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.text.InputType;
import android.webkit.CookieManager;
import android.view.Window;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.widget.EditText;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
            " var on=false, timer=0; " +
            " function vid(h){ var s=String(h); var m=s.match(/[?&]v=([\\w-]{11})/)||s.match(/\\/shorts\\/([\\w-]{11})/); return m?m[1]:''; } " +
            " function onWatch(){ return /[?&]v=[\\w-]{11}/.test(location.href)||/\\/shorts\\//.test(location.href); } " +
            " function clearAll(){ var n=document.querySelectorAll('.pkMvBtn'); for(var i=0;i<n.length;i++){ if(n[i].parentNode) n[i].parentNode.removeChild(n[i]); } } " +
            " function mk(id,isShort){ " +
            "  var b=document.createElement('button'); b.type='button'; b.className='pkMvBtn'; b.textContent='▶ Room'; " +
            "  b.style.cssText='position:absolute;top:8px;left:8px;z-index:3;height:32px;padding:0 13px;margin:0;border:0;border-radius:16px;background:linear-gradient(135deg,#FF3B5C,#B5179E);color:#fff;font:800 13px/32px sans-serif;border:2px solid rgba(255,255,255,.55);box-shadow:0 4px 12px rgba(0,0,0,.45);'; " +
            "  b.addEventListener('click',function(e){ e.preventDefault(); e.stopPropagation(); try{ AndroidMV.send('https://www.youtube.com/'+(isShort?'shorts/':'watch?v=')+id); }catch(x){} },true); " +
            "  b.addEventListener('touchend',function(e){ e.stopPropagation(); },true); " +
            "  b.addEventListener('pointerup',function(e){ e.stopPropagation(); },true); " +
            "  return b; " +
            " } " +
            " function scan(){ " +
            "  if(!on){ if(document.querySelector('.pkMvBtn')) clearAll(); return; } " +
            "  if(onWatch()){ on=false; clearAll(); return; } " +
            "  var as=document.querySelectorAll('a[href*=\"/watch?v=\"],a[href*=\"/shorts/\"]'), best={}; " +
            "  for(var i=0;i<as.length;i++){ " +
            "   var a=as[i], h=a.getAttribute('href')||'', v=vid(h); if(!v) continue; " +
            "   var r=a.getBoundingClientRect(); " +
            "   if(r.width<110||r.height<60) continue; " +
            "   var ar=r.width*r.height; " +
            "   if(!best[v]||ar>best[v].ar) best[v]={a:a,ar:ar,s:/\\/shorts\\//.test(h)}; " +
            "  } " +
            "  for(var id in best){ " +
            "   var an=best[id].a; " +
            "   if(an.querySelector(':scope > .pkMvBtn')) continue; " +
            "   try{ if(getComputedStyle(an).position==='static') an.style.position='relative'; }catch(x){} " +
            "   an.appendChild(mk(id,best[id].s)); " +
            "  } " +
            " } " +
            " function later(){ if(timer) return; timer=setTimeout(function(){ timer=0; scan(); },250); } " +
            " function onNav(){ on=false; clearAll(); } " +
            " window.__pkMvToggle=function(){ on=!on; if(!on) clearAll(); else scan(); return on?'on':'off'; }; " +
            " window.__pkMvOff=function(){ on=false; clearAll(); }; " +
            " ['pushState','replaceState'].forEach(function(k){ var o=history[k]; history[k]=function(){ var r=o.apply(this,arguments); setTimeout(onNav,0); return r; }; }); " +
            " window.addEventListener('popstate',onNav); " +
            " try{ new MutationObserver(function(){ if(on) later(); }).observe(document.documentElement,{childList:true,subtree:true}); }catch(x){} " +
            " setInterval(scan,900); " +
            "})(); ";

    /** ब्राउज़िंग मोड: किसी भी साइट पर दिख रहे वीडियो पर "🔗 Copy" और (YouTube हो तो) "▶ Room" बटन */
    private static final String BW_PICK_JS =
            "(function(){ " +
            " if(window.__pkBw) return; window.__pkBw=1; " +
            " var on=false, layer=null, items={}, timer=0; " +
            " function yid(u){ var m=String(u).match(/(?:youtube\\.com\\/(?:watch\\?(?:[^#]*&)?v=|shorts\\/|embed\\/|live\\/)|youtu\\.be\\/)([\\w-]{11})/); return m?m[1]:''; } " +
            " function yurl(u){ var i=yid(u); if(!i) return ''; return /\\/shorts\\//.test(String(u))?'https://www.youtube.com/shorts/'+i:'https://www.youtube.com/watch?v='+i; } " +
            " function abs(h){ try{ return new URL(h,location.href).href; }catch(e){ return ''; } } " +
            " function isMedia(h){ return /\\.(mp4|webm|m3u8|mov|mkv|ogg)(\\?|#|$)/i.test(String(h)); } " +
            " var AD_TOKEN=/(^|[\\s_\\-:.\\/])(ad|ads|adv|advert|advertisement|adunit|adslot|adsbygoogle|adsense|adbox|sponsor|sponsored|promoted|dfp|doubleclick|googlesyndication|taboola|outbrain|mgid|revcontent|popunder|preroll|ima-ad|videoad|video-ad)([\\s_\\-:.\\/\\d]|$)/i; " +
            " var AD_URL=/doubleclick|googlesyndication|googleadservices|adservice\\.|\\/aclk\\?|adclick|adsystem|adnxs|taboola|outbrain|mgid\\.com|popads|exoclick|propellerads|juicyads|trafficjunky|clickadu|adsterra|\\/ads?\\/|[?&]ad(?:s|id|_id)?=/i; " +
            " function isAd(el){ " +
            "  var n=el, d=0; " +
            "  while(n&&n!==document.documentElement&&d<8){ " +
            "   var cl=(typeof n.className==='string')?n.className:((n.getAttribute&&n.getAttribute('class'))||''); " +
            "   var tag=(n.id||'')+' '+cl+' '+((n.getAttribute&&n.getAttribute('aria-label'))||''); " +
            "   if(AD_TOKEN.test(tag)) return true; " +
            "   if(n.getAttribute&&(n.getAttribute('data-ad')!==null||n.getAttribute('data-ad-slot')!==null||n.getAttribute('data-google-query-id')!==null||n.getAttribute('data-adsbygoogle-status')!==null)) return true; " +
            "   n=n.parentElement; d++; " +
            "  } " +
            "  return false; " +
            " } " +
            " function pageLike(h){ return /\\/(video|videos|watch|play|player|embed|clip|clips|reel|reels|movie|movies|film|vid|v|view|media|stream)(\\/|\\?|\\.|-|_|$)/i.test(h); } " +
            " function hasThumb(a){ " +
            "  if(a.querySelector('img,picture,video,canvas')) return true; " +
            "  try{ var bg=getComputedStyle(a).backgroundImage; if(bg&&bg!=='none') return true; var k=a.firstElementChild; if(k){ var b2=getComputedStyle(k).backgroundImage; if(b2&&b2!=='none') return true; } }catch(e){} " +
            "  return false; " +
            " } " +
            " function layerEl(){ if(layer&&layer.isConnected) return layer; layer=document.createElement('div'); layer.id='pkBwLayer'; layer.style.cssText='position:absolute;left:0;top:0;width:0;height:0;z-index:2147483647;'; document.documentElement.appendChild(layer); return layer; } " +
            " function clearAll(){ if(layer) layer.innerHTML=''; items={}; } " +
            " function mk(it){ " +
            "  var g=document.createElement('div'); g.style.cssText='position:absolute;display:flex;gap:6px;align-items:center;'; " +
            "  var b=document.createElement('button'); b.type='button'; b.textContent='▶ Room'; " +
            "  b.style.cssText='height:34px;padding:0 14px;margin:0;border:2px solid rgba(255,255,255,.65);border-radius:17px;background:linear-gradient(135deg,#FF3B5C,#B5179E);color:#fff;font:800 13px/30px sans-serif;box-shadow:0 4px 12px rgba(0,0,0,.45);'; " +
            "  b.addEventListener('click',function(e){ " +
            "   e.preventDefault(); e.stopPropagation(); " +
            "   try{ " +
            "    if(it.kind==='yt') AndroidMV.send(it.url); " +
            "    else if(it.kind==='direct') AndroidMV.sendDirect(it.url); " +
            "    else AndroidMV.resolveAndSend(it.url); " +
            "   }catch(x){} " +
            "  },true); " +
            "  b.addEventListener('touchend',function(e){ e.stopPropagation(); },true); " +
            "  b.addEventListener('pointerup',function(e){ e.stopPropagation(); },true); " +
            "  g.appendChild(b); layerEl().appendChild(g); return g; " +
            " } " +
            " function collect(){ " +
            "  var res={}, vh=window.innerHeight, pages=0; " +
            "  function add(kind,url,el){ " +
            "   if(!url||AD_URL.test(url)||isAd(el)) return; " +
            "   var r=el.getBoundingClientRect(); " +
            "   if(r.width<90||r.height<50||r.bottom<-1200||r.top>vh+1200) return; " +
            "   var key=(kind==='yt'?yid(url):url), ar=r.width*r.height; " +
            "   if(!res[key]||ar>res[key].ar) res[key]={kind:kind,url:kind==='yt'?yurl(url):url,r:r,ar:ar}; " +
            "  } " +
            "  var vs=document.querySelectorAll('video'); " +
            "  for(var i=0;i<vs.length;i++){ " +
            "   var v=vs[i]; if(v.loop&&v.muted) continue; " +
            "   var u=v.currentSrc||v.src||''; " +
            "   if(!u){ var so=v.querySelector('source[src]'); if(so) u=so.src||''; } " +
            "   if(!u){ u=v.getAttribute('data-src')||v.getAttribute('data-video')||v.getAttribute('data-mp4')||v.getAttribute('data-url')||''; if(u) u=abs(u); } " +
            "   if(u&&!/^blob:/.test(u)){ if(yid(u)) add('yt',u,v); else add('direct',u,v); } " +
            "   else if(yid(location.href)) add('yt',location.href,v); " +
            "   else add('page',location.href,v); " +
            "  } " +
            "  var fs=document.querySelectorAll('iframe[src]'); " +
            "  for(var k=0;k<fs.length;k++){ var s=fs[k].src; if(yid(s)) add('yt',s,fs[k]); } " +
            "  var as=document.querySelectorAll('a[href]'); " +
            "  for(var n=0;n<as.length;n++){ " +
            "   var a=as[n], h=abs(a.getAttribute('href')||''); if(!h) continue; " +
            "   if(yid(h)) add('yt',h,a); " +
            "   else if(isMedia(h)) add('direct',h,a); " +
            "   else if(pages<40&&pageLike(h)){ var rr=a.getBoundingClientRect(); if(rr.width>=140&&rr.height>=80&&hasThumb(a)){ pages++; add('page',h,a); } } " +
            "  } " +
            "  return res; " +
            " } " +
            " function scan(){ " +
            "  if(!on){ if(layer&&layer.firstChild) clearAll(); return; } " +
            "  var res=collect(), sx=window.pageXOffset||0, sy=window.pageYOffset||0; " +
            "  for(var key in items){ if(!res[key]){ var o=items[key]; if(o.parentNode) o.parentNode.removeChild(o); delete items[key]; } } " +
            "  for(var k2 in res){ " +
            "   var it=res[k2], g=items[k2]||(items[k2]=mk(it)); " +
            "   g.style.left=Math.round(Math.max(it.r.left+sx+8,sx+4))+'px'; g.style.top=Math.round(it.r.top+sy+8)+'px'; " +
            "  } " +
            " } " +
            " function later(){ if(timer) return; timer=setTimeout(function(){ timer=0; scan(); },200); } " +
            " function onNav(){ on=false; clearAll(); } " +
            " window.__pkBwToggle=function(){ on=!on; if(!on) clearAll(); else scan(); return on?'on':'off'; }; " +
            " window.__pkBwOff=function(){ on=false; clearAll(); }; " +
            " ['pushState','replaceState'].forEach(function(k){ var o=history[k]; history[k]=function(){ var r=o.apply(this,arguments); setTimeout(onNav,0); return r; }; }); " +
            " window.addEventListener('popstate',onNav); " +
            " try{ new MutationObserver(function(){ if(on) later(); }).observe(document.documentElement,{childList:true,subtree:true}); }catch(x){} " +
            " setInterval(scan,700); " +
            "})(); ";

    // ब्राउज़िंग मोड के सर्च इंजन
    private static final String[] ENG_LABELS = {"🔎 Google", "🦆 DuckDuckGo", "🅱️ Bing", "🦁 Brave", "🌐 Yandex", "🔒 Startpage", "🌱 Ecosia", "🇪🇺 Qwant", "🔍 Mojeek"};
    private static final String[] ENG_URLS = {
            "https://www.google.com/search?q=", "https://duckduckgo.com/?q=", "https://www.bing.com/search?q=",
            "https://search.brave.com/search?q=", "https://yandex.com/search/?text=", "https://www.startpage.com/sp/search?query=",
            "https://www.ecosia.org/search?q=", "https://www.qwant.com/?q=", "https://www.mojeek.com/search?q="};

    // More Video: अलग पूरे पेज में खुलने वाला दूसरा WebView
    private FrameLayout mvRoot;
    private WebView mvWeb;
    private View mvLoading;      // More Video खुलते समय की लोडिंग
    private TextView mvSendBtn;  // नीचे का लाल बटन
    private FrameLayout mvChooser;     // "ब्राउज़िंग / YouTube" चुनाव वाली खिड़की
    private int mvMode = 0;            // 0 = YouTube, 1 = ब्राउज़िंग
    private EditText bwInput;
    private TextView bwEngineBtn, bwPlaceholder;
    private View bwProgress;
    private int bwEngine = 1;          // DuckDuckGo पहले से चुना

    /** बंद करने पर पेज नष्ट नहीं होते, छुपा लिए जाते हैं: दोबारा खोलने पर ब्राउज़र/YouTube वहीं से मिलता है */
    private static class MvHolder {
        FrameLayout root; WebView web; TextView sendBtn, engineBtn, placeholder; EditText input; View progress;
    }
    private MvHolder mvHolderYt, mvHolderBw, mvHolderOwn;

    /** किस वेबसाइट के video को किस पेज का Referer देना है (hotlink रोकने वाली साइटों के लिए) */
    private final ConcurrentHashMap<String, String> mediaRefHosts = new ConcurrentHashMap<>();
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
                try {
                    String host = request.getUrl().getHost();
                    if (host != null && "GET".equalsIgnoreCase(request.getMethod())) {
                        String ref = mediaRefHosts.get(host);
                        if (ref != null) {
                            WebResourceResponse r = proxyMedia(request, ref);
                            if (r != null) return r;
                        }
                    }
                } catch (Exception ignored) { }
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
                else if (mvChooser != null) closeMvChooser();
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
        if (idE != 0 && idS != 0) {
            // परतें: पीछे आइकन, उसके ऊपर तारा, और (अगर splash_cat.png रखी हो तो) सबसे ऊपर बिल्ली. बिल्ली न रखें तो बिल्ली नहीं आएगी.
            splashSz = (int) (230 * d);
            FrameLayout stage = new FrameLayout(this);
            stage.setClipChildren(false);
            box.setClipChildren(false);
            splashEmblem = mkLayer(idE);
            splashStar = mkLayer(idS);
            splashCat = (idC != 0) ? mkLayer(idC) : null;
            stage.addView(splashEmblem, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            stage.addView(splashStar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (splashCat != null) stage.addView(splashCat, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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

        // "लोड हो रहा है" की जगह: उछलते म्यूज़िक नोट्स और नाचते साउंड-बार
        LinearLayout.LayoutParams lpMusic = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpMusic.topMargin = (int) (26 * d);
        box.addView(buildMusicLoader(
                new int[]{Color.parseColor("#FF2D95"), Color.parseColor("#8A2BE2"), Color.parseColor("#FF8A00"), Color.parseColor("#7C3AED")},
                Color.parseColor("#DB2777"), Color.parseColor("#7C3AED")), lpMusic);
        if (splashAnimated) startSplashAnim();
        return box;
    }

    /** नाचते साउंड-बार (equalizer): बार ऊपर-नीचे लहराते रहते हैं */
    private static class EqView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int[] cols;
        private final int n;
        private float phase = 0f;
        private ValueAnimator va;

        EqView(Context c, int[] cols, int n) {
            super(c);
            this.cols = cols;
            this.n = n;
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            va = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
            va.setDuration(850);
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.setInterpolator(new LinearInterpolator());
            va.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); });
            va.start();
        }

        @Override protected void onDetachedFromWindow() {
            if (va != null) { va.cancel(); va = null; }
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            float bw = w / (n * 1.7f);
            float gap = bw * 0.7f;
            float total = n * bw + (n - 1) * gap;
            float x = (w - total) / 2f;
            for (int i = 0; i < n; i++) {
                // हर बार की अलग लय: कोई ऊँचा, कोई नीचा, लगातार बदलता
                float s = (float) (0.5 + 0.5 * Math.sin(phase * (1 + (i % 3) * 0.5f) + i * 1.1f));
                float bh = h * (0.22f + 0.78f * s);
                paint.setColor(cols[i % cols.length]);
                rect.set(x, (h - bh) / 2f, x + bw, (h + bh) / 2f);
                canvas.drawRoundRect(rect, bw / 2f, bw / 2f, paint);
                x += bw + gap;
            }
        }
    }

    /** उछलता हुआ चित्र: view ऊपर-नीचे कूदता रहता है, स्क्रीन से हटते ही अपने आप रुक जाता है */
    private void bounce(final View v, float amp, long delay) {
        final ObjectAnimator a = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, -amp);
        a.setDuration(360);
        a.setStartDelay(delay);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new DecelerateInterpolator());
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View x) { }
            @Override public void onViewDetachedFromWindow(View x) { a.cancel(); }
        });
        a.start();
    }

    /** लोडिंग की जगह: ♪ [साउंड-बार] ♫ — नोट्स बारी-बारी से उछलते हैं और बार नाचते हैं */
    private View buildMusicLoader(int[] barColors, int note1Color, int note2Color) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);

        TextView n1 = new TextView(this);
        n1.setText("♪");
        n1.setTextSize(30);
        n1.setTextColor(note1Color);
        n1.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(n1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EqView eq = new EqView(this, barColors, 7);
        LinearLayout.LayoutParams lpEq = new LinearLayout.LayoutParams((int) (112 * d), (int) (46 * d));
        lpEq.leftMargin = (int) (10 * d);
        lpEq.rightMargin = (int) (10 * d);
        row.addView(eq, lpEq);

        TextView n2 = new TextView(this);
        n2.setText("♫");
        n2.setTextSize(30);
        n2.setTextColor(note2Color);
        n2.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(n2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bounce(n1, 14 * d, 0);
        bounce(n2, 14 * d, 180);   // दूसरा नोट आधी लय बाद उछलता है
        return row;
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

        if (splashCat != null) {
            splashCat.setPivotX(0.755f * sz);   // पैरों के पास से झूलेगी
            splashCat.setPivotY(0.957f * sz);
            splashCat.setTranslationX(330 * d); // स्क्रीन के दाएँ बाहर से शुरू
        }

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

        if (splashCat == null) return;   // बिल्ली की फ़ोटो नहीं रखी तो यहीं रुक जाओ

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
            long minMs = (splashCat != null) ? 2600 : 1800;   // बिल्ली के बिना थोड़ा कम
            long left = minMs - (SystemClock.uptimeMillis() - splashStartMs);
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
        // रूम जैसा बैंगनी → गुलाबी ग्रेडिएंट
        GradientDrawable bgGrad = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#2B0A63"), Color.parseColor("#6D28D9"), Color.parseColor("#DB2777")});
        box.setBackground(bgGrad);
        box.setClickable(true);

        int logoRes = getResources().getIdentifier("splash_logo", "drawable", getPackageName());
        if (logoRes != 0) {
            ImageView logo = new ImageView(this);
            logo.setImageResource(logoRes);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            box.addView(logo, new LinearLayout.LayoutParams((int) (96 * d), (int) (96 * d)));
        }

        TextView title = new TextView(this);
        title.setText("More Video");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        title.setLetterSpacing(0.04f);
        title.setGravity(Gravity.CENTER);
        title.setShadowLayer(10 * d, 0, 2 * d, Color.parseColor("#66000000"));
        LinearLayout.LayoutParams lpTi = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpTi.topMargin = (int) (14 * d);
        box.addView(title, lpTi);

        LinearLayout.LayoutParams lpMusic = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpMusic.topMargin = (int) (22 * d);
        box.addView(buildMusicLoader(
                new int[]{Color.WHITE, Color.parseColor("#FFD54F"), Color.parseColor("#FFB3E6"), Color.parseColor("#E9DDFF")},
                Color.parseColor("#FFD54F"), Color.WHITE), lpMusic);
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
        /** ID जैसी चीज़ें पक्के तौर पर कॉपी करने के लिए (WebView में वेब का clipboard कई बार नहीं चलता) */
        /** रूम का video player इस video के लिए सही Referer (जिस पेज पर वह मिला) याद रखवाता है */
        @JavascriptInterface
        public void registerMediaReferer(final String url, final String referer) {
            registerMediaRef(url, referer);
        }
        /** रूम के "सीधा video link" खाने में पेज का link डाला हो तो असली video link ढूँढ़ने के लिए */
        @JavascriptInterface
        public void resolveVideo(final String pageUrl) {
            if (pageUrl == null || !pageUrl.matches("(?i)^https?://\\S+$")) return;
            resolveForRoomLoader(pageUrl);
        }
        /** रूम के "📋 Paste" बटन के लिए: कॉपी किया हुआ लिंक पढ़ता है */
        @JavascriptInterface
        public String getClipboardText() {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this);
                    return t == null ? "" : t.toString();
                }
            } catch (Exception ignored) { }
            return "";
        }
        @JavascriptInterface
        public void copyText(final String text) {
            runOnUiThread(() -> {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("text", String.valueOf(text)));
                } catch (Exception ignored) { }
            });
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
        /** ब्राउज़िंग मोड का "🔗 Copy": सिर्फ़ http/https लिंक कॉपी होता है */
        @JavascriptInterface
        public void copy(final String url) {
            if (url == null || !url.matches("(?i)^https?://\\S+$")) return;
            runOnUiThread(() -> {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("video", url));
                    Toast.makeText(MainActivity.this, "✅ लिंक कॉपी हो गया", Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) { }
            });
        }
        /** हमारा अपना More Video पेज (more-video.html) सर्च के लिए बुलाता है: नतीजे ऐप के अंदर से YouTube से आते हैं */
        @JavascriptInterface
        public void ytSearch(final String q, final String cbId) {
            if (q == null || q.trim().isEmpty() || cbId == null || !cbId.matches("[A-Za-z0-9_]+")) return;
            new Thread(() -> {
                final String json = ytSearchJson(q.trim());
                runOnUiThread(() -> {
                    if (mvWeb != null) mvWeb.evaluateJavascript("window.__mvSearchDone&&window.__mvSearchDone("
                            + JSONObject.quote(cbId) + "," + JSONObject.quote(json) + ")", null);
                });
            }).start();
        }
        /** पेज के ✕ से More Video बंद (पेज छुप जाता है, नष्ट नहीं होता) */
        @JavascriptInterface
        public void close() {
            runOnUiThread(() -> hideMoreVideoPage());
        }
        /** ब्राउज़िंग से चुना सीधा video (YouTube नहीं): रूम के HTML को संदेश भेजता है; कॉइन काटना और चलाना वहीं होता है */
        /** ब्राउज़िंग में वेबपेज-लिंक पर ▶ Room: पेज के अंदर से असली video ढूँढकर भेजना */
        @JavascriptInterface
        public void resolveAndSend(final String pageUrl) {
            if (pageUrl == null || !pageUrl.matches("(?i)^https?://\\S+$")) return;
            runOnUiThread(() -> resolveAndSendToRoom(pageUrl));
        }
        @JavascriptInterface
        public void sendDirect(final String url) {
            if (url == null || !url.matches("(?i)^https?://\\S+$")) return;
            runOnUiThread(() -> {
                String ref = (mvWeb != null && mvWeb.getUrl() != null) ? mvWeb.getUrl() : "";
                sendDirectToRoom(url, ref);
            });
        }
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
    //  बंद करने के लिए: फ़ोन का Back (YouTube में पीछे जाते-जाते आख़िर में पेज बंद हो जाता है)
    // ---------------------------------------------------------------------------------------------
    /** More Video दबाने पर पहले चुनाव: ब्राउज़िंग से वीडियो लाओ, या सीधे YouTube */
    private void openMoreVideoPage(String url) {
        if (mvRoot != null || mvChooser != null) return;
        final String u = (url == null || url.trim().isEmpty()) ? "https://www.youtube.com/" : url;
        final float d = getResources().getDisplayMetrics().density;

        FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(Color.parseColor("#D9140A2E"));
        dim.setClickable(true);
        dim.setOnClickListener(v -> closeMvChooser());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (18 * d), (int) (20 * d), (int) (18 * d), (int) (14 * d));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#FFFFFF"));
        cardBg.setCornerRadius(28 * d);
        card.setBackground(cardBg);
        card.setElevation(16 * d);
        card.setClickable(true);

        TextView title = new TextView(this);
        title.setText("More Video");
        title.setTextColor(Color.parseColor("#4C1D95"));
        title.setTextSize(24);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView sub = new TextView(this);
        sub.setText("आप क्या करना चाहते हैं?");
        sub.setTextColor(Color.parseColor("#7B6A99"));
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpSub.topMargin = (int) (4 * d);
        lpSub.bottomMargin = (int) (14 * d);
        card.addView(sub, lpSub);

        card.addView(makeChoice("🌐  ब्राउज़िंग से वीडियो लाएँ", "सर्च करो, वीडियो चुनो, लिंक कॉपी करो या रूम में भेजो",
                "#7C3AED", "#DB2777", () -> { closeMvChooser(); startBrowseMode(); }));
        card.addView(makeChoice("▶  YouTube में जाओ", "सीधे YouTube खोलो और वीडियो चुनो",
                "#FF3B5C", "#B5179E", () -> { closeMvChooser(); startYoutubeMode(u); }));

        TextView cancel = new TextView(this);
        cancel.setText("बंद करें");
        cancel.setTextColor(Color.parseColor("#7B6A99"));
        cancel.setTextSize(14);
        cancel.setTypeface(Typeface.DEFAULT_BOLD);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, (int) (10 * d), 0, (int) (4 * d));
        cancel.setOnClickListener(v -> closeMvChooser());
        card.addView(cancel);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        lp.setMargins((int) (22 * d), 0, (int) (22 * d), 0);
        dim.addView(card, lp);

        ViewGroup content = findViewById(android.R.id.content);
        content.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mvChooser = dim;
    }

    private LinearLayout makeChoice(String title, String sub, String c1, String c2, final Runnable action) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding((int) (18 * d), (int) (14 * d), (int) (18 * d), (int) (14 * d));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor(c1), Color.parseColor(c2)});
        g.setCornerRadius(20 * d);
        b.setBackground(g);
        b.setElevation(6 * d);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        b.addView(t);
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextColor(Color.parseColor("#F3E8FF"));
        s.setTextSize(12);
        LinearLayout.LayoutParams lpS = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpS.topMargin = (int) (3 * d);
        b.addView(s, lpS);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (12 * d);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void closeMvChooser() {
        if (mvChooser == null) return;
        try {
            ViewGroup p = (ViewGroup) mvChooser.getParent();
            if (p != null) p.removeView(mvChooser);
        } catch (Exception ignored) { }
        mvChooser = null;
    }

    // ---------------------------------------------------------------------------------------------
    //  ब्राउज़िंग मोड: ऊपर सर्च इंजन + सर्च बॉक्स, नीचे पूरा वेब पेज (असली, iframe नहीं, इसलिए सर्च चलता है).
    //  नीचे "🎬 वीडियो चुनो": स्क्रीन के वीडियो पर "🔗 Copy" और (YouTube हो तो) "▶ Room" बटन आते हैं.
    // ---------------------------------------------------------------------------------------------
    private void startBrowseMode() {
        if (mvRoot != null) return;
        if (mvHolderBw != null) { restoreHolder(mvHolderBw, 1); return; }   // पहले का ब्राउज़िंग पेज जैसा था वैसा ही
        mvMode = 1;
        final float d = getResources().getDisplayMetrics().density;

        mvRoot = new FrameLayout(this);
        mvRoot.setBackgroundColor(Color.parseColor("#0F0F12"));
        mvRoot.setClickable(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        mvRoot.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ऊपर की पट्टी
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int) (10 * d), (int) (8 * d), (int) (10 * d), (int) (8 * d));
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#4C1D95"), Color.parseColor("#9D1FB3"), Color.parseColor("#DB2777")}));

        bwEngineBtn = new TextView(this);
        bwEngineBtn.setText(ENG_LABELS[bwEngine] + " ▾");
        bwEngineBtn.setTextColor(Color.WHITE);
        bwEngineBtn.setTextSize(12);
        bwEngineBtn.setTypeface(Typeface.DEFAULT_BOLD);
        bwEngineBtn.setSingleLine(true);
        bwEngineBtn.setGravity(Gravity.CENTER);
        bwEngineBtn.setPadding((int) (10 * d), 0, (int) (10 * d), 0);
        GradientDrawable engBg = new GradientDrawable();
        engBg.setColor(Color.parseColor("#33FFFFFF"));
        engBg.setCornerRadius(22 * d);
        bwEngineBtn.setBackground(engBg);
        bwEngineBtn.setOnClickListener(v -> showEnginePicker());
        bar.addView(bwEngineBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (40 * d)));

        bwInput = new EditText(this);
        bwInput.setHint("वेब पर खोजें…");
        bwInput.setHintTextColor(Color.parseColor("#9A8DB5"));
        bwInput.setTextColor(Color.parseColor("#1F1235"));
        bwInput.setTextSize(14);
        bwInput.setSingleLine(true);
        bwInput.setInputType(InputType.TYPE_CLASS_TEXT);
        bwInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        bwInput.setPadding((int) (14 * d), 0, (int) (14 * d), 0);
        GradientDrawable inBg = new GradientDrawable();
        inBg.setColor(Color.WHITE);
        inBg.setCornerRadius(22 * d);
        bwInput.setBackground(inBg);
        bwInput.setOnEditorActionListener((v, actionId, ev) -> { doBrowseSearch(); return true; });
        LinearLayout.LayoutParams lpIn = new LinearLayout.LayoutParams(0, (int) (40 * d), 1f);
        lpIn.leftMargin = (int) (8 * d);
        lpIn.rightMargin = (int) (8 * d);
        bar.addView(bwInput, lpIn);

        TextView go = new TextView(this);
        go.setText("खोजें");
        go.setTextColor(Color.WHITE);
        go.setTextSize(13);
        go.setTypeface(Typeface.DEFAULT_BOLD);
        go.setGravity(Gravity.CENTER);
        go.setPadding((int) (14 * d), 0, (int) (14 * d), 0);
        GradientDrawable goBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#FF4D4D"), Color.parseColor("#FF9A3C")});
        goBg.setCornerRadius(22 * d);
        go.setBackground(goBg);
        go.setOnClickListener(v -> doBrowseSearch());
        bar.addView(go, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (40 * d)));
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // लोड होते समय पतली पट्टी
        android.widget.ProgressBar pb = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setIndeterminate(true);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Color.parseColor("#FF9A3C")));
        pb.setVisibility(View.GONE);
        bwProgress = pb;
        col.addView(pb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (3 * d)));

        // नीचे: वेब पेज + शुरुआती संदेश + वीडियो चुनो बटन
        FrameLayout body = new FrameLayout(this);
        col.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mvWeb = new WebView(this);
        mvWeb.setBackgroundColor(Color.WHITE);
        WebSettings ws = mvWeb.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);   // कई साइटें नए विंडो में वीडियो-पेज खोलती हैं
        ws.setSupportMultipleWindows(true);
        try { CookieManager.getInstance().setAcceptThirdPartyCookies(mvWeb, true); } catch (Exception ignored) { }
        mvWeb.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String u, android.graphics.Bitmap fav) {
                if (bwProgress != null) bwProgress.setVisibility(View.VISIBLE);
            }
            @Override
            public void onPageFinished(WebView view, String u) {
                if (bwProgress != null) bwProgress.setVisibility(View.GONE);
                view.evaluateJavascript(MV_HOLD_JS, null);   // YouTube वीडियो खुले तो पहले क्लिक पर अपने आप न चले
                view.evaluateJavascript(BW_PICK_JS, null);   // वीडियो चुनने वाली स्क्रिप्ट
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    if (bwProgress != null) bwProgress.setVisibility(View.GONE);
                    String why = (error != null && error.getDescription() != null) ? error.getDescription().toString() : "";
                    view.loadDataWithBaseURL(null,
                            "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                                    + "<body style='font-family:sans-serif;text-align:center;padding:48px 22px;background:#F7F4FF;color:#4C1D95'>"
                                    + "<div style='font-size:54px'>😕</div><h2>यह साइट नहीं खुल पाई</h2>"
                                    + "<p style='color:#7B6A99;font-size:14px'>" + why.replace("<", "&lt;") + "</p>"
                                    + "<p style='color:#7B6A99;font-size:14px'>दूसरा सर्च इंजन या दूसरी साइट आज़माइए</p></body></html>",
                            "text/html", "UTF-8", null);
                }
            }
            @Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();   // असुरक्षित सर्टिफ़िकेट वाली साइट नहीं खुलेगी
            }
            @Override
            public void doUpdateVisitedHistory(WebView view, String u, boolean isReload) {
                resetPickLabel();
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String sc = request.getUrl().getScheme();
                return !("http".equals(sc) || "https".equals(sc));   // youtube:// intent:// जैसे लिंक अनदेखे
            }
        });
        mvWeb.setWebChromeClient(new WebChromeClient() {
            // नए विंडो में खुलने वाला लिंक (सिर्फ़ उँगली से दबाने पर) इसी पेज में खोलो; अपने-आप खुलने वाले pop-up ऐड रोक दो
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                if (!isUserGesture) return false;
                final WebView tmp = new WebView(MainActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView v, String u, android.graphics.Bitmap fav) {
                        if (u != null && u.startsWith("http") && mvWeb != null) mvWeb.loadUrl(u);
                        v.stopLoading();
                        v.post(v::destroy);
                    }
                });
                WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
                t.setWebView(tmp);
                resultMsg.sendToTarget();
                return true;
            }
            @Override public void onShowCustomView(View v, CustomViewCallback cb) { chrome.onShowCustomView(v, cb); }
            @Override public void onHideCustomView() { chrome.onHideCustomView(); }
            @Override public void onPermissionRequest(PermissionRequest r) { chrome.onPermissionRequest(r); }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) { return chrome.onShowFileChooser(v, cb, p); }
        });
        mvWeb.addJavascriptInterface(new MvBridge(), "AndroidMV");
        body.addView(mvWeb, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bwPlaceholder = new TextView(this);
        bwPlaceholder.setText("🔎\nऊपर कुछ सर्च करें\nरिज़ल्ट इसी पेज में खुलेंगे");
        bwPlaceholder.setTextColor(Color.parseColor("#6B5B8A"));
        bwPlaceholder.setTextSize(16);
        bwPlaceholder.setGravity(Gravity.CENTER);
        bwPlaceholder.setBackgroundColor(Color.parseColor("#F7F4FF"));
        bwPlaceholder.setClickable(true);
        body.addView(bwPlaceholder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView pick = new TextView(this);
        pick.setText("🎬  वीडियो चुनो");
        pick.setTextColor(Color.WHITE);
        pick.setTextSize(15);
        pick.setTypeface(Typeface.DEFAULT_BOLD);
        pick.setGravity(Gravity.CENTER);
        pick.setPadding((int) (22 * d), 0, (int) (22 * d), 0);
        GradientDrawable pickBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#FF3B5C"), Color.parseColor("#E11D48"), Color.parseColor("#B5179E")});
        pickBg.setCornerRadius(26 * d);
        pickBg.setStroke((int) (2 * d), Color.parseColor("#66FFFFFF"));
        pick.setBackground(pickBg);
        pick.setElevation(10 * d);
        pick.setOnClickListener(v -> onBwPickTapped());
        mvSendBtn = pick;
        FrameLayout.LayoutParams lpPick = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (50 * d));
        lpPick.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lpPick.bottomMargin = (int) (22 * d);
        body.addView(pick, lpPick);

        ViewGroup content = findViewById(android.R.id.content);
        content.addView(mvRoot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mvHolderBw = snapshotHolder();
        bwInput.requestFocus();
    }

    private void showEnginePicker() {
        new AlertDialog.Builder(this)
                .setTitle("सर्च इंजन चुनें")
                .setItems(ENG_LABELS, (dlg, which) -> {
                    bwEngine = which;
                    if (bwEngineBtn != null) bwEngineBtn.setText(ENG_LABELS[bwEngine] + " ▾");
                })
                .show();
    }

    private void doBrowseSearch() {
        if (bwInput == null || mvWeb == null) return;
        String q = bwInput.getText().toString().trim();
        if (q.isEmpty()) return;
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(bwInput.getWindowToken(), 0);
        } catch (Exception ignored) { }
        String url;
        if (q.matches("(?i)^https?://\\S+$")) url = q;
        else if (q.matches("^[\\w-]+(\\.[\\w-]+)+(/\\S*)?$")) url = "https://" + q;
        else url = ENG_URLS[bwEngine] + Uri.encode(q);
        if (bwPlaceholder != null) bwPlaceholder.setVisibility(View.GONE);
        resetPickLabel();
        mvWeb.loadUrl(url);
    }

    /** नीचे का "🎬 वीडियो चुनो": स्क्रीन के वीडियो पर बटन दिखाओ/छुपाओ */
    private void onBwPickTapped() {
        if (mvWeb == null) return;
        mvWeb.evaluateJavascript("(function(){return window.__pkBwToggle?window.__pkBwToggle():'none';})()", value -> {
            boolean on = "\"on\"".equals(value);
            boolean none = "\"none\"".equals(value);
            mvPickOn = on;
            if (mvSendBtn != null) mvSendBtn.setText(on ? "✕  बटन छुपाएँ" : "🎬  वीडियो चुनो");
            if (on) Toast.makeText(this, "जिस वीडियो को रूम में चलाना है, उसके ▶ Room बटन को दबाइए", Toast.LENGTH_LONG).show();
            else if (none) Toast.makeText(this, "पहले कुछ सर्च करके पेज खोलिए", Toast.LENGTH_SHORT).show();
        });
    }

    private MvHolder snapshotHolder() {
        MvHolder h = new MvHolder();
        h.root = mvRoot; h.web = mvWeb; h.sendBtn = mvSendBtn;
        h.engineBtn = bwEngineBtn; h.placeholder = bwPlaceholder; h.input = bwInput; h.progress = bwProgress;
        return h;
    }

    private void restoreHolder(MvHolder h, int mode) {
        mvMode = mode;
        mvRoot = h.root; mvWeb = h.web; mvSendBtn = h.sendBtn; mvLoading = null;
        bwEngineBtn = h.engineBtn; bwPlaceholder = h.placeholder; bwInput = h.input; bwProgress = h.progress;
        ViewGroup content = findViewById(android.R.id.content);
        if (mvRoot.getParent() == null) {
            content.addView(mvRoot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        try { mvWeb.onResume(); } catch (Exception ignored) { }
        resetPickLabel();   // बटन बंद, नीचे का बटन सामान्य
    }

    /** हमारा अपना YouTube-जैसा पेज (more-video.html, GitHub Pages से): वीडियो दबाने पर ऊपर प्लेयर, नीचे सूची */
    private void startOwnMode(String fallbackUrl) {
        if (mvRoot != null) return;
        if (mvHolderOwn != null) { restoreHolder(mvHolderOwn, 2); return; }
        if (START_URL.isEmpty()) { startYoutubeMode(fallbackUrl); return; }
        mvMode = 2;
        mvRoot = new FrameLayout(this);
        mvRoot.setBackgroundColor(Color.parseColor("#0F0F14"));
        mvRoot.setClickable(true);

        mvWeb = new WebView(this);
        mvWeb.setBackgroundColor(Color.parseColor("#0F0F14"));
        WebSettings ws = mvWeb.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        final String startHost = Uri.parse(START_URL).getHost();
        mvWeb.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String u) { view.postDelayed(MainActivity.this::hideMvLoading, 250); }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    hideMvLoading();
                    view.loadDataWithBaseURL(null,
                            "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                                    + "<body style='font-family:sans-serif;text-align:center;padding:60px 22px;background:#0F0F14;color:#e9ddff'>"
                                    + "<div style='font-size:54px'>😕</div><h2>More Video खुल नहीं पाया</h2>"
                                    + "<p style='color:#b9a9dd;font-size:14px'>नेट जाँचिए, या GitHub में more-video.html डाली है या नहीं देखिए।</p></body></html>",
                            "text/html", "UTF-8", null);
                }
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String h = request.getUrl().getHost();
                return h == null || !h.equals(startHost);   // पेज के बाहर कहीं नहीं जाना
            }
        });
        mvWeb.setWebChromeClient(chrome);   // YouTube प्लेयर का फ़ुल-स्क्रीन भी चलेगा
        mvWeb.addJavascriptInterface(new MvBridge(), "AndroidMV");
        mvRoot.addView(mvWeb, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mvLoading = buildMvLoading();
        mvRoot.addView(mvLoading, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mvLoading.postDelayed(this::hideMvLoading, 12000);

        ViewGroup content = findViewById(android.R.id.content);
        content.addView(mvRoot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mvHolderOwn = snapshotHolder();
        mvWeb.loadUrl(START_URL + "more-video.html");
    }

    /** YouTube का सर्च पेज पढ़कर वीडियो की सूची (JSON) बनाता है: id, title, ch, dur, views, when, short */
    private String ytSearchJson(String q) {
        HttpURLConnection c = null;
        try {
            URL u = new URL("https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8") + "&hl=hi&gl=IN");
            c = (HttpURLConnection) u.openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(9000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");
            c.setRequestProperty("Accept-Language", "hi-IN,hi;q=0.9,en;q=0.8");
            c.setRequestProperty("Cookie", "CONSENT=YES+1; SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg");
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < 5000000) { bo.write(buf, 0, n); total += n; }
            String html = bo.toString("UTF-8");
            int k = html.indexOf("ytInitialData");
            if (k < 0) return "[]";
            int s0 = html.indexOf('{', k);
            if (s0 < 0) return "[]";
            int depth = 0, end = -1;
            boolean inStr = false, esc = false;
            for (int i = s0; i < html.length(); i++) {
                char ch = html.charAt(i);
                if (inStr) {
                    if (esc) esc = false; else if (ch == '\\') esc = true; else if (ch == '"') inStr = false;
                } else if (ch == '"') inStr = true;
                else if (ch == '{') depth++;
                else if (ch == '}') { depth--; if (depth == 0) { end = i; break; } }
            }
            if (end < 0) return "[]";
            JSONObject root = new JSONObject(html.substring(s0, end + 1));
            JSONArray out = new JSONArray();
            collectVideos(root, out, new HashSet<String>());
            return out.toString();
        } catch (Exception e) {
            return "[]";
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void collectVideos(Object node, JSONArray out, Set<String> seen) {
        if (out.length() >= 60) return;
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            JSONObject vr = o.optJSONObject("videoRenderer");
            if (vr != null) addVideo(vr, false, out, seen);
            JSONObject rr = o.optJSONObject("reelItemRenderer");
            if (rr != null) addVideo(rr, true, out, seen);
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String key = it.next();
                if ("videoRenderer".equals(key) || "reelItemRenderer".equals(key)) continue;
                collectVideos(o.opt(key), out, seen);
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collectVideos(a.opt(i), out, seen);
        }
    }

    private static String ytText(JSONObject o, String key) {
        JSONObject t = o.optJSONObject(key);
        if (t == null) return "";
        String s = t.optString("simpleText", "");
        if (!s.isEmpty()) return s;
        JSONArray r = t.optJSONArray("runs");
        if (r == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < r.length(); i++) {
            JSONObject x = r.optJSONObject(i);
            if (x != null) b.append(x.optString("text", ""));
        }
        return b.toString();
    }

    private void addVideo(JSONObject v, boolean reel, JSONArray out, Set<String> seen) {
        String id = v.optString("videoId", "");
        if (id.length() != 11 || !seen.add(id)) return;
        try {
            JSONObject it = new JSONObject();
            it.put("id", id);
            it.put("title", reel ? ytText(v, "headline") : ytText(v, "title"));
            String ch = ytText(v, "ownerText");
            if (ch.isEmpty()) ch = ytText(v, "longBylineText");
            it.put("ch", reel ? "" : ch);
            it.put("dur", reel ? "" : ytText(v, "lengthText"));
            String views = ytText(v, "viewCountText");
            if (views.isEmpty()) views = ytText(v, "shortViewCountText");
            it.put("views", views);
            it.put("when", reel ? "" : ytText(v, "publishedTimeText"));
            it.put("short", reel);
            out.put(it);
        } catch (Exception ignored) { }
    }

    private void startYoutubeMode(String url) {
        if (mvRoot != null) return;
        if (mvHolderYt != null) { restoreHolder(mvHolderYt, 0); return; }   // पहले का YouTube पेज जैसा था वैसा ही
        mvMode = 0;
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
        GradientDrawable red = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#FF3B5C"), Color.parseColor("#E11D48"), Color.parseColor("#B5179E")});
        red.setCornerRadius(26 * d);
        red.setStroke((int) (2 * d), Color.parseColor("#66FFFFFF"));
        send.setBackground(red);
        send.setElevation(10 * d);
        send.setLetterSpacing(0.02f);
        FrameLayout.LayoutParams lpSend = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (46 * d));
        lpSend.gravity = Gravity.BOTTOM | Gravity.END;
        lpSend.setMargins(0, 0, (int) (14 * d), (int) (86 * d));
        send.setOnClickListener(v -> onMvSendTapped());
        mvSendBtn = send;
        mvRoot.addView(send, lpSend);

        ViewGroup content = findViewById(android.R.id.content);
        content.addView(mvRoot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mvHolderYt = snapshotHolder();
        mvWeb.loadUrl(url);
    }

    /** More Video बंद करना: पेज नष्ट नहीं होता, छुप जाता है (वीडियो रुक जाता है). दोबारा खोलने पर वहीं से मिलेगा. */
    private void hideMoreVideoPage() {
        if (mvRoot == null) return;
        final WebView w = mvWeb;
        try {
            if (w != null) {
                w.evaluateJavascript("(function(){try{document.querySelectorAll('video,audio').forEach(function(v){try{v.pause();}catch(e){}});window.__pkMvOff&&window.__pkMvOff();window.__pkBwOff&&window.__pkBwOff();window.__mvPause&&window.__mvPause();}catch(e){}})()", null);
                w.postDelayed(() -> { if (mvWeb != w) { try { w.onPause(); } catch (Exception ignored) { } } }, 500);
            }
            ViewGroup content = findViewById(android.R.id.content);
            content.removeView(mvRoot);
        } catch (Exception ignored) { }
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && bwInput != null) imm.hideSoftInputFromWindow(bwInput.getWindowToken(), 0);
        } catch (Exception ignored) { }
        mvWeb = null;
        mvRoot = null;
        mvLoading = null;
        mvSendBtn = null;
        mvPickOn = false;
        bwInput = null;
        bwEngineBtn = null;
        bwPlaceholder = null;
        bwProgress = null;
        mvMode = 0;
    }

    private void closeMoreVideoPage() { hideMoreVideoPage(); }

    /** ऐप बंद होते समय ही पेज नष्ट होते हैं */
    private void destroyMoreVideoPages() {
        hideMoreVideoPage();
        for (MvHolder h : new MvHolder[]{mvHolderYt, mvHolderBw, mvHolderOwn}) {
            if (h != null && h.web != null) {
                try { h.web.stopLoading(); h.web.loadUrl("about:blank"); h.web.destroy(); } catch (Exception ignored) { }
            }
        }
        mvHolderYt = null;
        mvHolderBw = null;
        mvHolderOwn = null;
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
            boolean on = "\"on\"".equals(value);
            mvPickOn = on;
            if (mvSendBtn != null) mvSendBtn.setText(on ? "✕  बटन छुपाएँ" : "▶  Room में भेजें  🪙");
            if (on) Toast.makeText(this, "जिस वीडियो को रूम में चलाना है, उसके लाल \"▶ Room\" बटन को दबाइए", Toast.LENGTH_LONG).show();
            else if ("\"none\"".equals(value)) Toast.makeText(this, "पेज अभी लोड हो रहा है, थोड़ी देर बाद दबाइए", Toast.LENGTH_SHORT).show();
        });
    }

    private void resetPickLabel() {
        mvPickOn = false;
        if (mvWeb != null) mvWeb.evaluateJavascript("window.__pkMvOff&&window.__pkMvOff();window.__pkBwOff&&window.__pkBwOff()", null);
        if (mvSendBtn != null) mvSendBtn.setText(mvMode == 1 ? "🎬  वीडियो चुनो" : "▶  Room में भेजें  🪙");
    }

    /** वेबपेज का HTML पढ़कर उसके अंदर से असली video link निकालता है (og:video, <video>, <source>, .mp4/.m3u8, YouTube embed) */
    private String resolveVideoFromPage(String pageUrl) {
        HttpURLConnection c = null;
        try {
            URL u0 = new URL(pageUrl);
            c = (HttpURLConnection) u0.openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*");
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9,hi;q=0.8");
            c.getResponseCode();
            String ct = c.getContentType() == null ? "" : c.getContentType().toLowerCase();
            URL fin = c.getURL();
            if (ct.startsWith("video/") || ct.contains("mpegurl")) return fin.toString();
            if (!ct.contains("html")) return "";
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < 2000000) { bo.write(buf, 0, n); total += n; }
            return extractVideoUrl(bo.toString("UTF-8"), fin);
        } catch (Exception e) {
            return "";
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static boolean isGoodVideoUrl(String u) {
        if (u == null || !u.startsWith("http")) return false;
        String l = u.toLowerCase();
        if (l.matches(".*\\.(jpg|jpeg|png|gif|webp|js|css|json|svg|ico)(\\?.*)?$")) return false;
        return !(l.contains("doubleclick") || l.contains("googlesyndication") || l.contains("adserver"));
    }

    private String extractVideoUrl(String html, URL base) {
        String h = html.replace("\\/", "/").replace("&amp;", "&");
        String[] pats = {
                "<meta[^>]+(?:property|name)=[\"']og:video(?::secure_url|:url)?[\"'][^>]*content=[\"']([^\"']+)[\"']",
                "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"']og:video(?::secure_url|:url)?[\"']",
                "<meta[^>]+name=[\"']twitter:player:stream[\"'][^>]*content=[\"']([^\"']+)[\"']",
                "\"contentUrl\"\\s*:\\s*\"([^\"]+)\"",
                "<video[^>]*\\ssrc=[\"']([^\"']+)[\"']",
                "<source[^>]+src=[\"']([^\"']+)[\"']",
                "(https?://[^\"'\\s<>\\\\]+\\.(?:mp4|m3u8|webm|mov)(?:\\?[^\"'\\s<>\\\\]*)?)"
        };
        String firstAny = "";
        for (String p : pats) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(p, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(h);
            while (m.find()) {
                String u;
                try { u = new URL(base, m.group(1)).toString(); } catch (Exception e) { continue; }
                if (!isGoodVideoUrl(u)) continue;
                if (u.toLowerCase().matches(".*\\.(mp4|m3u8|webm|mov)(\\?.*)?$")) return u;   // असली फ़ाइल मिली
                if (firstAny.isEmpty()) firstAny = u;
            }
        }
        java.util.regex.Matcher y = java.util.regex.Pattern.compile("(?:youtube(?:-nocookie)?\\.com/(?:embed/|watch\\?v=)|youtu\\.be/)([A-Za-z0-9_-]{11})").matcher(h);
        if (y.find()) return "https://www.youtube.com/watch?v=" + y.group(1);
        return firstAny;
    }

    /** पेज का link असली video link में बदलकर JS को बताता है (रूम का "सीधा video link" खाना इसे इस्तेमाल करता है) */
    private void resolveForRoomLoader(final String pageUrl) {
        new Thread(() -> {
            final String r = resolveVideoFromPage(pageUrl);
            runOnUiThread(() -> {
                if (webView != null) webView.evaluateJavascript("window.__pkOnResolved&&window.__pkOnResolved("
                        + JSONObject.quote(pageUrl) + "," + JSONObject.quote(r == null ? "" : r) + ")", null);
            });
        }).start();
    }

    /** ब्राउज़िंग में किसी वेबपेज-लिंक पर "▶ Room": पेज में से असली video निकालकर रूम में भेजो */
    private void resolveAndSendToRoom(final String pageUrl) {
        Toast.makeText(this, "🔎 वीडियो ढूँढ रहा हूँ…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final String r = resolveVideoFromPage(pageUrl);
            runOnUiThread(() -> {
                if (r == null || r.isEmpty()) {
                    Toast.makeText(MainActivity.this, "❌ इस पेज से सीधा वीडियो नहीं मिला", Toast.LENGTH_LONG).show();
                    return;
                }
                String yt = cleanYoutubeUrl(r);
                if (yt != null) sendCleanToRoom(yt); else sendDirectToRoom(r, pageUrl);
            });
        }).start();
    }

    private static boolean isProtectedHost(String h) {
        h = h.toLowerCase();
        String[] block = {"google", "gstatic", "googleapis", "googleusercontent", "firebase", "firebaseio", "appspot", "youtube", "ytimg",
                "ggpht", "github.io", "githubusercontent", "cloudflare", "jsdelivr", "unity3d", "unity.com", "androidplatform.net"};
        for (String b : block) if (h.contains(b)) return true;
        return false;
    }

    private void registerMediaRef(String url, String ref) {
        try {
            if (url == null || ref == null || !url.matches("(?i)^https?://\\S+$") || !ref.matches("(?i)^https?://\\S+$")) return;
            String host = Uri.parse(url).getHost();
            String startHost = START_URL.isEmpty() ? "" : Uri.parse(START_URL).getHost();
            if (host == null || host.equals(startHost) || isProtectedHost(host)) return;
            mediaRefHosts.put(host, ref);
        } catch (Exception ignored) { }
    }

    /** video की माँग ऐप खुद करके भेजता है, सही Referer/Origin के साथ (और आगे-पीछे करने के लिए Range के साथ) */
    private WebResourceResponse proxyMedia(WebResourceRequest req, String referer) {
        HttpURLConnection c = null;
        try {
            String cur = req.getUrl().toString();
            Map<String, String> reqH = req.getRequestHeaders();
            for (int hop = 0; hop < 6; hop++) {
                c = (HttpURLConnection) new URL(cur).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(12000);
                c.setReadTimeout(25000);
                for (Map.Entry<String, String> h : reqH.entrySet()) {
                    String k = h.getKey();
                    if (k.equalsIgnoreCase("Range") || k.equalsIgnoreCase("User-Agent") || k.equalsIgnoreCase("Accept") || k.equalsIgnoreCase("Accept-Language"))
                        c.setRequestProperty(k, h.getValue());
                }
                c.setRequestProperty("Referer", referer);
                try {
                    Uri ru = Uri.parse(referer);
                    c.setRequestProperty("Origin", ru.getScheme() + "://" + ru.getHost());
                } catch (Exception ignored) { }
                int code = c.getResponseCode();
                if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                    cur = new URL(new URL(cur), c.getHeaderField("Location")).toString();
                    c.disconnect();
                    c = null;
                    continue;
                }
                String mime = c.getContentType();
                if (mime != null && mime.contains(";")) mime = mime.substring(0, mime.indexOf(';')).trim();
                String l = cur.toLowerCase();
                if (mime == null || mime.isEmpty() || mime.equals("application/octet-stream") || mime.equals("text/plain")) {
                    if (l.contains(".mp4")) mime = "video/mp4";
                    else if (l.contains(".webm")) mime = "video/webm";
                    else if (l.contains(".m3u8")) mime = "application/vnd.apple.mpegurl";
                    else if (l.contains(".ts")) mime = "video/mp2t";
                    else if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
                }
                Map<String, String> out = new HashMap<>();
                for (String name : new String[]{"Content-Range", "Accept-Ranges", "Content-Length"}) {
                    String v = c.getHeaderField(name);
                    if (v != null) out.put(name, v);
                }
                out.put("Access-Control-Allow-Origin", "*");
                out.put("Access-Control-Expose-Headers", "Content-Length,Content-Range,Accept-Ranges");
                InputStream raw = code >= 400 ? c.getErrorStream() : c.getInputStream();
                if (raw == null) raw = new ByteArrayInputStream(new byte[0]);
                final HttpURLConnection conn = c;
                InputStream in = new FilterInputStream(raw) {
                    @Override public void close() throws java.io.IOException {
                        try { super.close(); } finally { conn.disconnect(); }
                    }
                };
                String reason = c.getResponseMessage();
                return new WebResourceResponse(mime, null, code, (reason == null || reason.isEmpty()) ? "OK" : reason, out, in);
            }
        } catch (Exception e) {
            if (c != null) c.disconnect();
        }
        return null;
    }

    private void sendDirectToRoom(String url) { sendDirectToRoom(url, ""); }

    private void sendDirectToRoom(String url, String ref) {
        closeMoreVideoPage();
        final String js = "window.postMessage({type:'PARTY_ROOM_DIRECT_VIDEO_SELECTED',url:" + JSONObject.quote(url)
                + ",ref:" + JSONObject.quote(ref == null ? "" : ref) + "},'*');";
        webView.evaluateJavascript(js, null);
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
        destroyMoreVideoPages();
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
