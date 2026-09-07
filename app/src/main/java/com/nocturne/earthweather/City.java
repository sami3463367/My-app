package com.nocturne.earthweather;

import java.util.Locale;

/** Immutable place used both by the renderer and the live-weather client. */
public final class City {
    public final String name;
    public final String country;
    public final double latitude;
    public final double longitude;
    public final String zoneId;

    public City(String name, String country, double latitude, double longitude, String zoneId) {
        this.name = name;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
        this.zoneId = zoneId;
    }

    public String key() {
        return String.format(Locale.US, "%s|%.4f|%.4f", name, latitude, longitude);
    }

    public String displayName() {
        return name + ", " + country;
    }
}
