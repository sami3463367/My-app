package com.nocturne.earthweather;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.List;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * A compact GLES 2 renderer designed for consistently smooth Android 10+ interaction. It keeps
 * the planet, atmosphere, star field, and city lights in a few batched draw calls rather than
 * creating a view for every marker.
 */
public final class GlobeRenderer implements android.opengl.GLSurfaceView.Renderer {
    private static final float PI = (float) Math.PI;
    private static final int LATITUDE_BANDS = 112;
    private static final int LONGITUDE_BANDS = 224;
    private static final int STRIDE_BYTES = 8 * 4;

    private final Context context;
    private final List<City> cities;
    private final float density;
    private final Object stateLock = new Object();

    private FloatBuffer sphereVertices;
    private ShortBuffer sphereIndices;
    private int sphereIndexCount;
    private FloatBuffer cityVertices;
    private FloatBuffer stars;
    private int starCount;

    private int earthProgram;
    private int atmosphereProgram;
    private int cityProgram;
    private int starProgram;
    private int earthTexture;
    private int dayTexture;
    private int cloudTexture;
    private final float[] sunLocal = new float[3];
    private final float[] sunWorld = new float[3];

    private final float[] model = new float[16];
    private final float[] view = new float[16];
    private final float[] projection = new float[16];
    private final float[] modelView = new float[16];
    private final float[] mvp = new float[16];
    private final float[] viewProjection = new float[16];

    private volatile float aspect = 1f;
    private long previousFrameNanos;
    private volatile boolean rendererFatal;

    // All angles are radians. Initial framing presents the whole disc with comfortable
    // breathing room (deliberately 50% further back than a tight crop), then slowly drifts.
    private float yaw = PI / 2f;
    private float pitch = -0.12f;
    private float distance = 6.32f;
    private static final float MIN_DISTANCE = 1.34f;
    private static final float MAX_DISTANCE = 6.36f;
    private float yawVelocity;
    private float pitchVelocity;
    private float targetYaw = Float.NaN;
    private float targetPitch = Float.NaN;
    private float targetDistance = Float.NaN;
    private boolean interacting;
    private long lastInteractionMillis;
    private boolean autoOrbit = true;
    private boolean cloudsEnabled = true;
    private volatile boolean markersVisible = true;
    private boolean nightLightsEnabled = true;
    private boolean cityBufferDirty = true;
    private String selectedKey;

    public GlobeRenderer(Context context, List<City> cities) {
        this.context = context.getApplicationContext();
        this.cities = cities;
        this.density = context.getResources().getDisplayMetrics().density;
        buildSphere();
        buildStars();
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        try {
            GLES20.glClearColor(0.001f, 0.003f, 0.012f, 1f);
            GLES20.glDisable(GLES20.GL_DITHER);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);

            earthProgram = createProgram(EARTH_VERTEX_SHADER, EARTH_FRAGMENT_SHADER);
            atmosphereProgram = createProgram(ATMOSPHERE_VERTEX_SHADER, ATMOSPHERE_FRAGMENT_SHADER);
            cityProgram = createProgram(CITY_VERTEX_SHADER, CITY_FRAGMENT_SHADER);
            starProgram = createProgram(STAR_VERTEX_SHADER, STAR_FRAGMENT_SHADER);

            int[] maxTextureSize = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0);
            // Target long edges keep decoded bitmaps comfortably small (a few MB each) so the
            // init never blows the process memory budget, whatever the source resolution.
            earthTexture = loadTexture(R.drawable.earth_night, 2048, 0xFF02050E);
            dayTexture = loadTexture(R.drawable.earth_day, 1536, 0xFF143054);
            cloudTexture = loadTexture(R.drawable.earth_clouds, 1536, 0xFF000000);
            cityBufferDirty = true;
            previousFrameNanos = 0L;
        } catch (Throwable error) {
            // A broken GPU context must not take the whole app down; the HUD stays usable.
            Log.e("GlobeRenderer", "OpenGL initialisation failed", error);
            rendererFatal = true;
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        aspect = height == 0 ? 1f : (float) width / (float) height;
        Matrix.perspectiveM(projection, 0, 38f, aspect, 0.1f, 80f);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        if (rendererFatal) {
            GLES20.glClearColor(0.001f, 0.003f, 0.012f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            return;
        }
        try {
            long nowNanos = System.nanoTime();
            float deltaSeconds = previousFrameNanos == 0L ? 0.016f
                    : Math.min(0.05f, (nowNanos - previousFrameNanos) / 1_000_000_000f);
            previousFrameNanos = nowNanos;

            Transform transform = advanceAndSnapshot(deltaSeconds);
            buildMatrices(transform);
            if (cityBufferDirty) rebuildCityBuffer();

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            float elapsed = SystemClock.elapsedRealtime() / 1000f;
            drawStars(elapsed);
            drawEarth(transform, elapsed);
            drawAtmosphere(transform);
            if (markersVisible) drawCities(transform, elapsed);
        } catch (Throwable error) {
            Log.e("GlobeRenderer", "Frame rendering failed", error);
            rendererFatal = true;
        }
    }

    private Transform advanceAndSnapshot(float deltaSeconds) {
        synchronized (stateLock) {
            if (!interacting) {
                if (!Float.isNaN(targetYaw)) {
                    float fraction = 1f - (float) Math.exp(-5.5f * deltaSeconds);
                    yaw += shortestAngle(targetYaw - yaw) * fraction;
                    pitch += (targetPitch - pitch) * fraction;
                    distance += (targetDistance - distance) * fraction;
                    if (Math.abs(shortestAngle(targetYaw - yaw)) < 0.002f
                            && Math.abs(targetPitch - pitch) < 0.002f
                            && Math.abs(targetDistance - distance) < 0.003f) {
                        yaw = targetYaw;
                        pitch = targetPitch;
                        distance = targetDistance;
                        targetYaw = Float.NaN;
                        targetPitch = Float.NaN;
                        targetDistance = Float.NaN;
                    }
                } else {
                    yaw += yawVelocity * deltaSeconds;
                    pitch += pitchVelocity * deltaSeconds;
                    float drag = (float) Math.pow(0.012, deltaSeconds);
                    yawVelocity *= drag;
                    pitchVelocity *= drag;
                    if (autoOrbit && SystemClock.elapsedRealtime() - lastInteractionMillis > 2300L
                            && Math.abs(yawVelocity) < 0.002f) {
                        yaw += 0.032f * deltaSeconds;
                    }
                }
                pitch = clamp(pitch, -1.32f, 1.32f);
                distance = clamp(distance, MIN_DISTANCE, MAX_DISTANCE);
            }
            return new Transform(yaw, pitch, distance,
                    cloudsEnabled, nightLightsEnabled, selectedKey);
        }
    }

    private void buildMatrices(Transform transform) {
        Matrix.setIdentityM(model, 0);
        // Post-multiplying yields Rx * Ry: yaw first, then a natural vertical pitch.
        Matrix.rotateM(model, 0, (float) Math.toDegrees(transform.pitch), 1f, 0f, 0f);
        Matrix.rotateM(model, 0, (float) Math.toDegrees(transform.yaw), 0f, 1f, 0f);
        Matrix.setLookAtM(view, 0,
                0f, 0f, transform.distance,
                0f, 0f, 0f,
                0f, 1f, 0f);
        Matrix.multiplyMM(modelView, 0, view, 0, model, 0);
        Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0);
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0);
    }

    /**
     * Computes the sun direction for the current UTC instant and transforms it into world space
     * (the sphere's rotated frame) so the shader can shade the true day/night terminator.
     * Low-precision solar ephemeris: accurate to well under a degree, which is far beyond what
     * the visual terminator needs.
     */
    private void computeSunDirection() {
        double daysSinceJ2000 = (System.currentTimeMillis() - 946_728_000_000L) / 86_400_000.0;
        double meanLongitude = Math.toRadians(280.460 + 0.9856474 * daysSinceJ2000);
        double meanAnomaly = Math.toRadians(357.528 + 0.9856003 * daysSinceJ2000);
        double eclipticLongitude = meanLongitude
                + Math.toRadians(1.915) * Math.sin(meanAnomaly)
                + Math.toRadians(0.020) * Math.sin(2.0 * meanAnomaly);
        double obliquity = Math.toRadians(23.439);
        double declination = Math.asin(Math.sin(eclipticLongitude) * Math.sin(obliquity));
        double rightAscension = Math.atan2(
                Math.sin(eclipticLongitude) * Math.cos(obliquity), Math.cos(eclipticLongitude));
        double gmst = Math.toRadians(280.46061837 + 360.98564736629 * daysSinceJ2000);
        double subsolarLongitude = normalizeAngle(rightAscension - gmst);

        // Same basis as cityPoint()/the equirectangular texture: longitude offset by PI.
        double cosDeclination = Math.cos(declination);
        double theta = subsolarLongitude + PI;
        sunLocal[0] = (float) (cosDeclination * Math.cos(theta));
        sunLocal[1] = (float) Math.sin(declination);
        sunLocal[2] = (float) (cosDeclination * Math.sin(theta));
        Matrix.multiplyMV(sunWorld, 0, model, 0, sunLocal, 0);
    }

    private static double normalizeAngle(double radians) {
        double angle = radians % (2.0 * Math.PI);
        if (angle > Math.PI) angle -= 2.0 * Math.PI;
        if (angle < -Math.PI) angle += 2.0 * Math.PI;
        return angle;
    }

    private void drawStars(float elapsed) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glUseProgram(starProgram);
        int position = GLES20.glGetAttribLocation(starProgram, "aPosition");
        int size = GLES20.glGetAttribLocation(starProgram, "aSize");
        int phase = GLES20.glGetAttribLocation(starProgram, "aPhase");
        int time = GLES20.glGetUniformLocation(starProgram, "uTime");
        stars.position(0);
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 4 * 4, stars);
        GLES20.glEnableVertexAttribArray(position);
        stars.position(2);
        GLES20.glVertexAttribPointer(size, 1, GLES20.GL_FLOAT, false, 4 * 4, stars);
        GLES20.glEnableVertexAttribArray(size);
        stars.position(3);
        GLES20.glVertexAttribPointer(phase, 1, GLES20.GL_FLOAT, false, 4 * 4, stars);
        GLES20.glEnableVertexAttribArray(phase);
        GLES20.glUniform1f(time, elapsed);
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, starCount);
        GLES20.glDisableVertexAttribArray(position);
        GLES20.glDisableVertexAttribArray(size);
        GLES20.glDisableVertexAttribArray(phase);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private void drawEarth(Transform transform, float elapsed) {
        GLES20.glUseProgram(earthProgram);
        int position = GLES20.glGetAttribLocation(earthProgram, "aPosition");
        int normal = GLES20.glGetAttribLocation(earthProgram, "aNormal");
        int uv = GLES20.glGetAttribLocation(earthProgram, "aUv");
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(earthProgram, "uMvp"), 1, false, mvp, 0);
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(earthProgram, "uModel"), 1, false, model, 0);
        GLES20.glUniform3f(GLES20.glGetUniformLocation(earthProgram, "uCamera"),
                0f, 0f, transform.distance);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(earthProgram, "uTime"), elapsed);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(earthProgram, "uClouds"),
                transform.clouds ? 1f : 0f);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(earthProgram, "uLightStrength"),
                transform.nightLights ? 1f : 0.08f);

        computeSunDirection();
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, earthTexture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(earthProgram, "uNightMap"), 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cloudTexture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(earthProgram, "uCloudMap"), 1);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, dayTexture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(earthProgram, "uDayMap"), 2);
        GLES20.glUniform3f(GLES20.glGetUniformLocation(earthProgram, "uSunDir"),
                sunWorld[0], sunWorld[1], sunWorld[2]);

        sphereVertices.position(0);
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, sphereVertices);
        GLES20.glEnableVertexAttribArray(position);
        sphereVertices.position(3);
        GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, sphereVertices);
        GLES20.glEnableVertexAttribArray(normal);
        sphereVertices.position(6);
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, sphereVertices);
        GLES20.glEnableVertexAttribArray(uv);
        sphereIndices.position(0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphereIndexCount,
                GLES20.GL_UNSIGNED_SHORT, sphereIndices);
        GLES20.glDisableVertexAttribArray(position);
        GLES20.glDisableVertexAttribArray(normal);
        GLES20.glDisableVertexAttribArray(uv);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    private void drawAtmosphere(Transform transform) {
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glDepthMask(false);
        GLES20.glUseProgram(atmosphereProgram);
        int position = GLES20.glGetAttribLocation(atmosphereProgram, "aPosition");
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(atmosphereProgram, "uMvp"),
                1, false, mvp, 0);
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(atmosphereProgram, "uModel"),
                1, false, model, 0);
        GLES20.glUniform3f(GLES20.glGetUniformLocation(atmosphereProgram, "uCamera"),
                0f, 0f, transform.distance);
        GLES20.glUniform3f(GLES20.glGetUniformLocation(atmosphereProgram, "uSunDir"),
                sunWorld[0], sunWorld[1], sunWorld[2]);
        sphereVertices.position(0);
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, sphereVertices);
        GLES20.glEnableVertexAttribArray(position);
        sphereIndices.position(0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphereIndexCount,
                GLES20.GL_UNSIGNED_SHORT, sphereIndices);
        GLES20.glDisableVertexAttribArray(position);
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void drawCities(Transform transform, float elapsed) {
        if (cityVertices == null) return;
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glDepthMask(false);
        GLES20.glUseProgram(cityProgram);
        int position = GLES20.glGetAttribLocation(cityProgram, "aPosition");
        int color = GLES20.glGetAttribLocation(cityProgram, "aColor");
        int selected = GLES20.glGetAttribLocation(cityProgram, "aSelected");
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(cityProgram, "uMvp"),
                1, false, mvp, 0);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(cityProgram, "uDensity"), density);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(cityProgram, "uZoom"),
                clamp((MAX_DISTANCE - transform.distance) / 1.6f, 0f, 3f));
        GLES20.glUniform1f(GLES20.glGetUniformLocation(cityProgram, "uTime"), elapsed);

        cityVertices.position(0);
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 7 * 4, cityVertices);
        GLES20.glEnableVertexAttribArray(position);
        cityVertices.position(3);
        GLES20.glVertexAttribPointer(color, 3, GLES20.GL_FLOAT, false, 7 * 4, cityVertices);
        GLES20.glEnableVertexAttribArray(color);
        cityVertices.position(6);
        GLES20.glVertexAttribPointer(selected, 1, GLES20.GL_FLOAT, false, 7 * 4, cityVertices);
        GLES20.glEnableVertexAttribArray(selected);
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, cities.size());
        GLES20.glDisableVertexAttribArray(position);
        GLES20.glDisableVertexAttribArray(color);
        GLES20.glDisableVertexAttribArray(selected);
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void buildSphere() {
        int vertexCount = (LATITUDE_BANDS + 1) * (LONGITUDE_BANDS + 1);
        sphereVertices = directFloats(vertexCount * 8);
        for (int lat = 0; lat <= LATITUDE_BANDS; lat++) {
            float v = (float) lat / LATITUDE_BANDS;
            float phi = v * PI;
            float sinPhi = (float) Math.sin(phi);
            float y = (float) Math.cos(phi);
            for (int lon = 0; lon <= LONGITUDE_BANDS; lon++) {
                float u = (float) lon / LONGITUDE_BANDS;
                float theta = u * PI * 2f;
                float x = sinPhi * (float) Math.cos(theta);
                float z = sinPhi * (float) Math.sin(theta);
                sphereVertices.put(x).put(y).put(z);
                sphereVertices.put(x).put(y).put(z);
                // Android bitmaps are uploaded top row first, so v=0 maps to the north pole.
                sphereVertices.put(u).put(v);
            }
        }
        sphereVertices.position(0);

        sphereIndexCount = LATITUDE_BANDS * LONGITUDE_BANDS * 6;
        sphereIndices = directShorts(sphereIndexCount);
        int row = LONGITUDE_BANDS + 1;
        for (int lat = 0; lat < LATITUDE_BANDS; lat++) {
            for (int lon = 0; lon < LONGITUDE_BANDS; lon++) {
                int a = lat * row + lon;
                int b = a + row;
                sphereIndices.put((short) a).put((short) b).put((short) (a + 1));
                sphereIndices.put((short) (a + 1)).put((short) b).put((short) (b + 1));
            }
        }
        sphereIndices.position(0);
    }

    private void buildStars() {
        starCount = 1550;
        stars = directFloats(starCount * 4);
        Random random = new Random(716_226_991L);
        for (int index = 0; index < starCount; index++) {
            // Keep a small central breathing space for the planet and cluster faint stars naturally.
            float x = -1.08f + random.nextFloat() * 2.16f;
            float y = -1.04f + random.nextFloat() * 2.08f;
            float size = random.nextFloat() < 0.055f
                    ? 1.5f + random.nextFloat() * 1.45f
                    : 0.42f + random.nextFloat() * 0.92f;
            stars.put(x).put(y).put(size * density).put(random.nextFloat() * PI * 2f);
        }
        stars.position(0);
    }

    private void rebuildCityBuffer() {
        String selected;
        synchronized (stateLock) {
            selected = selectedKey;
            cityBufferDirty = false;
        }
        cityVertices = directFloats(cities.size() * 7);
        for (City city : cities) {
            float[] point = cityPoint(city);
            boolean isSelected = selected != null && selected.equals(city.key());
            cityVertices.put(point[0] * 1.013f).put(point[1] * 1.013f).put(point[2] * 1.013f);
            if (isSelected) {
                cityVertices.put(1.00f).put(0.63f).put(0.22f).put(1f);
            } else {
                cityVertices.put(0.40f).put(0.88f).put(1.00f).put(0f);
            }
        }
        cityVertices.position(0);
    }

    /**
     * Decodes a texture resource at a resolution that fits the device memory budget. Never throws:
     * if decoding is impossible (corrupt asset, OOM on a weak device) a tiny solid colour is used
     * instead, so the app always opens.
     */
    private int loadTexture(int resourceId, int targetLongEdge, int fallbackColor) {
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            probe.inScaled = false;
            BitmapFactory.decodeResource(context.getResources(), resourceId, probe);
            int sourceWidth = probe.outWidth;
            int sourceHeight = probe.outHeight;
            if (sourceWidth > 0 && sourceHeight > 0) {
                int sample = 1;
                int longEdge = Math.max(sourceWidth, sourceHeight);
                while (longEdge / sample > targetLongEdge) sample *= 2;
                BitmapFactory.Options decode = new BitmapFactory.Options();
                decode.inSampleSize = sample;
                decode.inScaled = false;
                decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
                bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, decode);
            }
        } catch (Throwable ignored) {
            bitmap = null;
        }
        if (bitmap == null) {
            int[] pixels = new int[16];
            for (int i = 0; i < pixels.length; i++) pixels[i] = fallbackColor;
            bitmap = Bitmap.createBitmap(pixels, 4, 4, Bitmap.Config.ARGB_8888);
            Log.w("GlobeRenderer", "Texture " + resourceId + " fell back to a solid colour");
        }
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return textures[0];
    }

    private static FloatBuffer directFloats(int count) {
        return ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private static ShortBuffer directShorts(int count) {
        return ByteBuffer.allocateDirect(count * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex);
        GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            String message = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("Unable to link globe shader: " + message);
        }
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String message = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Unable to compile globe shader: " + message);
        }
        return shader;
    }

    public void beginTouch() {
        synchronized (stateLock) {
            interacting = true;
            targetYaw = Float.NaN;
            targetPitch = Float.NaN;
            targetDistance = Float.NaN;
            lastInteractionMillis = SystemClock.elapsedRealtime();
        }
    }

    public void dragBy(float deltaX, float deltaY) {
        synchronized (stateLock) {
            float sensitivity = 0.0044f * (0.72f + distance / 4.2f);
            yaw += deltaX * sensitivity;
            pitch = clamp(pitch + deltaY * sensitivity, -1.32f, 1.32f);
            // This low-pass estimate produces inertial movement without a twitch on slow drags.
            yawVelocity = yawVelocity * 0.58f + deltaX * sensitivity * 16f * 0.42f;
            pitchVelocity = pitchVelocity * 0.58f + deltaY * sensitivity * 16f * 0.42f;
            lastInteractionMillis = SystemClock.elapsedRealtime();
        }
    }

    public void zoomBy(float scaleFactor) {
        synchronized (stateLock) {
            if (scaleFactor > 0f) {
                distance = clamp(distance / scaleFactor, MIN_DISTANCE, MAX_DISTANCE);
                targetDistance = Float.NaN;
                lastInteractionMillis = SystemClock.elapsedRealtime();
            }
        }
    }

    public void endTouch() {
        synchronized (stateLock) {
            interacting = false;
            lastInteractionMillis = SystemClock.elapsedRealtime();
        }
    }

    public void focusOn(City city) {
        float[] point = cityPoint(city);
        synchronized (stateLock) {
            // Solve yaw, then pitch, so the selected latitude/longitude comes to the camera-facing
            // center without an abrupt reset of the user's current orbit.
            float rawYaw = (float) Math.atan2(-point[0], point[2]);
            float yawForPitch = yaw + shortestAngle(rawYaw - yaw);
            float xAfterYaw = (float) Math.cos(yawForPitch) * point[0]
                    + (float) Math.sin(yawForPitch) * point[2];
            float zAfterYaw = -(float) Math.sin(yawForPitch) * point[0]
                    + (float) Math.cos(yawForPitch) * point[2];
            // xAfterYaw is intentionally not used beyond solving the yaw; it documents the basis.
            float rawPitch = (float) Math.atan2(point[1], zAfterYaw);
            targetYaw = yaw + shortestAngle(rawYaw - yaw);
            targetPitch = clamp(rawPitch, -1.20f, 1.20f);
            targetDistance = Math.min(distance, 2.55f);
            selectedKey = city.key();
            cityBufferDirty = true;
            yawVelocity = 0f;
            pitchVelocity = 0f;
            lastInteractionMillis = SystemClock.elapsedRealtime();
        }
    }

    public void resetView() {
        synchronized (stateLock) {
            targetYaw = PI / 2f;
            targetPitch = -0.12f;
            targetDistance = 6.32f;
            selectedKey = null;
            cityBufferDirty = true;
            yawVelocity = 0f;
            pitchVelocity = 0f;
            lastInteractionMillis = SystemClock.elapsedRealtime();
        }
    }

    public void setAutoOrbit(boolean enabled) {
        synchronized (stateLock) {
            autoOrbit = enabled;
            lastInteractionMillis = SystemClock.elapsedRealtime();
        }
    }

    public void setCloudsEnabled(boolean enabled) {
        synchronized (stateLock) {
            cloudsEnabled = enabled;
        }
    }

    public void setMarkersVisible(boolean enabled) {
        synchronized (stateLock) {
            markersVisible = enabled;
        }
    }

    public void setNightLightsEnabled(boolean enabled) {
        synchronized (stateLock) {
            nightLightsEnabled = enabled;
        }
    }

    /** Returns the nearest visible marker in tap range, or null for ordinary globe drags. */
    public City pickCity(float screenX, float screenY, int width, int height) {
        if (width <= 0 || height <= 0) return null;
        float localYaw;
        float localPitch;
        float localDistance;
        boolean showMarkers;
        synchronized (stateLock) {
            localYaw = yaw;
            localPitch = pitch;
            localDistance = distance;
            showMarkers = markersVisible;
        }
        if (!showMarkers) return null;
        float focal = 1f / (float) Math.tan(Math.toRadians(38f) / 2f);
        float bestDistance = 28f * density;
        City closest = null;
        for (City city : cities) {
            float[] p = cityPoint(city);
            float x1 = (float) Math.cos(localYaw) * p[0] + (float) Math.sin(localYaw) * p[2];
            float z1 = -(float) Math.sin(localYaw) * p[0] + (float) Math.cos(localYaw) * p[2];
            float y2 = (float) Math.cos(localPitch) * p[1] - (float) Math.sin(localPitch) * z1;
            float z2 = (float) Math.sin(localPitch) * p[1] + (float) Math.cos(localPitch) * z1;
            // Back-side points are hidden by the depth buffer and must not be touch targets.
            if (z2 <= 0.015f) continue;
            float perspective = localDistance - z2;
            if (perspective <= 0.1f) continue;
            float ndcX = (x1 * focal / aspect) / perspective;
            float ndcY = (y2 * focal) / perspective;
            if (Math.abs(ndcX) > 1.15f || Math.abs(ndcY) > 1.15f) continue;
            float cityX = (ndcX * 0.5f + 0.5f) * width;
            float cityY = (0.5f - ndcY * 0.5f) * height;
            float dx = screenX - cityX;
            float dy = screenY - cityY;
            float hitDistance = (float) Math.sqrt(dx * dx + dy * dy);
            if (hitDistance < bestDistance) {
                bestDistance = hitDistance;
                closest = city;
            }
        }
        return closest;
    }

    private static float[] cityPoint(City city) {
        float latitude = (float) Math.toRadians(city.latitude);
        float longitude = (float) Math.toRadians(city.longitude);
        // u=(longitude+180)/360 exactly matches the equirectangular surface texture.
        float theta = longitude + PI;
        float cosLatitude = (float) Math.cos(latitude);
        return new float[]{
                cosLatitude * (float) Math.cos(theta),
                (float) Math.sin(latitude),
                cosLatitude * (float) Math.sin(theta)
        };
    }

    private static float shortestAngle(float radians) {
        while (radians > PI) radians -= PI * 2f;
        while (radians < -PI) radians += PI * 2f;
        return radians;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Transform {
        final float yaw;
        final float pitch;
        final float distance;
        final boolean clouds;
        final boolean nightLights;
        final String selected;

        Transform(float yaw, float pitch, float distance, boolean clouds,
                  boolean nightLights, String selected) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.distance = distance;
            this.clouds = clouds;
            this.nightLights = nightLights;
            this.selected = selected;
        }
    }

    private static final String EARTH_VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec2 aUv;
            varying vec2 vUv;
            varying vec3 vWorldPosition;
            varying vec3 vNormal;
            void main() {
                vec4 world = uModel * vec4(aPosition, 1.0);
                vWorldPosition = world.xyz;
                vNormal = normalize((uModel * vec4(aNormal, 0.0)).xyz);
                vUv = aUv;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
            """;

    private static final String EARTH_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uNightMap;
            uniform sampler2D uCloudMap;
            uniform sampler2D uDayMap;
            uniform vec3 uSunDir;
            uniform vec3 uCamera;
            uniform float uTime;
            uniform float uClouds;
            uniform float uLightStrength;
            varying vec2 vUv;
            varying vec3 vWorldPosition;
            varying vec3 vNormal;
            void main() {
                vec2 uv = vec2(fract(vUv.x), vUv.y);
                vec3 nightTex = texture2D(uNightMap, uv).rgb;
                vec3 dayTex = texture2D(uDayMap, uv).rgb;

                // Night side: deep space-dark ocean plus satellite city lights.
                float luminance = dot(nightTex, vec3(0.2126, 0.7152, 0.0722));
                vec3 nightColor = vec3(0.0015, 0.007, 0.026)
                        + nightTex * vec3(0.15, 0.22, 0.42);
                float cityLights = smoothstep(0.022, 0.66, luminance);
                float lightBoost = (0.26 + 1.42 * pow(cityLights, 1.35)) * uLightStrength;

                // Day side: real satellite colour with a light cinematic grade.
                vec3 dayColor = dayTex * vec3(1.045, 1.03, 1.0) + vec3(0.012, 0.016, 0.03);
                float oceanMask = 1.0 - smoothstep(0.05, 0.42, luminance);
                dayColor += vec3(0.018, 0.05, 0.085) * oceanMask;

                float sunDot = dot(normalize(vNormal), normalize(uSunDir));
                float dayFactor = smoothstep(-0.10, 0.18, sunDot);
                float terminator = smoothstep(-0.24, -0.04, sunDot)
                        * (1.0 - smoothstep(-0.04, 0.16, sunDot));

                vec3 color = mix(nightColor, dayColor, dayFactor);
                // City lights fade out over the daylit side.
                color += nightTex * vec3(0.32, 0.58, 1.16) * lightBoost
                        * (1.0 - dayFactor * 0.96);
                // Warm sunset/sunrise band straddling the terminator.
                color += vec3(0.85, 0.42, 0.18) * terminator * 0.16;

                // Clouds: bright white in daylight, faintly lit on the night side.
                float cloudSample = texture2D(uCloudMap,
                        vec2(fract(vUv.x + uTime * 0.00085), vUv.y)).r;
                float cloud = smoothstep(0.38, 0.86, cloudSample);
                vec3 cloudTint = mix(vec3(0.045, 0.12, 0.25), vec3(1.0, 0.99, 0.97), dayFactor);
                color = mix(color, cloudTint, cloud * uClouds * 0.82);
                color += vec3(0.85, 0.42, 0.18) * terminator * cloud * uClouds * 0.10;

                vec3 viewDirection = normalize(uCamera - vWorldPosition);
                float fresnel = pow(1.0 - max(dot(normalize(vNormal), viewDirection), 0.0), 3.1);
                vec3 rim = mix(vec3(0.008, 0.075, 0.26), vec3(0.25, 0.55, 1.0), dayFactor);
                color += rim * fresnel * 0.72;
                gl_FragColor = vec4(color, 1.0);
            }
            """;

    private static final String ATMOSPHERE_VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            varying vec3 vWorldPosition;
            varying vec3 vNormal;
            void main() {
                vec3 expanded = aPosition * 1.035;
                vec4 world = uModel * vec4(expanded, 1.0);
                vWorldPosition = world.xyz;
                vNormal = normalize((uModel * vec4(aPosition, 0.0)).xyz);
                gl_Position = uMvp * vec4(expanded, 1.0);
            }
            """;

    private static final String ATMOSPHERE_FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec3 uCamera;
            uniform vec3 uSunDir;
            varying vec3 vWorldPosition;
            varying vec3 vNormal;
            void main() {
                vec3 viewDirection = normalize(uCamera - vWorldPosition);
                float rim = pow(1.0 - max(dot(normalize(vNormal), viewDirection), 0.0), 2.5);
                // The limb glows a little more on the sunlit side.
                float dayBoost = 0.72 + 0.55 * max(dot(normalize(vNormal), uSunDir), 0.0);
                float alpha = rim * 0.54 * dayBoost;
                gl_FragColor = vec4(vec3(0.025, 0.29, 0.92) * rim * dayBoost, alpha);
            }
            """;

    private static final String CITY_VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform float uDensity;
            uniform float uZoom;
            attribute vec3 aPosition;
            attribute vec3 aColor;
            attribute float aSelected;
            varying vec3 vColor;
            varying float vSelected;
            void main() {
                vColor = aColor;
                vSelected = aSelected;
                gl_Position = uMvp * vec4(aPosition, 1.0);
                gl_PointSize = uDensity * (2.75 + uZoom * 0.85 + aSelected * 5.2);
            }
            """;

    private static final String CITY_FRAGMENT_SHADER = """
            precision mediump float;
            uniform float uTime;
            varying vec3 vColor;
            varying float vSelected;
            void main() {
                vec2 point = gl_PointCoord * 2.0 - 1.0;
                float radius = length(point);
                if (radius > 1.0) discard;
                // Crisp white-hot core, a clean ring, and a soft outer glow.
                float core = 1.0 - smoothstep(0.0, 0.30, radius);
                float ring = (1.0 - smoothstep(0.58, 0.72, radius))
                        * (1.0 - smoothstep(0.72, 0.86, radius));
                float halo = pow(max(1.0 - radius, 0.0), 2.7);
                float pulse = 0.92 + vSelected * (0.24 + 0.18 * sin(uTime * 4.0));
                vec3 tint = mix(vec3(0.86, 0.97, 1.0), vColor, 0.75);
                float alpha = (core * 0.95 + ring * 0.72 + halo * 0.40) * pulse;
                gl_FragColor = vec4(tint * (core * 1.65 + ring * 0.95 + halo * 0.50) * pulse, alpha);
            }
            """;

    private static final String STAR_VERTEX_SHADER = """
            uniform float uTime;
            attribute vec2 aPosition;
            attribute float aSize;
            attribute float aPhase;
            varying vec3 vTint;
            varying float vAlpha;
            void main() {
                float twinkle = 0.76 + 0.24 * sin(uTime * (0.6 + fract(aPhase)) + aPhase);
                vTint = mix(vec3(0.52, 0.68, 1.0), vec3(1.0, 0.83, 0.61), fract(aPhase * 0.31));
                vAlpha = 0.35 + 0.45 * twinkle;
                gl_PointSize = aSize * twinkle;
                gl_Position = vec4(aPosition, 0.95, 1.0);
            }
            """;

    private static final String STAR_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec3 vTint;
            varying float vAlpha;
            void main() {
                vec2 point = gl_PointCoord * 2.0 - 1.0;
                float distance = dot(point, point);
                if (distance > 1.0) discard;
                float glow = pow(1.0 - distance, 2.0);
                gl_FragColor = vec4(vTint * glow, glow * vAlpha);
            }
            """;
}
