package com.nocturne.earthweather;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;

/** Full-screen touch surface: one-finger orbit, inertia, pinch zoom, city picking, double-tap reset. */
public final class GlobeSurfaceView extends GLSurfaceView {
    public interface CityTapListener {
        void onCityTapped(City city);
    }

    private final GlobeRenderer globeRenderer;
    private final CityTapListener cityTapListener;
    private final GestureDetector gestures;
    private final ScaleGestureDetector scaleGestures;
    private final float touchSlop;

    private float downX;
    private float downY;
    private boolean moved;
    private boolean usedScale;
    private boolean doubleTapped;

    public GlobeSurfaceView(Context context, CityTapListener cityTapListener) {
        super(context);
        this.cityTapListener = cityTapListener;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        globeRenderer = new GlobeRenderer(context, CityCatalog.ALL);
        setRenderer(globeRenderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) {
                globeRenderer.beginTouch();
                return true;
            }

            @Override public boolean onScroll(MotionEvent first, MotionEvent current,
                                              float distanceX, float distanceY) {
                if (!scaleGestures.isInProgress()) {
                    moved = true;
                    // GestureDetector reports previous-current; invert it so the globe follows a drag.
                    globeRenderer.dragBy(-distanceX, -distanceY);
                }
                return true;
            }

            @Override public boolean onDoubleTap(MotionEvent event) {
                doubleTapped = true;
                moved = true;
                globeRenderer.resetView();
                return true;
            }
        });

        scaleGestures = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                        usedScale = true;
                        moved = true;
                        return true;
                    }

                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        globeRenderer.zoomBy(detector.getScaleFactor());
                        return true;
                    }
                });
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            moved = false;
            usedScale = false;
            doubleTapped = false;
        } else if (action == MotionEvent.ACTION_MOVE && !usedScale) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (dx * dx + dy * dy > touchSlop * touchSlop) moved = true;
        }

        scaleGestures.onTouchEvent(event);
        gestures.onTouchEvent(event);

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (action == MotionEvent.ACTION_UP && !moved && !usedScale && !doubleTapped) {
                City city = globeRenderer.pickCity(event.getX(), event.getY(), getWidth(), getHeight());
                if (city != null) {
                    globeRenderer.focusOn(city);
                    cityTapListener.onCityTapped(city);
                }
            }
            globeRenderer.endTouch();
            performClick();
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    public void focusOn(City city) {
        globeRenderer.focusOn(city);
    }

    public void resetGlobe() {
        globeRenderer.resetView();
    }

    public void setAutoOrbit(boolean enabled) {
        globeRenderer.setAutoOrbit(enabled);
    }

    public void setCloudsEnabled(boolean enabled) {
        globeRenderer.setCloudsEnabled(enabled);
    }

    public void setMarkersVisible(boolean enabled) {
        globeRenderer.setMarkersVisible(enabled);
    }

    public void setNightLightsEnabled(boolean enabled) {
        globeRenderer.setNightLightsEnabled(enabled);
    }
}
