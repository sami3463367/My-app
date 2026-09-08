package com.nocturne.earthweather;

import java.util.Locale;

/** A current-conditions reading returned by Open-Meteo. Values are always stored in SI units. */
public final class WeatherSnapshot {
    public final double temperatureC;
    public final double apparentTemperatureC;
    public final double humidityPercent;
    public final double windKph;
    public final double windDirectionDeg;
    public final double windGustsKph;
    public final double pressureHpa;
    public final double precipitationMm;
    public final double rainChancePercent;
    public final double uvIndex;
    public final int weatherCode;
    public final boolean isDay;
    /** ISO-8601 local time string (e.g. "2026-09-08T05:12") or null when unavailable. */
    public final String sunriseIso;
    public final String sunsetIso;
    public final String providerZoneId;
    public final long receivedAtMillis;

    public WeatherSnapshot(
            double temperatureC,
            double apparentTemperatureC,
            double humidityPercent,
            double windKph,
            double windDirectionDeg,
            double windGustsKph,
            double pressureHpa,
            double precipitationMm,
            double rainChancePercent,
            double uvIndex,
            int weatherCode,
            boolean isDay,
            String sunriseIso,
            String sunsetIso,
            String providerZoneId,
            long receivedAtMillis) {
        this.temperatureC = temperatureC;
        this.apparentTemperatureC = apparentTemperatureC;
        this.humidityPercent = humidityPercent;
        this.windKph = windKph;
        this.windDirectionDeg = windDirectionDeg;
        this.windGustsKph = windGustsKph;
        this.pressureHpa = pressureHpa;
        this.precipitationMm = precipitationMm;
        this.rainChancePercent = rainChancePercent;
        this.uvIndex = uvIndex;
        this.weatherCode = weatherCode;
        this.isDay = isDay;
        this.sunriseIso = sunriseIso;
        this.sunsetIso = sunsetIso;
        this.providerZoneId = providerZoneId;
        this.receivedAtMillis = receivedAtMillis;
    }

    public boolean hasWind() { return !Double.isNaN(windKph); }
    public boolean hasHumidity() { return !Double.isNaN(humidityPercent); }
    public boolean hasRainChance() { return !Double.isNaN(rainChancePercent); }
    public boolean hasUv() { return !Double.isNaN(uvIndex); }
    public boolean hasPressure() { return !Double.isNaN(pressureHpa); }
    public boolean hasSun() { return sunriseIso != null && sunsetIso != null; }

    /** Compass label for the wind origin, e.g. "NW". */
    public String windDirectionLabel() {
        if (Double.isNaN(windDirectionDeg)) return "—";
        String[] points = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = Math.round((float) (windDirectionDeg / 22.5f)) % 16;
        return points[index];
    }

    /** Arrow glyph pointing where the wind is going (index = compass point of origin). */
    public String windArrow() {
        if (Double.isNaN(windDirectionDeg)) return "";
        String[] arrows = {"↓", "↙", "←", "↖", "↑", "↗", "→", "↘"};
        int index = Math.round((float) (windDirectionDeg / 45f)) % 8;
        return arrows[index < 0 ? index + 8 : index];
    }

    public String uvLabel() {
        if (!hasUv()) return "—";
        if (uvIndex < 3d) return "LOW";
        if (uvIndex < 6d) return "MODERATE";
        if (uvIndex < 8d) return "HIGH";
        if (uvIndex < 11d) return "VERY HIGH";
        return "EXTREME";
    }

    /** Color key for the UV rating so the UI can tint it: 0 low, 1 moderate, 2 high, 3 very, 4 extreme. */
    public int uvRating() {
        if (!hasUv()) return -1;
        if (uvIndex < 3d) return 0;
        if (uvIndex < 6d) return 1;
        if (uvIndex < 8d) return 2;
        if (uvIndex < 11d) return 3;
        return 4;
    }

    /** "HH:mm" from an Open-Meteo local ISO string, or "—" when unavailable. */
    public static String clockPart(String iso) {
        if (iso == null) return "—";
        int t = iso.indexOf('T');
        if (t < 0 || iso.length() < t + 6) return "—";
        return iso.substring(t + 1, t + 6);
    }

    public String condition() {
        switch (weatherCode) {
            case 0: return "Clear sky";
            case 1: return "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45:
            case 48: return "Fog";
            case 51: return "Light drizzle";
            case 53: return "Drizzle";
            case 55: return "Heavy drizzle";
            case 56:
            case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66:
            case 67: return "Freezing rain";
            case 71: return "Light snow";
            case 73: return "Snow";
            case 75: return "Heavy snow";
            case 77: return "Snow grains";
            case 80: return "Rain showers";
            case 81: return "Rain showers";
            case 82: return "Violent showers";
            case 85: return "Snow showers";
            case 86: return "Heavy snow showers";
            case 95: return "Thunderstorm";
            case 96:
            case 99: return "Storm with hail";
            default: return "Current conditions";
        }
    }

    public String temperature(boolean fahrenheit) {
        double value = fahrenheit ? temperatureC * 9d / 5d + 32d : temperatureC;
        return String.format(Locale.getDefault(), "%.0f°%s", value, fahrenheit ? "F" : "C");
    }

    public String apparentTemperature(boolean fahrenheit) {
        double value = fahrenheit ? apparentTemperatureC * 9d / 5d + 32d : apparentTemperatureC;
        return String.format(Locale.getDefault(), "%.0f°%s", value, fahrenheit ? "F" : "C");
    }
}
