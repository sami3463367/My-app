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
 * Inline 16:9 player that streams a 24/7 live broadcast (curated weather/news channel or a
 * "live" YouTube search) inside the app. Every state is explicit: a branded loading splash,
 * a fullscreen mode, and a graceful, labeled fallback card when the stream cannot be shown.
 */
public final class LiveWeatherPanel extends FrameLayout {
    private static final int COLOR_TEXT = Color.rgb(235, 246, 255);
    private static final int COLOR_MUTED = Color.rgb(140, 170, 196);
    private static final int COLOR_CYAN = Color.rgb(75, 212, 255);
    private static final int COLOR_RED = Color.rgb(255, 92, 92);
    private static final long TAP_TOLERANCE_MS = 300L;

    private final Activity activity;
    private final FrameLayout content;
    private final View loadingView;
    private final View errorView;
    private TextView channelLabel;
    private TextView statusHint;
    private WebView webView;
    private Dialog fullscreenDialog;
    private Runnable onClose;
    private boolean errorShowing;
    private long lastTapMillis;
    private String currentUrl = "";

    public LiveWeatherPanel(Context context) {
        super(context);
        this.activity = (Activity) context;
        setBackgroundColor(Color.BLACK);
        setClipToOutline(true);
        setBackground(roundRect(Color.BLACK, dp(16), dp(1), Color.argb(120, 76, 205, 249)));
        setClickable(true);
        setOnClickListener(view -> onPlayerTap());

        content = new FrameLayout(activity);
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        webView = buildWebView();
        content.addView(webView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        loadingView = buildLoadingView(activity);
        addView(loadingView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        errorView = buildErrorView(activity);
        errorView.setVisibility(View.GONE);
        addView(errorView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        addChrome();
    }

    private void addChrome() {
        LinearLayout chrome = new LinearLayout(activity);
        chrome.setOrientation(LinearLayout.HORIZONTAL);
        chrome.setGravity(Gravity.CENTER_VERTICAL);
        chrome.setPadding(dp(12), dp(8), dp(8), dp(8));
        chrome.setBackgroundColor(Color.argb(140, 3, 8, 18));

        TextView live = new TextView(activity);
        live.setText("●  LIVE");
        live.setTextSize(10);
        live.setTextColor(COLOR_RED);
        live.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        live.setLetterSpacing(0.12f);
        chrome.addView(live, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        channelLabel = new TextView(activity);
        channelLabel.setTextColor(COLOR_TEXT);
        channelLabel.setTextSize(9f);
        channelLabel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        channelLabel.setLetterSpacing(0.06f);
        channelLabel.setSingleLine(true);
        channelLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chrome.addView(channelLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 2f));

        chrome.addView(chromeButton("⛶", "Make the broadcast fullscreen",
                view -> showFullscreen()));
        chrome.addView(chromeButton("×", "Close the broadcast",
                view -> {
                    if (onClose != null) onClose.run();
                }));

        FrameLayout.LayoutParams chromeParams = new FrameLayout.LayoutParams(
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
        statusHint.setVisibility(View.GONE);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
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
        view.setPadding(dp(18), dp(14), dp(18), dp(14));

        ProgressBar spinner = new ProgressBar(activity);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        view.addView(spinner, spinnerParams);

        TextView label = new TextView(activity);
        label.setText("TUNING INTO LIVE BROADCAST…");
        label.setTextSize(10.5f);
        label.setTextColor(COLOR_CYAN);
        label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        label.setLetterSpacing(0.12f);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(10);
        view.addView(label, labelParams);

        TextView hint = new TextView(activity);
        hint.setText("24/7 WEATHER CHANNEL · NO ADS, NO SIGN-INS");
        hint.setTextSize(8f);
        hint.setTextColor(Color.argb(170, 140, 170, 196));
        hint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        hint.setLetterSpacing(0.10f);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(5);
        view.addView(hint, hintParams);
        return view;
    }

    private View buildErrorView(Activity activity) {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(Color.BLACK);
        view.setPadding(dp(18), dp(12), dp(18), dp(12));

        TextView glyph = new TextView(activity);
        glyph.setText("⚠");
        glyph.setTextSize(18);
        glyph.setTextColor(Color.rgb(255, 173, 75));
        glyph.setGravity(Gravity.CENTER);
        view.addView(glyph, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(activity);
        title.setText("THIS STREAM IS UNAVAILABLE RIGHT NOW");
        title.setTextSize(10.5f);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setLetterSpacing(0.08f);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(8);
        view.addView(title, titleParams);

        TextView detail = new TextView(activity);
        detail.setText("Some 24/7 channels pause or block in-app playback. You can retry, or open the broadcast in YouTube.");
        detail.setTextSize(8.5f);
        detail.setTextColor(COLOR_MUTED);
        detail.setLineSpacing(dp(2), 1f);
        detail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(5);
        view.addView(detail, detailParams);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        TextView retry = chromeButtonSmall("RETRY", "Retry the broadcast",
                v -> {
                    errorShowing = false;
                    errorView.setVisibility(View.GONE);
                    if (webView != null) webView.reload();
                    loadingView.setVisibility(View.VISIBLE);
                });
        TextView open = chromeButtonSmall("OPEN IN YOUTUBE", "Open the broadcast in YouTube",
                v -> openInBrowser(currentUrl));
        actions.addView(retry, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        openParams.leftMargin = dp(8);
        actions.addView(open, openParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL);
        actionsParams.topMargin = dp(9);
        view.addView(actions, actionsParams);
        return view;
    }

    private TextView chromeButtonSmall(String label, String description, OnClickListener listener) {
        TextView button = new TextView(activity);
        button.setText(label);
        button.setTextSize(8.5f);
        button.setTextColor(COLOR_CYAN);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setLetterSpacing(0.08f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setContentDescription(description);
        button.setOnClickListener(listener);
        button.setBackground(roundRect(Color.argb(175, 17, 49, 76), dp(14),
                dp(1), Color.argb(105, 84, 210, 255)));
        return button;
    }

    private WebView buildWebView() {
        return buildWebView(false);
    }

    private WebView buildWebView(final boolean standalone) {
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
                if (!standalone && !errorShowing) loadingView.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request) {
                if (request == null || !request.isForMainFrame()) return;
                Uri uri = request.getUrl();
                if (uri == null || !uri.isHierarchical()) return;
                String scheme = uri.getScheme();
                if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) return;
                if (standalone) {
                    closeFullscreen();
                } else {
                    showError();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) return true;
                if (uri.getHost() == null) return true;
                String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
                if (host.equals("youtube.com") || host.equals("youtu.be")) return false;
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, uri)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) { }
                return true;
            }
        });
        return view;
    }

    private void showError() {
        if (errorShowing) return;
        errorShowing = true;
        loadingView.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
    }

    /** Starts (or switches to) the broadcast for the given source. */
    public void load(String url, String channel) {
        currentUrl = url;
        channelLabel.setText(channel);
        errorShowing = false;
        errorView.setVisibility(View.GONE);
        loadingView.setVisibility(View.VISIBLE);
        statusHint.setVisibility(View.GONE);
        if (webView != null) webView.loadUrl(url);
        loadingView.postDelayed(() -> {
            if (loadingView.getVisibility() == View.VISIBLE) {
                statusHint.setVisibility(View.VISIBLE);
            }
        }, 12_000L);
    }

    public void setOnClose(Runnable runnable) {
        this.onClose = runnable;
    }

    public boolean isFullscreenOpen() {
        return fullscreenDialog != null && fullscreenDialog.isShowing();
    }

    public void closeFullscreen() {
        if (fullscreenDialog != null && fullscreenDialog.isShowing()) fullscreenDialog.dismiss();
    }

    private void showFullscreen() {
        if (currentUrl.isEmpty()) return;
        if (fullscreenDialog != null && fullscreenDialog.isShowing()) {
            fullscreenDialog.dismiss();
            return;
        }
        fullscreenDialog = new Dialog(activity);
        fullscreenDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        fullscreenDialog.setContentView(buildFullscreenContent());
        Window window = fullscreenDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        fullscreenDialog.setOnDismissListener(dialog -> fullscreenDialog = null);
        fullscreenDialog.show();
    }

    private View buildFullscreenContent() {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);

        WebView player = buildWebView(true);
        root.addView(player, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        player.loadUrl(currentUrl);


        TextView close = new TextView(activity);
        close.setText("×");
        close.setTextSize(26);
        close.setTextColor(COLOR_TEXT);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Exit fullscreen");
        close.setBackground(roundRect(Color.argb(175, 10, 24, 47), dp(22),
                dp(1), Color.argb(110, 90, 207, 255)));
        close.setOnClickListener(view -> {
            if (fullscreenDialog != null) fullscreenDialog.dismiss();
        });
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(46), dp(46),
                Gravity.TOP | Gravity.END);
        closeParams.setMargins(0, dp(18), dp(16), 0);
        root.addView(close, closeParams);

        TextView label = new TextView(activity);
        label.setText("LIVE WEATHER BROADCAST · FULLSCREEN");
        label.setTextSize(8.5f);
        label.setTextColor(Color.argb(200, 140, 170, 196));
        label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        label.setLetterSpacing(0.12f);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        labelParams.bottomMargin = dp(16);
        root.addView(label, labelParams);
        return root;
    }

    private void onPlayerTap() {
        long now = System.currentTimeMillis();
        if (now - lastTapMillis < TAP_TOLERANCE_MS) {
            showFullscreen();
            lastTapMillis = 0L;
        } else {
            lastTapMillis = now;
        }
    }

    private void openInBrowser(String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    /** Releases the WebView when the activity is finished. */
    public void destroy() {
        if (fullscreenDialog != null && fullscreenDialog.isShowing()) fullscreenDialog.dismiss();
        if (webView != null) {
            content.removeView(webView);
            webView.destroy();
            webView = null;
        }
    }

    private GradientDrawable roundRect(int color, int radius, int strokeWidth, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strowCount, strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
