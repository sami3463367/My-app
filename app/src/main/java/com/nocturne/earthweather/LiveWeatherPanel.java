package com.nocturne.earthweather;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 16:9 live-broadcast panel that plays a 24/7 weather or news channel in a WebView. It degrades
 * gracefully: a tuning splash while connecting, a labelled fallback card when the stream cannot be
 * reached, and a fullscreen presentation mode.
 */
public final class LiveWeatherPanel extends FrameLayout {
    private static final int COLOR_VOID = Color.rgb(2, 4, 11);
    private static final int COLOR_TEXT = Color.rgb(235, 246, 255);
    private static final int COLOR_MUTED = Color.rgb(140, 170, 196);
    private static final int COLOR_LIVE_RED = Color.rgb(255, 84, 84);

    private final Activity activity;
    private final FrameLayout content;
    private final TextView channelLabel;
    private final TextView statusHint;
    private final View loadingView;
    private final View errorView;
    private final TextView errorChannel;
    private final WebView webView;
    private Dialog fullscreenDialog;
    private String currentUrl;
    private String currentLabel;
    private Runnable onClose;
    private boolean errorShowing;

    public LiveWeatherPanel(Activity activity) {
        super(activity);
        this.activity = activity;
        setBackgroundColor(COLOR_VOID);

        content = new FrameLayout(activity);
        content.setBackgroundColor(Color.BLACK);
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        webView = buildWebView();
        content.addView(webView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        loadingView = buildLoadingView(activity);
        loadingView.setVisibility(View.GONE);
        addView(loadingView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        errorView = buildErrorView(activity);
        errorView.setVisibility(View.GONE);
        addView(errorView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        addChrome(activity);
    }

    private void addChrome(Activity activity) {
        LinearLayout chrome = new LinearLayout(activity);
        chrome.setOrientation(LinearLayout.HORIZONTAL);
        chrome.setGravity(Gravity.CENTER_VERTICAL);
        chrome.setPadding(dp(10), dp(8), dp(8), dp(8));

        TextView badge = new TextView(activity);
        badge.setText("● LIVE");
        badge.setTextSize(9.5f);
        badge.setTextColor(Color.WHITE);
        badge.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        badge.setLetterSpacing(0.12f);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.argb(232, 178, 34, 34));
        badgeBg.setCornerRadius(dp(12));
        badge.setBackground(badgeBg);
        chrome.addView(badge);

        channelLabel = new TextView(activity);
        channelLabel.setTextColor(COLOR_TEXT);
        channelLabel.setTextSize(9.5f);
        channelLabel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        channelLabel.setLetterSpacing(0.08f);
        channelLabel.setSingleLine(true);
        channelLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(10);
        chrome.addView(channelLabel, labelParams);

        chrome.addView(chromeButton("⛶", "Make the broadcast fullscreen",
                view -> showFullscreen()));
        chrome.addView(chromeButton("×", "Close the broadcast",
                view -> {
                    if (onClose != null) onClose.run();
                }));

        FrameLayout.LayoutParams chromeParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        addView(chrome, chromeParams);

        statusHint = new TextView(activity);
        statusHint.setText("24/7 BROADCAST · STREAMED FROM YOUTUBE");
        statusHint.setTextSize(8f);
        statusHint.setTextColor(Color.argb(150, 140, 170, 196));
        statusHint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        statusHint.setLetterSpacing(0.10f);
        statusHint.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams hintParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        hintParams.bottomMargin = dp(2);
        addView(statusHint, hintParams);
    }

    private TextView chromeButton(String glyph, String description, OnClickListener listener) {
        TextView button = new TextView(activity);
        button.setText(glyph);
        button.setTextSize(13f);
        button.setTextColor(COLOR_TEXT);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setOnClickListener(listener);
        button.setBackground(new GradientDrawable());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(34), dp(30));
        params.leftMargin = dp(4);
        button.setLayoutParams(params);
        return button;
    }

    private View buildLoadingView(Activity activity) {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(Color.BLACK);
        ProgressBar spinner = new ProgressBar(activity);
        view.addView(spinner, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView label = new TextView(activity);
        label.setText("TUNING INTO LIVE BROADCAST…");
        label.setTextSize(10f);
        label.setTextColor(COLOR_MUTED);
        label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        label.setLetterSpacing(0.10f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(12);
        view.addView(label, labelParams);
        return view;
    }

    private View buildErrorView(Activity activity) {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(18), dp(14), dp(18), dp(14));
        view.setBackgroundColor(Color.BLACK);

        TextView icon = new TextView(activity);
        icon.setText("📡");
        icon.setTextSize(26f);
        view.addView(icon);

        TextView title = new TextView(activity);
        title.setText("BROADCAST NOT REACHABLE RIGHT NOW");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(11.5f);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setLetterSpacing(0.06f);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(10);
        view.addView(title, titleParams);

        errorChannel = new TextView(activity);
        errorChannel.setTextColor(COLOR_MUTED);
        errorChannel.setTextSize(9.5f);
        errorChannel.setGravity(Gravity.CENTER);
        errorChannel.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams channelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        channelParams.topMargin = dp(6);
        view.addView(errorChannel, channelParams);

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.addView(fallbackButton("RETRY", this::retry));
        buttons.addView(fallbackButton("OPEN IN YOUTUBE", this::openInYouTube));
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dp(13);
        view.addView(buttons, buttonsParams);
        return view;
    }

    private TextView fallbackButton(String label, OnClickListener listener) {
        TextView button = new TextView(activity);
        button.setText(label);
        button.setTextSize(9.5f);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setLetterSpacing(0.08f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(13), dp(9), dp(13), dp(9));
        button.setContentDescription(label);
        button.setOnClickListener(listener);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(225, 16, 44, 70));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.argb(120, 84, 210, 255));
        button.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (label.equals("OPEN IN YOUTUBE")) params.leftMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private WebView buildWebView() {
        WebView view = new WebView(activity);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setTextZoom(100);
        view.setBackgroundColor(Color.BLACK);
        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (!errorShowing) loadingView.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request) {
                if (request == null || !request.isForMainFrame()) return;
                Uri uri = request.getUrl();
                if (uri == null || !uri.isHierarchical()) return;
                String scheme = uri.getScheme();
                if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) return;
                showError();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri == null ? null : uri.getHost();
                if (host != null && (host.equals("youtube.com")
                        || host.endsWith(".youtube.com") || host.equals("youtu.be"))) {
                    return false;
                }
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                    // No browser available; keep the current page.
                }
                return true;
            }
        });
        return view;
    }

    /** Starts (or restarts) a broadcast. @param label is the human channel name shown in chrome. */
    public void load(String url, String label) {
        currentUrl = url;
        currentLabel = label;
        channelLabel.setText(label);
        errorShowing = false;
        errorView.setVisibility(View.GONE);
        loadingView.setVisibility(View.VISIBLE);
        if (webView.getVisibility() != View.VISIBLE) webView.setVisibility(View.VISIBLE);
        try {
            webView.loadUrl(url);
        } catch (Exception error) {
            showError();
        }
    }

    private void retry() {
        if (currentUrl != null) load(currentUrl, currentLabel);
    }

    private void openInYouTube() {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)));
        } catch (Exception ignored) {
        }
    }

    private void showError() {
        if (errorShowing) return;
        errorShowing = true;
        loadingView.setVisibility(View.GONE);
        errorChannel.setText(currentLabel == null ? "LIVE BROADCAST" : currentLabel
                + "\nThe channel may not be airing right now, or this device could not reach it.");
        errorView.setVisibility(View.VISIBLE);
    }

    public void showFullscreen() {
        if (currentUrl == null || fullscreenDialog != null) return;
        showFullscreenDialog(currentUrl);
    }

    private void showFullscreenDialog(String url) {
        fullscreenDialog = new Dialog(activity);
        fullscreenDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        fullscreenDialog.setCanceledOnTouchOutside(false);

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);

        WebView player = buildWebView(true);
        root.addView(player, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView close = new TextView(activity);
        close.setText("×");
        close.setTextSize(22f);
        close.setTextColor(Color.WHITE);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Exit fullscreen");
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.argb(150, 4, 10, 22));
        closeBg.setCornerRadius(dp(18));
        close.setBackground(closeBg);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(46), dp(46),
                Gravity.TOP | Gravity.START);
        closeParams.leftMargin = dp(14);
        closeParams.topMargin = dp(26);
        root.addView(close, closeParams);

        fullscreenDialog.setContentView(root);
        Window window = fullscreenDialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        fullscreenDialog.setOnDismissListener(dialog -> player.destroy());
        close.setOnClickListener(v -> fullscreenDialog.dismiss());
        fullscreenDialog.show();
        player.loadUrl(url);
    }

    public boolean isFullscreenOpen() {
        return fullscreenDialog != null && fullscreenDialog.isShowing();
    }

    public void closeFullscreen() {
        if (isFullscreenOpen()) fullscreenDialog.dismiss();
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    /** Releases the embedded browser. Call from the owning activity's onDestroy. */
    public void destroy() {
        closeFullscreen();
        try {
            webView.loadUrl("about:blank");
            content.removeView(webView);
            webView.destroy();
        } catch (Exception ignored) {
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
