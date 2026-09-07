package com.nocturne.earthweather;

import java.util.Locale;

/** A current-conditions reading returned by Open-Meteo. Values are always stored in SI units. */
public final class WeatherSnapshot {
    public final double temperatureC;
    public final double apparentTemperatureC;
    public final double humidityPercent;
    public final double windKph;
    public final double precipitationMm;
    public final int weatherCode;
    public final boolean isDay;
    public final String providerZoneId;
    public final long receivedAtMillis;

    public WeatherSnapshot(
            double temperatureC,
            double apparentTemperatureC,
            double humidityPercent,
            double windKph,
            double precipitationMm,
            int weatherCode,
            boolean isDay,
            String providerZoneId,
            long receivedAtMillis) {
        this.temperatureC = temperatureC;
        this.apparentTemperatureC = apparentTemperatureC;
        this.humidityPercent = humidityPercent;
        this.windKph = windKph;
        this.precipitationMm = precipitationMm;
        this.weatherCode = weatherCode;
        this.isDay = isDay;
        this.providerZoneId = providerZoneId;
        this.receivedAtMillis = receivedAtMillis;
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
