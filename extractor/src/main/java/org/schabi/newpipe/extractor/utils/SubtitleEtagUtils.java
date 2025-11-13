package org.schabi.newpipe.extractor.utils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;

/**
 * Utility class for fetching and comparing ETag headers from HTTP resources.
 *
 * This helper is meant for subtitle caching (e.g., detecting if the remote subtitle file
 * has changed since last download).
 *
 * Usage example:
 * String etag = SubtitleEtagUtils.getEtag(subtitleUrl);
 * boolean changed = SubtitleEtagUtils.isEtagChanged(subtitleUrl, cachedEtag);
 */
public final class SubtitleEtagUtils {

    // Reuse a single OkHttpClient instance for efficiency
    private static final OkHttpClient client = new OkHttpClient();

    // Private constructor to prevent instantiation
    private SubtitleEtagUtils() {}

    /**
     * Fetches the ETag header from a given URL (using a HEAD request).
     *
     * @param url The remote resource URL
     * @return The ETag value as a string, or null if unavailable or an error occurs
     */
    public static String getEtag(String url) {
        Request request = new Request.Builder()
                .url(url)
                .head()  // Use HEAD request to only fetch headers
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.header("ETag");
            } else {
                System.err.println("ETag request failed: " + response.code() + " for URL: " + url);
            }
        } catch (IOException e) {
            System.err.println("Error fetching ETag for URL: " + url);
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Checks if the ETag of a given URL differs from a previously stored ETag.
     *
     * @param url The remote resource URL
     * @param oldEtag The previously stored ETag value
     * @return true if the new ETag differs or is unavailable; false if unchanged
     */
    public static boolean isEtagChanged(String url, String oldEtag) {
        String newEtag = getEtag(url);

        // If no ETag is returned, assume changed (to force refresh)
        if (newEtag == null || oldEtag == null) {
            return true;
        }

        return !newEtag.equals(oldEtag);
    }
}
