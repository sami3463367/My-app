package com.nocturne.earthweather;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Curated set of real 24/7 live broadcasts on YouTube, keyed by the country names used in
 * {@link CityCatalog}. Entries use stable channel "/live" URLs (never rotating video IDs), so a
 * link keeps working as long as the channel exists. Channels are labelled honestly: only dedicated
 * weather services are marked as weather channels; the rest are 24/7 news channels that carry
 * continuous live weather coverage. Every other country falls back to a live YouTube search for
 * "<country> live weather", which always surfaces currently-airing streams.
 */
public final class LiveWeatherCatalog {
    public static final class Entry {
        public final String channelLabel;
        public final String liveUrl;
        public final boolean dedicatedWeather;

        Entry(String channelLabel, String liveUrl, boolean dedicatedWeather) {
            this.channelLabel = channelLabel;
            this.liveUrl = liveUrl;
            this.dedicatedWeather = dedicatedWeather;
        }
    }

    private static final Map<String, Entry> CHANNELS = new HashMap<>();

    static {
        add("United States", "WEATHERNATION · 24/7 WEATHER", "https://www.youtube.com/@WeatherNation/live", true);
        add("United Kingdom", "SKY NEWS · 24/7 LIVE", "https://www.youtube.com/@SkyNews/live", false);
        add("Australia", "ABC NEWS AUSTRALIA · 24/7 LIVE", "https://www.youtube.com/@abcnewsaustralia/live", false);
        add("Germany", "DW NEWS · 24/7 LIVE", "https://www.youtube.com/@dwnews/live", false);
        add("France", "FRANCE 24 · 24/7 LIVE", "https://www.youtube.com/@france24/live", false);
        add("India", "INDIA TODAY · 24/7 LIVE", "https://www.youtube.com/@IndiaToday/live", false);
        add("China", "CGTN · 24/7 LIVE", "https://www.youtube.com/@CGTNOfficial/live", false);
        add("Hong Kong", "CGTN · 24/7 LIVE", "https://www.youtube.com/@CGTNOfficial/live", false);
        add("Macau", "CGTN · 24/7 LIVE", "https://www.youtube.com/@CGTNOfficial/live", false);
        add("Japan", "NHK WORLD · 24/7 LIVE", "https://www.youtube.com/@NHKworldpremium/live", false);
        add("Italy", "RAI NEWS 24 · LIVE", "https://www.youtube.com/@rainewstv/live", false);
        add("Spain", "CANAL 24H · LIVE", "https://www.youtube.com/@canal24h/live", false);
        add("South Korea", "YTN 24 · LIVE", "https://www.youtube.com/@ytn24/live", false);
        add("Russia", "RUSSIA 24 · LIVE", "https://www.youtube.com/@russia24tv/live", false);
        add("Türkiye", "TRT WORLD · 24/7 LIVE", "https://www.youtube.com/@trtworld/live", false);
        add("Israel", "i24NEWS · 24/7 LIVE", "https://www.youtube.com/@i24NEWS/live", false);
        add("Singapore", "CHANNEL NEWSASIA · LIVE", "https://www.youtube.com/@cna/live", false);
        add("South Africa", "E.NCA WEATHER · LIVE", "https://www.youtube.com/@encaweather6508/live", true);
        add("Nigeria", "E.NCA WEATHER · LIVE", "https://www.youtube.com/@encaweather6508/live", true);
        add("Kenya", "E.NCA WEATHER · LIVE", "https://www.youtube.com/@encaweather6508/live", true);
        add("Egypt", "AL JAZEERA ARABIA · 24/7 LIVE", "https://www.youtube.com/@AljazeeraArabic/live", false);
        add("United Arab Emirates", "AL JAZEERA · 24/7 LIVE", "https://www.youtube.com/@aljazeera/live", false);
        add("Saudi Arabia", "AL JAZEERA · 24/7 LIVE", "https://www.youtube.com/@aljazeera/live", false);
        add("Qatar", "AL JAZEERA · 24/7 LIVE", "https://www.youtube.com/@aljazeera/live", false);
        add("Kuwait", "AL JAZEERA · 24/7 LIVE", "https://www.youtube.com/@aljazeera/live", false);
        add("Bahrain", "AL JAZEERA · 24/7 LIVE", "https://www.youtube.com/@aljazeera/live", false);
        add("Oman", "AL JAZEERA · 24/7 LIVE", "https://www.youtube.com/@aljazeera/live", false);
        add("Iraq", "AL JAZEERA · 24/7 LIVE", "https://www.youtube.com/@aljazeera/live", false);
        add("Iran", "AL JAZEERA · 24/7 LIVE", "https://www.youtube.com/@aljazeera/live", false);
        add("New Zealand", "ABC NEWS AUSTRALIA · 24/7 LIVE", "https://www.youtube.com/@abcnewsaustralia/live", false);
    }

    private static void add(String country, String label, String url, boolean dedicatedWeather) {
        CHANNELS.put(fold(country), new Entry(label, url, dedicatedWeather));
    }

    private static String fold(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** Best live broadcast for the country, or null when nothing is curated. */
    public static Entry entryFor(String country) {
        return CHANNELS.get(fold(country));
    }

    /** Always-current YouTube live search used when no curated channel exists. */
    public static String liveSearchUrl(String country) {
        String query = "Current device".equals(country)
                ? "live weather"
                : country + " live weather";
        // sp=EgIQAg%3D%3D is YouTube's "is live" filter.
        return "https://www.youtube.com/results?search_query="
                + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8)
                + "&sp=EgIQAg%3D%3D";
    }

    public static String labelFor(String country) {
        Entry entry = entryFor(country);
        if (entry != null) return entry.channelLabel;
        String shortCountry = country == null ? "" : country;
        return "LIVE WEATHER · " + shortCountry.toUpperCase(Locale.getDefault()) + " ON YOUTUBE";
    }

    public static String searchLabelFor(String country) {
        return "LIVE WEATHER SEARCH · "
                + (country == null ? "" : country.toUpperCase(Locale.getDefault())) + " · YOUTUBE";
    }
}
