package org.schabi.newpipe.extractor.utils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Headers;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Date;
import java.util.TimeZone;

public final class SubtitleLastModifiedUtils {

    private static final OkHttpClient client = new OkHttpClient();

    // Standard HTTP date format: "EEE, dd MMM yyyy HH:mm:ss zzz"
    private static final SimpleDateFormat HTTP_DATE_FORMAT = createHttpDateFormat();

    private SubtitleLastModifiedUtils() { /* no instance */ }

    private static SimpleDateFormat createHttpDateFormat() {
        SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        return df;
    }

    /**
     * Parse the Last-Modified header into a Date.
     * Returns null if parsing fails or header is missing.
     */
    public static Date parseLastModified(String lastModifiedHeader) {
        if (lastModifiedHeader == null) return null;
        try {
            synchronized (HTTP_DATE_FORMAT) {
                return HTTP_DATE_FORMAT.parse(lastModifiedHeader);
            }
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Get "Last-Modified" header from a remote URL.
     * This does a HEAD request to avoid downloading the whole subtitle file.
     */
    public static String getLastModified(String url) {
        Request request = new Request.Builder()
                .url(url)
                .head()
                .build();

        try (Response response = client.newCall(request).execute()) {
            Headers headers = response.headers();
            return headers.get("Last-Modified");
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Send a conditional GET request with If-Modified-Since.
     * Returns:
     *   - 304 → null (means "not modified")
     *   - 200 → response body as String (subtitle content)
     *   - other → null
     */
    public static String conditionalGet(String url, String lastModifiedHeader) {
        Request.Builder builder = new Request.Builder().url(url);

        if (lastModifiedHeader != null) {
            builder.addHeader("If-Modified-Since", lastModifiedHeader);
        }

        Request request = builder.build();

        try (Response response = client.newCall(request).execute()) {

            if (response.code() == 304) {
                // Not modified, no need to download again
                return null;
            }

            if (response.code() == 200 && response.body() != null) {
                return response.body().string();
            }

            // Unexpected status → do nothing
            return null;

        } catch (IOException e) {
            return null;
        }
    }
}
