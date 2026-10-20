package com.makkahprintingpress.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceResponse;
import java.io.IOException;
import java.io.InputStream;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.ValueCallback;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String HOME = "https://www.makkahprintingpress.com/wp-admin/";
    private static final int GOLD = Color.rgb(204, 159, 26);
    private static final int FILE_PICKER = 901;
    private WebView browser;
    private FrameLayout root;
    private FrameLayout splash;
    private FrameLayout errorPanel;
    private ProgressBar progress;
    private ValueCallback<Uri[]> pendingFiles;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);
        createBrowser();
        createSplash();
        if (state == null) browser.loadUrl(HOME);
        else if (browser.restoreState(state) == null) browser.loadUrl(HOME);
        handler.postDelayed(this::hideSplash, 1600);
    }
    private void createBrowser() {
        browser = new WebView(this);
        browser.setBackgroundColor(Color.WHITE);
        browser.setOverScrollMode(View.OVER_SCROLL_NEVER);
        browser.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        root.addView(browser, new FrameLayout.LayoutParams(-1, -1));
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setTextZoom(100);
        String mobileUserAgent = settings.getUserAgentString();
        settings.setUserAgentString(mobileUserAgent.replace("; wv", "").replace("Version/4.0 ", ""));
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);
        browser.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (("www.makkahprintingpress.com".equalsIgnoreCase(uri.getHost()) || "makkahprintingpress.com".equalsIgnoreCase(uri.getHost())) && "/__mppapp__/admin-logo.png".equals(uri.getPath())) {
                    InputStream logo = getResources().openRawResource(R.drawable.brand_logo);
                    return new WebResourceResponse("image/png", null, logo);
                }
                if ("www.makkahprintingpress.com".equalsIgnoreCase(uri.getHost()) && "/__mppapp__/hero-banner.png".equals(uri.getPath())) {
                    try { return new WebResourceResponse("image/png", null, getAssets().open("hero-banner.png")); }
                    catch (IOException e) { return null; }
                }
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                return handleUrl(request.getUrl().toString());
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showError();
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (progress != null) progress.setVisibility(View.GONE);
                hideError();
                if (isWordPressLoginPage(url)) {
                    showAdminLoginLogo();
                    view.postDelayed(() -> showAdminLoginLogo(), 350);
                    view.postDelayed(() -> showAdminLoginLogo(), 1200);
                }
                view.postDelayed(() -> refreshVisibleImages(), 350);
                view.postDelayed(() -> refreshVisibleImages(), 1600);
                view.postDelayed(() -> refreshVisibleImages(), 3200);
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                if (progress != null) {
                    progress.setProgress(value);
                    progress.setVisibility(value == 100 ? View.GONE : View.VISIBLE);
                }
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFiles != null) pendingFiles.onReceiveValue(null);
                pendingFiles = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_PICKER);
                    return true;
                } catch (Exception e) {
                    pendingFiles = null;
                    callback.onReceiveValue(null);
                    return false;
                }
            }
        });
        browser.setDownloadListener((url, agent, disposition, type, length) -> openOutside(url));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(GOLD));
        root.addView(progress, new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP));
    }
    // The WordPress homepage emits an HTTP URL for the hero image on an HTTPS page.
    // Serve the original banner locally so mixed-content blocking cannot break it.
    private boolean isWordPressLoginPage(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        return host != null &&
            (host.equalsIgnoreCase("makkahprintingpress.com") || host.equalsIgnoreCase("www.makkahprintingpress.com")) &&
            "/wp-login.php".equalsIgnoreCase(uri.getPath());
    }
    private void showAdminLoginLogo() {
        if (browser == null || !isWordPressLoginPage(browser.getUrl())) return;
        String js = "(function(){try{" +
            "var login=document.getElementById('login');if(!login)return;" +
            "var head=login.querySelector('h1');" +
            "if(!head){head=document.createElement('h1');login.insertBefore(head,login.firstChild);}" +
            "var anchor=head.querySelector('a');" +
            "if(!anchor){anchor=document.createElement('a');anchor.href='https://www.makkahprintingpress.com/';head.appendChild(anchor);}" +
            "head.style.setProperty('display','block','important');" +
            "head.style.setProperty('visibility','visible','important');" +
            "head.style.setProperty('opacity','1','important');" +
            "head.style.setProperty('height','auto','important');" +
            "head.style.setProperty('margin','0 auto 22px','important');" +
            "anchor.style.setProperty('display','block','important');" +
            "anchor.style.setProperty('visibility','visible','important');" +
            "anchor.style.setProperty('opacity','1','important');" +
            "anchor.style.setProperty('width','220px','important');" +
            "anchor.style.setProperty('max-width','85%','important');" +
            "anchor.style.setProperty('height','120px','important');" +
            "anchor.style.setProperty('margin','0 auto','important');" +
            "anchor.style.setProperty('background-image','url(https://www.makkahprintingpress.com/__mppapp__/admin-logo.png)','important');" +
            "anchor.style.setProperty('background-position','center','important');" +
            "anchor.style.setProperty('background-repeat','no-repeat','important');" +
            "anchor.style.setProperty('background-size','contain','important');" +
            "anchor.style.setProperty('font-size','0','important');" +
            "}catch(e){}})();";
        browser.evaluateJavascript(js, null);
    }
    private void refreshVisibleImages() {
        if (browser == null) return;
        String js = "(function(){try{" +
            "var isHome=(location.hostname==='www.makkahprintingpress.com'||location.hostname==='makkahprintingpress.com')&&location.pathname==='/';" +
            "document.querySelectorAll('img').forEach(function(img){" +
            "if(isHome){" +
            "var info=((img.getAttribute('alt')||'')+' '+(img.getAttribute('src')||'')+' '+(img.getAttribute('data-src')||'')).toLowerCase();" +
            "if(info.indexOf('chatgpt-image-sep-9-2026')>=0||info.indexOf('chatgpt image sep 9')>=0){" +
            "if(img.parentElement&&img.parentElement.tagName==='PICTURE'){img.parentElement.querySelectorAll('source').forEach(function(s){s.removeAttribute('srcset');s.removeAttribute('data-srcset');});}" +
            "img.removeAttribute('srcset');img.removeAttribute('sizes');img.removeAttribute('data-srcset');" +
            "img.loading='eager';img.decoding='async';" +
            "if(img.getAttribute('src')!=='https://www.makkahprintingpress.com/__mppapp__/hero-banner.png')img.setAttribute('src','https://www.makkahprintingpress.com/__mppapp__/hero-banner.png');" +
            "img.style.maxWidth='100%';img.style.width='100%';img.style.height='auto';img.style.display='block';" +
            "return;" +
            "}}" +
            "if(!img.getAttribute('src')&&img.dataset.src){img.src=img.dataset.src;}" +
            "if(!img.getAttribute('srcset')&&img.dataset.srcset){img.srcset=img.dataset.srcset;}" +
            "});" +
            "window.dispatchEvent(new Event('resize'));" +
            "}catch(e){}})();";
        browser.evaluateJavascript(js, null);
    }
    private boolean handleUrl(String value) {
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (scheme.equals("https")) return false;
        if (scheme.equals("http")) { openOutside(value); return true; }
        if (scheme.equals("about") || scheme.equals("blob") || scheme.equals("data")) return false;
        try {
            if (scheme.equals("intent")) {
                Intent intent = Intent.parseUri(value, Intent.URI_INTENT_SCHEME);
                if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
                else {
                    String fallback = intent.getStringExtra("browser_fallback_url");
                    if (fallback != null && fallback.startsWith("https://")) browser.loadUrl(fallback);
                }
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
        } catch (Exception e) {
            Toast.makeText(this, "No application available to open this link", Toast.LENGTH_SHORT).show();
        }
        return true;
    }
    private void openOutside(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show(); }
    }
    private void createSplash() {
        splash = new FrameLayout(this);
        splash.setBackgroundColor(Color.WHITE);
        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setGravity(Gravity.CENTER);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.brand_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        stack.addView(logo, new LinearLayout.LayoutParams(dp(205), dp(205)));
        TextView welcome = label("Welcome to Admin Panel", 22, Color.DKGRAY, false);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(-1, -2);
        a.topMargin = dp(18);
        stack.addView(welcome, a);
        TextView title = label("Makkah Printing Press", 25, Color.BLACK, true);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(-1, -2);
        b.topMargin = dp(7);
        stack.addView(title, b);
        View line = new View(this);
        line.setBackground(background(GOLD, 4));
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(dp(96), dp(4));
        c.gravity = Gravity.CENTER;
        c.topMargin = dp(22);
        stack.addView(line, c);
        FrameLayout.LayoutParams center = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        center.leftMargin = dp(20);
        center.rightMargin = dp(20);
        splash.addView(stack, center);
        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));
    }
    private void hideSplash() {
        if (splash == null) return;
        FrameLayout s = splash;
        splash = null;
        s.animate().alpha(0f).setDuration(200).withEndAction(() -> root.removeView(s)).start();
    }
    private void showError() {
        if (errorPanel != null) return;
        errorPanel = new FrameLayout(this);
        errorPanel.setBackgroundColor(Color.WHITE);
        LinearLayout stack = new LinearLayout(this);
        stack.setGravity(Gravity.CENTER);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.addView(label("Connection unavailable", 23, Color.BLACK, true));
        TextView detail = label("Please check your internet connection and try again.", 15, Color.DKGRAY, false);
        LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(-1, -2);
        d.topMargin = dp(12);
        stack.addView(detail, d);
        Button retry = new Button(this);
        retry.setText("Retry");
        retry.setAllCaps(false);
        retry.setTextColor(Color.BLACK);
        retry.setBackground(background(GOLD, 12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(170), dp(52));
        p.gravity = Gravity.CENTER;
        p.topMargin = dp(24);
        stack.addView(retry, p);
        retry.setOnClickListener(v -> { hideError(); browser.reload(); });
        FrameLayout.LayoutParams center = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        center.leftMargin = dp(24);
        center.rightMargin = dp(24);
        errorPanel.addView(stack, center);
        root.addView(errorPanel, new FrameLayout.LayoutParams(-1, -1));
        errorPanel.bringToFront();
    }
    private void hideError() {
        if (errorPanel != null) {
            root.removeView(errorPanel);
            errorPanel = null;
        }
    }
    private boolean atHome() {
        String current = browser.getUrl();
        if (current == null) return true;
        Uri u = Uri.parse(current);
        String host = u.getHost();
        String path = u.getPath();
        return host != null &&
            (host.equalsIgnoreCase("makkahprintingpress.com") || host.equalsIgnoreCase("www.makkahprintingpress.com")) &&
            (path == null || path.equals("/")) && u.getQuery() == null;
    }
    @Override public void onBackPressed() {
        if (errorPanel != null) { hideError(); if (browser.canGoBack()) browser.goBack(); else showExit(); return; }
        if (browser.canGoBack() && !atHome()) browser.goBack();
        else showExit();
    }
    private void showExit() {
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Exit Makkah Printing Press?")
            .setMessage("Are you sure you want to close the app?")
            .setNegativeButton("No", (d, which) -> d.dismiss())
            .setPositiveButton("Exit", (d, which) -> finish())
            .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(GOLD);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.BLACK);
        });
        dialog.show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == FILE_PICKER && pendingFiles != null) {
            pendingFiles.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            pendingFiles = null;
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (browser != null) browser.saveState(state);
    }
    @Override protected void onDestroy() {
        if (browser != null) { browser.stopLoading(); browser.destroy(); browser = null; }
        super.onDestroy();
    }
}
