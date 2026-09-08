package com.nocturne.earthweather;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small, key-free client for Open-Meteo's forecast endpoint. Requests happen only for the place
 * the user explicitly selected. A short in-memory cache prevents accidental repeat requests while
 * allowing the refresh control to get a new reading.
 */
public final class WeatherRepository {
    private static final String ENDPOINT = "https://api.open-meteo.com/v1/forecast";
    private static final long CACHE_MAX_AGE_MS = 8L * 60L * 1000L;
    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(2);
    private static final Map<String, WeatherSnapshot> CACHE = new ConcurrentHashMap<>();

    public interface Callback {
        void onSuccess(WeatherSnapshot snapshot, boolean fromCache);
        void onFailure(String message);
    }

    private WeatherRepository() { }

    public static void fetch(City city, boolean forceRefresh, Callback callback) {
        WeatherSnapshot cached = CACHE.get(city.key());
        if (!forceRefresh && cached != null
                && System.currentTimeMillis() - cached.receivedAtMillis < CACHE_MAX_AGE_MS) {
            callback.onSuccess(cached, true);
            return;
        }

        NETWORK.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String url = Uri.parse(ENDPOINT).buildUpon()
                        .appendQueryParameter("latitude", String.format(Locale.US, "%.5f", city.latitude))
                        .appendQueryParameter("longitude", String.format(Locale.US, "%.5f", city.longitude))
                        .appendQueryParameter("current",
                                "temperature_2m,relative_humidity_2m,apparent_temperature,"
                                        + "precipitation,weather_code,is_day,wind_speed_10m,"
                                        + "wind_direction_10m,wind_gusts_10m,surface_pressure")
                        .appendQueryParameter("hourly", "uv_index,precipitation_probability")
                        .appendQueryParameter("daily", "sunrise,sunset")
                        .appendQueryParameter("forecast_days", "1")
                        .appendQueryParameter("timezone", "auto")
                        .build().toString();
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(12_000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "NocturneEarth/1.1 (Android)");

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    throw new IOException("Weather service returned " + responseCode);
                }
                String body = readAll(connection.getInputStream());
                JSONObject response = new JSONObject(body);
                JSONObject current = response.getJSONObject("current");
                String zone = response.optString("timezone", city.zoneId);
                String currentTime = current.optString("time", "");

                double uvIndex = Double.NaN;
                double rainChance = Double.NaN;
                JSONObject hourly = response.optJSONObject("hourly");
                if (hourly != null) {
                    int hourIndex = currentHourIndex(hourly.optJSONArray("time"), currentTime);
                    if (hourIndex >= 0) {
                        JSONArray uv = hourly.optJSONArray("uv_index");
                        if (uv != null && hourIndex < uv.length()) {
                            uvIndex = uv.isNull(hourIndex) ? Double.NaN : uv.getDouble(hourIndex);
                        }
                        JSONArray chance = hourly.optJSONArray("precipitation_probability");
                        if (chance != null && hourIndex < chance.length()) {
                            rainChance = chance.isNull(hourIndex)
                                    ? Double.NaN : chance.getDouble(hourIndex);
                        }
                    }
                }

                String sunrise = null;
                String sunset = null;
                JSONObject daily = response.optJSONObject("daily");
                if (daily != null && daily.length() > 0) {
                    sunrise = optIso(daily, "sunrise");
                    sunset = optIso(daily, "sunset");
                }

                WeatherSnapshot snapshot = new WeatherSnapshot(
                        current.optDouble("temperature_2m", Double.NaN),
                        current.optDouble("apparent_temperature", Double.NaN),
                        current.optDouble("relative_humidity_2m", Double.NaN),
                        current.optDouble("wind_speed_10m", Double.NaN),
                        current.optDouble("wind_direction_10m", Double.NaN),
                        current.optDouble("wind_gusts_10m", Double.NaN),
                        current.optDouble("surface_pressure", Double.NaN),
                        current.optDouble("precipitation", 0d),
                        rainChance,
                        uvIndex,
                        current.optInt("weather_code", -1),
                        current.optInt("is_day", 0) == 1,
                        sunrise,
                        sunset,
                        zone,
                        System.currentTimeMillis());
                CACHE.put(city.key(), snapshot);
                callback.onSuccess(snapshot, false);
            } catch (Exception error) {
                callback.onFailure("Live conditions are unavailable right now.");
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    /** Index of the current local hour inside the hourly array, matched by wall-clock prefix. */
    private static int currentHourIndex(JSONArray times, String currentTime) {
        if (times == null || currentTime == null || currentTime.length() < 13) return -1;
        // "2026-09-08T21:30" -> "2026-09-08T21"
        String hourPrefix = currentTime.substring(0, 13);
        int exact = indexOf(times, hourPrefix);
        if (exact >= 0) return exact;
        // Fall back to the nearest earlier hour in case the provider truncates differently.
        for (int i = times.length() - 1; i >= 0; i--) {
            String t = times.optString(i, "");
            if (t.length() >= 13 && t.substring(0, 13).compareTo(hourPrefix) <= 0) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOf(JSONArray array, String prefix) {
        for (int i = 0; i < array.length(); i++) {
            String t = array.optString(i, "");
            if (t.length() >= 13 && t.substring(0, 13).equals(prefix)) return i;
        }
        return -1;
    }

    private static String optIso(JSONObject daily, String field) throws org.json.JSONException {
        JSONArray values = daily.optJSONArray(field);
        if (values == null || values.length() == 0 || values.isNull(0)) return null;
        String value = values.getString(0);
        return value == null || value.isEmpty() ? null : value;
    }

    private static String readAll(InputStream stream) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) != -1) body.append(buffer, 0, count);
        }
        return body.toString();
    }
}
