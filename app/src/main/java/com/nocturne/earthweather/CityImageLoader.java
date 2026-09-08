package com.nocturne.earthweather;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads a real photograph of the selected city from Wikipedia (key-free REST API) and hands a
 * decoded bitmap back on the main thread. Every failure mode degrades to null so the UI can show
 * a styled placeholder instead of a broken image.
 */
public final class CityImageLoader {
    public interface Callback {
        /** @param cityKey the key the request was made for; ignore if it is not the active city. */
        void onResult(String cityKey, Bitmap bitmap);
    }

    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Bitmap> MEMO = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();
    /** Small LRU for decoded bitmaps so re-selecting a city is instant. */
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4 << 20) {
        @Override protected int size(String key, Bitmap value) {
            return value == null ? 0 : value.getByteCount();
        }
    };

    private CityImageLoader() { }

    public static void load(City city, Callback callback) {
        String key = city.key();
        Bitmap memo = MEMO.get(key);
        if (memo != null) {
            callback.onResult(key, memo);
            return;
        }
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            callback.onResult(key, cached);
            return;
        }
        if (fold(city.name).isEmpty() || city.name.equalsIgnoreCase("Your location")
                || fold(city.country).isEmpty() || city.country.equals("Current device")) {
            callback.onResult(key, null);
            return;
        }
        if (IN_FLIGHT.putIfAbsent(key, Boolean.TRUE) != null) {
            // Already loading; the first request's callback memoizes the result.
            return;
        }

        NETWORK.execute(() -> {
            try {
                loadInner(key, city, callback);
            } finally {
                IN_FLIGHT.remove(key);
            }
        });
    }

    private static void loadInner(String key, City city, Callback callback) {
        Bitmap result = null;
        try {
            String title = resolveTitle(city);
            if (title != null) {
                String imageUrl = thumbnailUrl(title);
                if (imageUrl != null) result = downloadImage(imageUrl);
            }
        } catch (Exception ignored) {
            result = null;
        }
        if (result != null) {
            MEMO.put(key, result);
            CACHE.put(key, result);
        }
        MAIN.post(() -> callback.onResult(key, result));
    }

    /** Resolves the Wikipedia page title that best matches "city, country". */
    private static String resolveTitle(City city) throws IOException {
        String query = city.name + " " + city.country;
        String url = "https://en.wikipedia.org/w/api.php?action=opensearch&limit=6&redirects=resolve"
                + "&format=json&search=" + Uri.encode(query);
        String body = httpGet(url);
        if (body == null) return null;
        JSONArray json = new JSONArray(body);
        if (json.length() < 2) return null;
        JSONArray titles = json.getJSONArray(1);
        String foldedCity = fold(city.name);
        String fallback = null;
        for (int i = 0; i < titles.length(); i++) {
            String title = titles.optString(i, "");
            if (title.isEmpty()) continue;
            String foldedTitle = fold(title);
            if (foldedTitle.startsWith(foldedCity) || foldedTitle.contains(foldedCity)) {
                // Prefer the shortest matching title (the primary city article, not a
                // "X (disambiguation)" or long variant).
                if (fallback == null) fallback = title;
                return title;
            }
        }
        return fallback;
    }

    /**
     * Fetches the lead image of the article, requesting an ~800px render from the Commons
     * thumbnail service when the summary offers the default 320px thumbnail.
     */
    private static String thumbnailUrl(String title) throws IOException {
        String url = "https://en.wikipedia.org/api/rest_v1/page/summary/"
                + Uri.encode(title.replace(' ', '_'));
        String body = httpGet(url);
        if (body == null) return null;
        JSONObject json = new JSONObject(body);
        String source = null;
        JSONObject thumbnail = json.optJSONObject("thumbnail");
        if (thumbnail != null) source = thumbnail.optString("source", null);
        if (source == null || source.isEmpty()) {
            JSONObject original = json.optJSONObject("originalimage");
            if (original != null) source = original.optString("source", null);
        }
        if (source == null || source.isEmpty()) return null;
        if (source.contains("/320px-")) {
            source = source.replace("/320px-", "/800px-");
        }
        return source;
    }

    private static Bitmap downloadImage(String url) throws IOException {
        byte[] bytes = httpDownload(url, 1_600_000);
        if (bytes == null || bytes.length < 256) return null;
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, probe);
        if (probe.outWidth <= 0 || probe.outHeight <= 0) return null;
        int sample = 1;
        while (probe.outWidth / (sample * 2) >= 720) sample *= 2;
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sample;
        decode.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decode);
    }

    private static String httpGet(String url) throws IOException {
        byte[] bytes = httpDownload(url, 256_000);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] httpDownload(String url, int maxBytes) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(9_000);
            connection.setUseCaches(true);
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("User-Agent", "NocturneEarth/1.1 (Android)");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return null;
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16384];
                int total = 0;
                int count;
                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    if (total > maxBytes) return null;
                    out.write(buffer, 0, count);
                }
                return out.toByteArray();
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String fold(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);
    }
}
