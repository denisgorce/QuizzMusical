package com.quizzmusical.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Recherche et écoute d'une chanson sur YouTube Music dans l'application. */
public class YouTubeActivity extends Activity {
    public static final String EXTRA_QUERY = "q";
    public static final String EXTRA_TITLE = "t";
    private static final String YTM_PACKAGE = "com.google.android.apps.youtube.music";

    private WebView web;
    private String searchUrl;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui ui = new Ui(this);
        getWindow().setStatusBarColor(Ui.BG);
        String q = getIntent().getStringExtra(EXTRA_QUERY);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (q == null) q = "";
        searchUrl = "https://music.youtube.com/search?q=" + Uri.encode(q);

        LinearLayout rootL = ui.vbox();
        rootL.setBackgroundColor(Ui.BG);

        LinearLayout bar = ui.hbox();
        bar.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        TextView back = ui.button("‹", Ui.CARD2, Ui.TXT);
        back.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(4));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView t = ui.text(title == null ? q : title, 15, Ui.TXT, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(ui.dp(10), 0, ui.dp(10), 0);
        bar.addView(t, ui.weight(1, 0));
        TextView app = ui.button("App ↗", Ui.PINK, Ui.TXT);
        app.setTextSize(14);
        app.setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6));
        app.setOnClickListener(v -> openExternal());
        bar.addView(app);
        rootL.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout frame = new FrameLayout(this);
        web = new WebView(this);
        final ProgressBar spinner = new ProgressBar(this);
        frame.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(spinner, new FrameLayout.LayoutParams(ui.dp(48), ui.dp(48), Gravity.CENTER));
        rootL.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(rootL);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int p) {
                spinner.setVisibility(p >= 90 ? View.GONE : View.VISIBLE);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return handleUrl(req.getUrl().toString());
            }
        });
        web.loadUrl(searchUrl);
    }

    private boolean handleUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) return false;
        try {
            Intent i = url.startsWith("intent:") ? Intent.parseUri(url, Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            i.setComponent(null);
            startActivity(i);
        } catch (Exception e) {
            // schéma non géré : on reste sur la page
        }
        return true;
    }

    private void openExternal() {
        String url = web != null && web.getUrl() != null ? web.getUrl() : searchUrl;
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.setPackage(YTM_PACKAGE);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (ActivityNotFoundException ignored) { }
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
