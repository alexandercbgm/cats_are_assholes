package com.alexandercbgm.ugomonikota;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Fullscreen landscape WebView that runs the game from the bundled assets. */
public class MainActivity extends Activity {
    private static final String EXIT_URL = "app://exit";
    // View.SYSTEM_UI_FLAG_* values (API 16-19); the compile jar predates some of the constants
    private static final int IMMERSIVE = 0x1000 | 0x0004 | 0x0002 | 0x0200 | 0x0400 | 0x0100;

    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(0xff120e26);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        try {
            WebSettings.class.getMethod("setMediaPlaybackRequiresUserGesture", boolean.class).invoke(s, false);
        } catch (Exception ignored) {
        }
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith(EXIT_URL)) {
                    finish();
                    return true;
                }
                return !url.startsWith("file:");
            }
        });
        setContentView(web);
        if (state == null || web.restoreState(state) == null) {
            web.loadUrl("file:///android_asset/index.html");
        }
        hideSystemBars();
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(IMMERSIVE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    /** Back pauses a running game; on a menu it leaves the app. */
    @Override
    public void onBackPressed() {
        web.loadUrl("javascript:(function(){if(game.running&&!game.paused){setPause(true);}"
                + "else if(game.paused){setPause(false);}else{location.href='" + EXIT_URL + "';}})()");
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        hideSystemBars();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
