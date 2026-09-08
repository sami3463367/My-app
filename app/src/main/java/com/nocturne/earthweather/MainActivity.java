package com.nocturne.earthweather;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Main experience and lightweight HUD layered above the hardware-rendered globe. */
public final class MainActivity extends Activity {
    private static final int LOCATION_PERMISSION_REQUEST = 410;
    private static final int COLOR_VOID = Color.rgb(2, 4, 11);
    private static final int COLOR_PANEL = Color.rgb(8, 17, 35);
    private static final int COLOR_PANEL_STRONG = Color.rgb(11, 24, 47);
    private static final int COLOR_TEXT = Color.rgb(235, 246, 255);
    private static final int COLOR_MUTED = Color.rgb(140, 170, 196);
    private static final int COLOR_CYAN = Color.rgb(75, 212, 255);
    private static final int COLOR_AMBER = Color.rgb(255, 173, 75);
    private static final DateTimeFormatter CLOCK_FORMAT =
            DateTimeFormatter.ofPattern("EEE · HH:mm:ss · z", Locale.getDefault());
    private static final DateTimeFormatter UPDATED_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault());

    private final Handler handler = new Handler(Looper.getMainLooper());
    private GlobeSurfaceView globe;
    private View headerView;
    private View menuView;
    private View weatherCardView;
    private View orbitLegendView;
    private TextView cityName;
    private TextView cityCountry;
    private TextView localTime;
    private TextView weatherLine;
    private TextView metricsLine;
    private TextView weatherStatus;
    private TextView unitsButton;
    private ScrollView cardScroll;
    private FrameLayout cityPhotoContainer;
    private ImageView cityPhoto;
    private TextView cityPhotoFallback;
    private TextView windValue;
    private TextView humidityValue;
    private TextView rainValue;
    private TextView sunValue;
    private TextView uvValue;
    private TextView pressureValue;
    private LiveWeatherPanel livePanel;
    private TextView liveResumeChip;
    private boolean liveBroadcastEnabled = true;
    private View drawerScrim;
    private LinearLayout drawer;
    private TextView drawerUnitsAction;
    private boolean drawerOpen;
    private boolean fahrenheit;
    private City activeCity;
    private WeatherSnapshot activeWeather;
    private LocationManager locationManager;
    private LocationListener oneShotLocationListener;

    private final Runnable clockTicker = new Runnable() {
        @Override public void run() {
            updateClock();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        setContentView(buildScreen());
        handler.post(clockTicker);
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private View buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_VOID);

        globe = new GlobeSurfaceView(this, this::selectCityFromGlobe);
        root.addView(globe, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        addHeader(root);
        addWeatherCard(root);
        addOrbitLegend(root);
        addDrawer(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            applySystemBarInsets(insets);
            return insets;
        });
        root.requestApplyInsets();
        return root;
    }

    private void addHeader(FrameLayout root) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, 0, dp(64), 0);

        TextView overline = text("●  LIVE PLANETARY WEATHER", 10, COLOR_CYAN, Typeface.BOLD);
        overline.setLetterSpacing(0.16f);
        header.addView(overline);

        TextView title = text("NOCTURNE", 27, COLOR_TEXT, Typeface.BOLD);
        title.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(4);
        header.addView(title, titleParams);

        TextView subtitle = text("EARTH WEATHER OBSERVATORY", 10, COLOR_MUTED, Typeface.BOLD);
        subtitle.setLetterSpacing(0.11f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(3);
        header.addView(subtitle, subtitleParams);

        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        headerParams.leftMargin = dp(20);
        headerParams.topMargin = dp(34);
        root.addView(header, headerParams);
        headerView = header;

        TextView menu = text("☰", 29, COLOR_TEXT, Typeface.NORMAL);
        menu.setGravity(Gravity.CENTER);
        menu.setContentDescription("Open planet tools");
        menu.setBackground(roundRect(Color.argb(215, 10, 24, 47), dp(20),
                dp(1), Color.argb(110, 90, 207, 255)));
        menu.setOnClickListener(view -> toggleDrawer());
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(dp(48), dp(48),
                Gravity.TOP | Gravity.END);
        menuParams.rightMargin = dp(18);
        menuParams.topMargin = dp(29);
        root.addView(menu, menuParams);
        menuView = menu;
    }

    private void addOrbitLegend(FrameLayout root) {
        TextView legend = text("DRAG TO ORBIT   ·   PINCH TO ZOOM   ·   TAP A CITY SIGNAL",
                9, Color.argb(205, 174, 207, 231), Typeface.BOLD);
        legend.setLetterSpacing(0.08f);
        legend.setPadding(dp(12), dp(8), dp(12), dp(8));
        legend.setBackground(roundRect(Color.argb(132, 6, 15, 31), dp(15), 0, Color.TRANSPARENT));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        params.bottomMargin = dp(198);
        root.addView(legend, params);
        orbitLegendView = legend;
    }

    private void addWeatherCard(FrameLayout root) {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(15), dp(17), dp(14));
        card.setBackground(roundRect(Color.argb(239, 7, 16, 34), dp(23),
                dp(1), Color.argb(118, 72, 190, 229)));
        card.setElevation(dp(8));
        card.setOnClickListener(view -> {
            if (activeCity != null) requestWeather(activeCity, true);
        });
        card.setContentDescription("Selected city weather. Tap to refresh.");

        // ── Header: city photograph, identity, unit toggle ─────────────────────
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);

        cityPhotoContainer = new FrameLayout(this);
        cityPhotoContainer.setBackground(roundRect(COLOR_PANEL_STRONG, dp(14),
                dp(1), Color.argb(130, 76, 205, 249)));
        cityPhoto = new ImageView(this);
        cityPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cityPhoto.setClipToOutline(true);
        cityPhoto.setContentDescription("Photograph of the selected city");
        cityPhotoContainer.addView(cityPhoto, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cityPhotoFallback = text("◍", 24, Color.argb(190, 75, 212, 255), Typeface.BOLD);
        cityPhotoFallback.setGravity(Gravity.CENTER);
        cityPhotoFallback.setContentDescription(null);
        cityPhotoContainer.addView(cityPhotoFallback, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        top.addView(cityPhotoContainer, new LinearLayout.LayoutParams(dp(74), dp(74)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        cityName = text("AWAITING A CITY", 16, COLOR_TEXT, Typeface.BOLD);
        cityName.setLetterSpacing(0.045f);
        cityCountry = text("TAP A LUMINOUS MARKER TO BEGIN", 10, COLOR_CYAN, Typeface.BOLD);
        cityCountry.setLetterSpacing(0.08f);
        cityCountry.setSingleLine(true);
        cityCountry.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(cityName);
        LinearLayout.LayoutParams countryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countryParams.topMargin = dp(3);
        titles.addView(cityCountry, countryParams);
        localTime = text("WORLD CLOCK READY", 10.5f, COLOR_MUTED, Typeface.NORMAL);
        localTime.setLetterSpacing(0.04f);
        localTime.setSingleLine(true);
        localTime.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = dp(4);
        titles.addView(localTime, timeParams);

        unitsButton = text("°C", 12, COLOR_CYAN, Typeface.BOLD);
        unitsButton.setGravity(Gravity.CENTER);
        unitsButton.setLetterSpacing(0.04f);
        unitsButton.setBackground(roundRect(Color.argb(175, 17, 49, 76), dp(14),
                dp(1), Color.argb(105, 84, 210, 255)));
        unitsButton.setContentDescription("Change temperature unit");
        unitsButton.setOnClickListener(view -> toggleTemperatureUnit());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(45), dp(30));
        buttonParams.leftMargin = dp(10);
        titles.addView(unitsButton, buttonParams);

        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titlesParams.leftMargin = dp(13);
        top.addView(titles, titlesParams);
        card.addView(top);

        weatherLine = text("LIVE CONDITIONS WILL APPEAR HERE", 19, COLOR_TEXT, Typeface.BOLD);
        weatherLine.setSingleLine(false);
        weatherLine.setLineSpacing(0f, 0.92f);
        LinearLayout.LayoutParams weatherParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        weatherParams.topMargin = dp(13);
        card.addView(weatherLine, weatherParams);

        // ── Metrics grid: wind · humidity · rain / sun · UV · pressure ─────────
        card.addView(buildMetricsRow());
        card.addView(buildMetricsRowSecond());

        metricsLine = text("EXPLORE THE LIVE GLOBE AND TAP A CITY", 10.5f, COLOR_MUTED, Typeface.NORMAL);
        metricsLine.setSingleLine(false);
        metricsLine.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams metricsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metricsParams.topMargin = dp(7);
        card.addView(metricsLine, metricsParams);

        // ── Live broadcast section ─────────────────────────────────────────────
        livePanel = new LiveWeatherPanel(this);
        livePanel.setVisibility(View.GONE);
        livePanel.setOnClose(this::collapseLiveBroadcast);
        LinearLayout.LayoutParams liveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
        liveParams.topMargin = dp(12);
        card.addView(livePanel, liveParams);

        liveResumeChip = text("▶   START LIVE WEATHER BROADCAST", 10.5f, COLOR_CYAN, Typeface.BOLD);
        liveResumeChip.setLetterSpacing(0.08f);
        liveResumeChip.setGravity(Gravity.CENTER);
        liveResumeChip.setVisibility(View.GONE);
        liveResumeChip.setContentDescription("Start the live weather broadcast");
        liveResumeChip.setBackground(roundRect(Color.argb(175, 17, 49, 76), dp(16),
                dp(1), Color.argb(105, 84, 210, 255)));
        liveResumeChip.setOnClickListener(view -> expandLiveBroadcast());
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        chipParams.topMargin = dp(12);
        card.addView(liveResumeChip, chipParams);

        weatherStatus = text("OPEN-METEO LIVE DATA · TAP CARD TO REFRESH", 9, COLOR_CYAN, Typeface.BOLD);
        weatherStatus.setLetterSpacing(0.075f);
        weatherStatus.setSingleLine(true);
        weatherStatus.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(12);
        card.addView(weatherStatus, statusParams);

        ScrollView scroll = new ScrollView(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, heightSpec);
                int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * 0.68f);
                setMeasuredDimension(getMeasuredWidth(), Math.min(getMeasuredHeight(), maxHeight));
            }
        };
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        card.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(card);
        cardScroll = scroll;

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cardParams.leftMargin = dp(16);
        cardParams.rightMargin = dp(16);
        cardParams.bottomMargin = dp(22);
        root.addView(scroll, cardParams);
        weatherCardView = card;
    }

    private LinearLayout buildMetricsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        row.setLayoutParams(params);
        addMetricCell(row, "WIND", v -> windValue = v);
        addMetricCell(row, "HUMIDITY", v -> humidityValue = v);
        addMetricCell(row, "RAIN", v -> rainValue = v);
        return row;
    }

    private LinearLayout buildMetricsRowSecond() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(params);
        addMetricCell(row, "SUNRISE · SUNSET", v -> sunValue = v);
        addMetricCell(row, "UV INDEX", v -> uvValue = v);
        addMetricCell(row, "PRESSURE", v -> pressureValue = v);
        return row;
    }

    private void addMetricCell(LinearLayout row, String label, MetricSetter setter) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        TextView labelView = text(label, 8.5f, COLOR_MUTED, Typeface.BOLD);
        labelView.setLetterSpacing(0.09f);
        cell.addView(labelView);
        TextView value = text("—", 12.5f, COLOR_TEXT, Typeface.BOLD);
        value.setSingleLine(true);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dp(3);
        cell.addView(value, valueParams);
        setter.accept(value);
        LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) cellParams.leftMargin = dp(10);
        row.addView(cell, cellParams);
    }

    private interface MetricSetter {
        void accept(TextView value);
    }

    private void addDrawer(FrameLayout root) {
        drawerScrim = new View(this);
        drawerScrim.setBackgroundColor(Color.argb(188, 0, 2, 8));
        drawerScrim.setAlpha(0f);
        drawerScrim.setVisibility(View.GONE);
        drawerScrim.setOnClickListener(view -> closeDrawer());
        root.addView(drawerScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setPadding(dp(21), dp(38), dp(17), dp(22));
        drawer.setBackground(roundRect(Color.rgb(7, 17, 35), dp(0), 0, Color.TRANSPARENT));
        drawer.setElevation(dp(22));
        drawer.setVisibility(View.GONE);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("PLANET TOOLS", 19, COLOR_TEXT, Typeface.BOLD);
        label.setLetterSpacing(0.07f);
        titleRow.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = text("×", 28, COLOR_MUTED, Typeface.NORMAL);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close planet tools");
        close.setOnClickListener(view -> closeDrawer());
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(42), dp(38)));
        drawer.addView(titleRow);

        TextView description = text("EXPLORE, FILTER, AND PERSONALIZE THE NIGHT GLOBE", 10,
                COLOR_MUTED, Typeface.BOLD);
        description.setLetterSpacing(0.09f);
        description.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(6);
        descriptionParams.bottomMargin = dp(17);
        drawer.addView(description, descriptionParams);

        drawer.addView(toolAction("⌕  FIND A CITY", "Search " + CityCatalog.ALL.size()
                + " global city signals", view -> showCityFinder()));
        drawer.addView(toolAction("◎  MY LOCATION", "Center the globe after you allow location access",
                view -> requestMyLocation()));
        drawer.addView(divider());
        drawer.addView(toolSwitch("AUTO ORBIT", "A slow cinematic drift after you stop touching", true,
                (button, enabled) -> globe.setAutoOrbit(enabled)));
        drawer.addView(toolSwitch("CITY SIGNALS", "Show or hide selectable major-city markers", true,
                (button, enabled) -> globe.setMarkersVisible(enabled)));
        drawer.addView(toolSwitch("NIGHT LIGHTS", "Intensify the satellite city-light layer", true,
                (button, enabled) -> globe.setNightLightsEnabled(enabled)));
        drawer.addView(toolSwitch("CLOUD VEIL", "Soft high-altitude cloud texture", true,
                (button, enabled) -> globe.setCloudsEnabled(enabled)));
        drawer.addView(toolSwitch("LIVE BROADCAST", "Auto-start a 24/7 live weather channel when a city opens",
                liveBroadcastEnabled, (button, enabled) -> {
                    liveBroadcastEnabled = enabled;
                    if (enabled && activeCity != null && livePanel.getVisibility() != View.VISIBLE) {
                        expandLiveBroadcast();
                    } else if (!enabled) {
                        collapseLiveBroadcast();
                    }
                }));
        drawer.addView(divider());
        LinearLayout unitsRow = toolAction("TEMPERATURE · °C", "Switch to Fahrenheit",
                view -> toggleTemperatureUnit());
        drawerUnitsAction = (TextView) unitsRow.getChildAt(0);
        drawer.addView(unitsRow);
        drawer.addView(toolAction("↺  RESET ORBIT", "Restore the showcase view", view -> {
            globe.resetGlobe();
            closeDrawer();
        }));

        View flexibleSpace = new View(this);
        drawer.addView(flexibleSpace, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView privacy = text("PRIVACY\nWeather loads only for a place you select. Your location is requested only from this tool and is never stored.",
                10, COLOR_MUTED, Typeface.NORMAL);
        privacy.setLineSpacing(dp(3), 1f);
        drawer.addView(privacy);
        TextView sources = text("ABOUT & DATA SOURCES", 10, COLOR_CYAN, Typeface.BOLD);
        sources.setLetterSpacing(0.08f);
        sources.setGravity(Gravity.CENTER_VERTICAL);
        sources.setPadding(0, dp(16), 0, dp(6));
        sources.setOnClickListener(view -> showAbout());
        drawer.addView(sources, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(43)));

        int maxDrawerWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.86f);
        int drawerWidth = Math.min(dp(350), maxDrawerWidth);
        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(drawerWidth,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START | Gravity.TOP);
        root.addView(drawer, drawerParams);
    }

    /** Keeps the HUD clear of status/navigation bars, including Android 15/16 enforced edge-to-edge. */
    private void applySystemBarInsets(WindowInsets insets) {
        int top = Math.max(dp(29), insets.getSystemWindowInsetTop() + dp(9));
        int bottom = Math.max(dp(18), insets.getSystemWindowInsetBottom() + dp(10));
        if (headerView != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) headerView.getLayoutParams();
            params.topMargin = top;
            headerView.setLayoutParams(params);
        }
        if (menuView != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) menuView.getLayoutParams();
            params.topMargin = Math.max(dp(25), insets.getSystemWindowInsetTop() + dp(5));
            menuView.setLayoutParams(params);
        }
        if (cardScroll != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) cardScroll.getLayoutParams();
            params.bottomMargin = bottom;
            cardScroll.setLayoutParams(params);
        }
        if (orbitLegendView != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) orbitLegendView.getLayoutParams();
            params.bottomMargin = bottom + dp(176);
            orbitLegendView.setLayoutParams(params);
        }
    }

    private LinearLayout toolAction(String headline, String detail, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(8), dp(9));
        row.setBackground(roundRect(Color.argb(0, 0, 0, 0), dp(14), 0, Color.TRANSPARENT));
        row.setOnClickListener(listener);
        TextView title = text(headline, 11, COLOR_TEXT, Typeface.BOLD);
        title.setLetterSpacing(0.065f);
        row.addView(title);
        TextView subtitle = text(detail, 10, COLOR_MUTED, Typeface.NORMAL);
        subtitle.setSingleLine(false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(3);
        row.addView(subtitle, detailParams);
        // The title keeps a handle to its row so the temperature detail can be updated in place.
        title.setTag(row);
        title.setOnClickListener(listener);
        return row;
    }

    private View toolSwitch(String headline, String detail, boolean checked,
                            android.widget.CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(4), dp(8));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(headline, 11, COLOR_TEXT, Typeface.BOLD);
        title.setLetterSpacing(0.065f);
        labels.addView(title);
        TextView subtitle = text(detail, 9, COLOR_MUTED, Typeface.NORMAL);
        subtitle.setSingleLine(false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(3);
        labels.addView(subtitle, subtitleParams);
        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setShowText(false);
        toggle.setContentDescription(headline);
        toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(57), dp(48)));
        row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
        return row;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(74, 107, 179, 219));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.topMargin = dp(9);
        params.bottomMargin = dp(8);
        divider.setLayoutParams(params);
        return divider;
    }

    private void selectCityFromGlobe(City city) {
        selectCity(city, false);
    }

    private void selectCity(City city, boolean fromTool) {
        activeCity = city;
        activeWeather = null;
        globe.focusOn(city);
        cityName.setText(city.name.toUpperCase(Locale.getDefault()));
        cityCountry.setText(city.country.toUpperCase(Locale.getDefault()) + "  ·  "
                + compactZone(city.zoneId));
        weatherLine.setText("CONNECTING TO LIVE CONDITIONS");
        metricsLine.setText("REQUESTING CURRENT WEATHER FOR THIS COORDINATE");
        weatherStatus.setText("●  FETCHING OPEN-METEO LIVE DATA");
        updateClock();
        if (fromTool) closeDrawer();
        orbitLegendView.setVisibility(View.GONE);
        requestWeather(city, false);
        updateCityPhoto(city);
        if (liveBroadcastEnabled) {
            expandLiveBroadcast();
        } else {
            livePanel.setVisibility(View.GONE);
            liveResumeChip.setVisibility(View.VISIBLE);
        }
    }

    private void expandLiveBroadcast() {
        if (activeCity == null) return;
        liveResumeChip.setVisibility(View.GONE);
        livePanel.setVisibility(View.VISIBLE);
        startLiveBroadcast();
    }

    private void collapseLiveBroadcast() {
        livePanel.setVisibility(View.GONE);
        if (activeCity != null) liveResumeChip.setVisibility(View.VISIBLE);
    }

    private void startLiveBroadcast() {
        if (activeCity == null) return;
        LiveWeatherCatalog.Entry entry = LiveWeatherCatalog.entryFor(activeCity.country);
        if (entry != null) {
            livePanel.load(entry.liveUrl, entry.channelLabel);
        } else {
            livePanel.load(LiveWeatherCatalog.liveSearchUrl(activeCity.country),
                    LiveWeatherCatalog.searchLabelFor(activeCity.country));
        }
    }

    private void updateCityPhoto(City city) {
        String key = city.key();
        cityPhoto.setImageBitmap(null);
        cityPhotoFallback.setText(String.valueOf(city.name == null || city.name.isEmpty()
                ? '◍' : city.name.charAt(0)).toUpperCase(Locale.getDefault()));
        cityPhotoFallback.setVisibility(View.VISIBLE);
        CityImageLoader.load(city, (cityKey, bitmap) -> {
            if (!key.equals(cityKey)) return; // a newer city was selected in the meantime
            if (bitmap != null) {
                cityPhoto.setImageBitmap(bitmap);
                cityPhotoFallback.setVisibility(View.GONE);
            }
        });
    }

    private void requestWeather(City city, boolean forceRefresh) {
        String cityKey = city.key();
        if (activeCity != null && cityKey.equals(activeCity.key())) {
            weatherStatus.setText(forceRefresh ? "●  REFRESHING LIVE DATA" : "●  FETCHING OPEN-METEO LIVE DATA");
        }
        WeatherRepository.fetch(city, forceRefresh, new WeatherRepository.Callback() {
            @Override public void onSuccess(WeatherSnapshot snapshot, boolean fromCache) {
                runOnUiThread(() -> {
                    if (activeCity == null || !cityKey.equals(activeCity.key())) return;
                    activeWeather = snapshot;
                    renderWeather(snapshot, fromCache);
                    updateClock();
                });
            }

            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    if (activeCity == null || !cityKey.equals(activeCity.key())) return;
                    weatherLine.setText("LIVE WEATHER UNAVAILABLE");
                    metricsLine.setText(message + " Your city time remains available offline.");
                    weatherStatus.setText("○  CHECK CONNECTION AND TAP TO RETRY");
                });
            }
        });
    }

    private void renderWeather(WeatherSnapshot snapshot, boolean fromCache) {
        weatherLine.setText(snapshot.condition().toUpperCase(Locale.getDefault()) + "   "
                + snapshot.temperature(fahrenheit));

        windValue.setText(snapshot.hasWind()
                ? String.format(Locale.getDefault(), "%.0f km/h %s %s",
                        snapshot.windKph, snapshot.windDirectionLabel(), snapshot.windArrow())
                : "—");
        humidityValue.setText(snapshot.hasHumidity()
                ? String.format(Locale.getDefault(), "%d%%", Math.round(snapshot.humidityPercent))
                : "—");
        if (snapshot.precipitationMm > 0.04d) {
            rainValue.setText(String.format(Locale.getDefault(), "%.1f mm%s",
                    snapshot.precipitationMm,
                    snapshot.hasRainChance()
                            ? "  ·  " + Math.round(snapshot.rainChancePercent) + "% chance" : ""));
        } else if (snapshot.hasRainChance() && snapshot.rainChancePercent >= 1d) {
            rainValue.setText(String.format(Locale.getDefault(), "%d%% chance",
                    Math.round(snapshot.rainChancePercent)));
        } else {
            rainValue.setText("Dry");
        }
        sunValue.setText(snapshot.hasSun()
                ? WeatherSnapshot.clockPart(snapshot.sunriseIso) + "  ·  "
                        + WeatherSnapshot.clockPart(snapshot.sunsetIso)
                : "—");
        if (snapshot.hasUv()) {
            uvValue.setText(String.format(Locale.getDefault(), "%.0f · %s",
                    snapshot.uvIndex, snapshot.uvLabel()));
            int[] uvColors = {
                    Color.rgb(116, 230, 146),
                    Color.rgb(255, 200, 87),
                    Color.rgb(255, 152, 70),
                    Color.rgb(255, 99, 99),
                    Color.rgb(206, 126, 255),
            };
            uvValue.setTextColor(uvColors[Math.max(0, Math.min(4, snapshot.uvRating()))]);
        } else {
            uvValue.setText("—");
            uvValue.setTextColor(COLOR_TEXT);
        }
        pressureValue.setText(snapshot.hasPressure()
                ? String.format(Locale.getDefault(), "%.0f hPa", snapshot.pressureHpa)
                : "—");

        String gusts = snapshot.hasWind() && !Double.isNaN(snapshot.windGustsKph)
                && snapshot.windGustsKph > snapshot.windKph
                ? String.format(Locale.getDefault(), "  ·  GUSTS %.0f km/h", snapshot.windGustsKph)
                : "";
        metricsLine.setText("FEELS " + snapshot.apparentTemperature(fahrenheit) + gusts);
        try {
            ZoneId zone = ZoneId.of(snapshot.providerZoneId);
            String updated = ZonedDateTime.now(zone).format(UPDATED_FORMAT);
            weatherStatus.setText((fromCache ? "●  RECENTLY CACHED" : "●  LIVE")
                    + "  ·  UPDATED " + updated + "  ·  OPEN-METEO");
        } catch (Exception ignored) {
            weatherStatus.setText((fromCache ? "●  RECENTLY CACHED" : "●  LIVE")
                    + "  ·  OPEN-METEO");
        }
    }

    private void updateClock() {
        if (activeCity == null) return;
        String zoneName = activeWeather != null && activeWeather.providerZoneId != null
                ? activeWeather.providerZoneId : activeCity.zoneId;
        try {
            ZoneId zone = ZoneId.of(zoneName);
            localTime.setText("LOCAL TIME  ·  " + ZonedDateTime.now(zone).format(CLOCK_FORMAT));
        } catch (Exception ignored) {
            localTime.setText("LOCAL TIME  ·  " + ZonedDateTime.now().format(CLOCK_FORMAT));
        }
    }

    private void toggleTemperatureUnit() {
        fahrenheit = !fahrenheit;
        unitsButton.setText(fahrenheit ? "°F" : "°C");
        if (drawerUnitsAction != null) {
            drawerUnitsAction.setText(fahrenheit ? "TEMPERATURE · °F" : "TEMPERATURE · °C");
            View parent = (View) drawerUnitsAction.getTag();
            if (parent instanceof LinearLayout && ((LinearLayout) parent).getChildCount() > 1) {
                View detail = ((LinearLayout) parent).getChildAt(1);
                if (detail instanceof TextView) {
                    ((TextView) detail).setText(fahrenheit ? "Switch to Celsius" : "Switch to Fahrenheit");
                }
            }
        }
        if (activeWeather != null) renderWeather(activeWeather, false);
    }

    private void toggleDrawer() {
        if (drawerOpen) closeDrawer(); else openDrawer();
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        drawerScrim.setVisibility(View.VISIBLE);
        drawer.setVisibility(View.VISIBLE);
        int width = drawer.getLayoutParams().width;
        drawer.setTranslationX(-width);
        drawerScrim.animate().alpha(1f).setDuration(180L).start();
        drawer.animate().translationX(0f).setDuration(280L)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        int width = drawer.getLayoutParams().width;
        drawerScrim.animate().alpha(0f).setDuration(180L)
                .withEndAction(() -> drawerScrim.setVisibility(View.GONE)).start();
        drawer.animate().translationX(-width).setDuration(220L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> drawer.setVisibility(View.GONE)).start();
    }

    private void showCityFinder() {
        closeDrawer();
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(21), dp(20), dp(18));
        panel.setBackground(roundRect(COLOR_PANEL, dp(25), dp(1), Color.argb(130, 76, 205, 249)));

        TextView title = text("FIND A CITY", 20, COLOR_TEXT, Typeface.BOLD);
        title.setLetterSpacing(0.07f);
        panel.addView(title);
        TextView subhead = text(CityCatalog.ALL.size() + " CAPITALS AND MAJOR METROPOLITAN SIGNALS", 10,
                COLOR_MUTED, Typeface.BOLD);
        subhead.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams subheadParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subheadParams.topMargin = dp(5);
        panel.addView(subhead, subheadParams);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(COLOR_TEXT);
        search.setHintTextColor(COLOR_MUTED);
        search.setTextSize(15);
        search.setHint("Search city or country");
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        search.setPadding(dp(15), 0, dp(15), 0);
        search.setBackground(roundRect(COLOR_PANEL_STRONG, dp(17), dp(1),
                Color.argb(135, 86, 203, 242)));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        searchParams.topMargin = dp(16);
        panel.addView(search, searchParams);

        ListView resultsView = new ListView(this);
        resultsView.setDivider(new ColorDrawable(Color.argb(44, 99, 175, 220)));
        resultsView.setDividerHeight(dp(1));
        final List<City> results = new ArrayList<>(CityCatalog.search("", 36));
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return results.size(); }
            @Override public Object getItem(int position) { return results.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView row;
                if (convertView instanceof TextView) {
                    row = (TextView) convertView;
                } else {
                    row = new TextView(MainActivity.this);
                    row.setTextColor(COLOR_TEXT);
                    row.setTextSize(15);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(12), dp(4), dp(12), dp(4));
                    row.setLineSpacing(dp(2), 1f);
                    row.setMinHeight(dp(57));
                }
                City item = results.get(position);
                row.setText(item.name + "\n" + item.country + "  ·  " + compactZone(item.zoneId));
                return row;
            }
        };
        resultsView.setAdapter(adapter);
        resultsView.setOnItemClickListener((parent, view, position, id) -> {
            selectCity(results.get(position), true);
            dialog.dismiss();
        });
        LinearLayout.LayoutParams resultsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        resultsParams.topMargin = dp(12);
        panel.addView(resultsView, resultsParams);

        TextView close = text("CLOSE", 11, COLOR_CYAN, Typeface.BOLD);
        close.setLetterSpacing(0.09f);
        close.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        closeParams.topMargin = dp(8);
        panel.addView(close, closeParams);
        close.setOnClickListener(view -> dialog.dismiss());

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable editable) {
                results.clear();
                results.addAll(CityCatalog.search(editable.toString(), 44));
                adapter.notifyDataSetChanged();
            }
        });

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.91f),
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.76f));
        }
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.91f),
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.76f));
        }
    }

    private void requestMyLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            return;
        }
        acquireLocation();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                       int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                acquireLocation();
            } else {
                Toast.makeText(this, "Location was not granted. Choose a city instead.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    private void acquireLocation() {
        closeDrawer();
        Toast.makeText(this, "Finding your approximate location…", Toast.LENGTH_SHORT).show();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            Toast.makeText(this, "Location services are unavailable on this device.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Location best = null;
            String[] providers = {LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER};
            for (String provider : providers) {
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
            }
            if (best != null) {
                useLocation(best);
                return;
            }
            if (!locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Toast.makeText(this, "Turn on network location or choose a city.", Toast.LENGTH_LONG).show();
                return;
            }
            oneShotLocationListener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    if (location != null) useLocation(location);
                    stopLocationUpdates();
                }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
                @Override public void onProviderEnabled(String provider) { }
                @Override public void onProviderDisabled(String provider) { }
            };
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f,
                    oneShotLocationListener, Looper.getMainLooper());
            handler.postDelayed(() -> {
                if (oneShotLocationListener != null) {
                    stopLocationUpdates();
                    Toast.makeText(this, "Could not get a location. Choose a city instead.", Toast.LENGTH_SHORT).show();
                }
            }, 12_000L);
        } catch (SecurityException error) {
            Toast.makeText(this, "Location permission is needed for this tool.", Toast.LENGTH_SHORT).show();
        }
    }

    private void useLocation(Location location) {
        City here = new City("Your location", "Current device", location.getLatitude(),
                location.getLongitude(), ZoneId.systemDefault().getId());
        selectCity(here, false);
    }

    @SuppressWarnings("MissingPermission")
    private void stopLocationUpdates() {
        if (locationManager != null && oneShotLocationListener != null) {
            try {
                locationManager.removeUpdates(oneShotLocationListener);
            } catch (SecurityException ignored) { }
        }
        oneShotLocationListener = null;
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("Nocturne Earth")
                .setMessage("A native OpenGL ES globe for Android 10 and newer.\n\n"
                        + "Controls: drag to orbit, pinch to zoom, tap a city signal, or double-tap to reset. "
                        + "The day/night terminator follows the real position of the sun at your current UTC time.\n\n"
                        + "Live conditions: Open-Meteo (no personal API key required). Weather requests "
                        + "are made only after you choose a place.\n\n"
                        + "City photographs: Wikipedia (CC BY-SA and other licenses, via the Wikipedia REST API).\n\n"
                        + "Live broadcasts: 24/7 channels on YouTube (WeatherNation, Sky News, France 24, "
                        + "DW News and others) or live YouTube search results, streamed only when you start them.\n\n"
                        + "Texture attribution: Solar System Scope Earth day, night and cloud textures, "
                        + "CC BY 4.0, based on NASA imagery. See ASSET_ATTRIBUTION.md in the source project.")
                .setPositiveButton("DONE", null)
                .show();
    }

    @Override public void onBackPressed() {
        if (livePanel != null && livePanel.isFullscreenOpen()) {
            livePanel.closeFullscreen();
        } else if (drawerOpen) {
            closeDrawer();
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onDestroy() {
        if (livePanel != null) livePanel.destroy();
        stopLocationUpdates();
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        if (globe != null) globe.onResume();
        handler.removeCallbacks(clockTicker);
        handler.post(clockTicker);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(clockTicker);
        stopLocationUpdates();
        if (globe != null) globe.onPause();
        super.onPause();
    }

    private TextView text(String value, float sizeSp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable roundRect(int color, int radius, int strokeWidth, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String compactZone(String zoneId) {
        int slash = zoneId.lastIndexOf('/');
        return (slash >= 0 ? zoneId.substring(slash + 1) : zoneId).replace('_', ' ')
                .toUpperCase(Locale.getDefault());
    }
}

}
