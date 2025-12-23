package org.schabi.newpipe.extractor.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

/**
 * SubtitleDeduplicator.java
 *
 * 1. This file is responsible for checking if the subtitles
 * contain any duplicate entries.
 *   a) If duplicates are found, it performs the following steps:
 *      downloads the subtitle, deduplicates it,
 *      and stores it locally.
 *   b) If no duplicates are found, no action is taken.
 *
 * 2. Core Functions:
 * - checkAndDeduplicate(): Checks for duplicate subtitles
 *   and handles downloading, deduplication, and local storage.
 *
 */

public class SubtitleDeduplicator {
    private static final String TAG = "SubtitleDeduplicator";
    public static final String LOCAL_SUBTITLE_URL_PREFIX = "file://";

    private static String subCacheDir = "subtitle_cache";

    private static File CACHE_DIR = null;

    // CACHE_DIR is /storage/emulated/0/Android/data/<package_name>/cache/{subCacheDir}
    public static void setCacheDirPath(String path) {
        if (true == stringIsNullOrEmpty(path)) {
            return;
        }

        CACHE_DIR = new File(path, subCacheDir);

        if (false == CACHE_DIR.exists()) {
            CACHE_DIR.mkdirs();
        }
    }

    /**
      * Checks if a subtitle contains duplicates,
        deduplicates it if necessary, and caches it locally.
      * @return The local file URL if deduplication and caching succeed,
                otherwise the original URL.
      */
    public static String checkAndDeduplicate(String remoteSubtitleUrl,
                                             MediaFormat format,
                                             SubtitleOrigin currentSubtitleOrigin) {
        String localSubtitleUrl = null;

        // Current subtitle format is TTML
        String downloadedContent = downloadRemoteSubtitleContent(remoteSubtitleUrl,3,1000);
        // High probability of download failure
        if (null == downloadedContent) {
            if (true == theSubtitleWasStoredBefore(
                            remoteSubtitleUrl,
                            format,
                            currentSubtitleOrigin))
            {
                File storedFile = findStoredCacheFile(
                        remoteSubtitleUrl,
                        format,
                        currentSubtitleOrigin
                );
                localSubtitleUrl = buildLocalFileUri(storedFile);
                return localSubtitleUrl;
            } else {
                return remoteSubtitleUrl;
            }
        }

        String finalContent = null;
        SubtitleState currentSubtitleState = SubtitleState.ORIGINAL;

        if (true == containsDuplicatedEntries(downloadedContent)) {
            finalContent = deduplicateContent(downloadedContent);
            currentSubtitleState = SubtitleState.DEDUPLICATED;
        } else {
            finalContent = downloadedContent;
            currentSubtitleState = SubtitleState.ORIGINAL;
        }

        File currentCacheFile = getCacheFile(remoteSubtitleUrl,
                                             format,
                                             currentSubtitleOrigin,
                                             currentSubtitleState);

        localSubtitleUrl = storeItToCacheDir(finalContent,
                                                    remoteSubtitleUrl,
                                                    format,
                                                    currentSubtitleOrigin,
                                                currentCacheFile);
        // failed to store
        if (null == localSubtitleUrl) {
            if (true == theSubtitleWasStoredBefore(
                            remoteSubtitleUrl,
                            format,
                            currentSubtitleOrigin))
            {
                File storedFile = findStoredCacheFile(
                        remoteSubtitleUrl,
                        format,
                        currentSubtitleOrigin
                );
                localSubtitleUrl = buildLocalFileUri(storedFile);
                return localSubtitleUrl;
            } else {
                return remoteSubtitleUrl;
            }
        }

        return localSubtitleUrl;
    }

    private static String downloadRemoteSubtitleContent(String urlStr,
                                                        int maxRetries,
                                                        int initialDelayMillis) {
        Downloader downloader = NewPipe.getDownloader();
        if (downloader == null) {
            System.err.println(TAG + ": Downloader not initialized");
            return null;
        }
        // if auto-translate language subtitle, use the bigger data.
        int delay = initDelayValue(urlStr, initialDelayMillis);
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Map<String, List<String>> headers = new HashMap<>();
                headers.put("Accept", Collections.singletonList("text/*"));
                headers.put("Accept-Language", Collections.singletonList("en-US,en;q=0.9"));
                Response response = downloader.get(urlStr, headers);
                if (response.responseCode() == 200) {
                    return response.responseBody();
                } else {
                    System.err.println(TAG + ": Attempt " + attempt + " failed with status: " + response.responseCode());
                    if (response.responseCode() != 503 && response.responseCode() != 429) {
                        return null;
                    }
                }
            } catch (IOException | ReCaptchaException e) {
                System.err.println(TAG + ": Attempt " + attempt + " failed: " + e.getMessage());
            }
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(delay);
                    delay *= 2;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        System.err.println(TAG + ": Failed to download subtitle after " + maxRetries + " attempts: " + urlStr);
        return null;
    }

    private static boolean isAutoTranslateSubtitleUrl(String urlStr) {
        if (null != checkAutoTranslateLanguage(urlStr)) {
            return true;
        } else {
            return false;
        }
    }

    private static int initDelayValue(String urlStr, int inputDelay) {
        int initDelay = 0;

        if (true == isAutoTranslateSubtitleUrl(urlStr)) {
            initDelay = 6500;
        } else {
            initDelay = inputDelay;
        }

        return initDelay;
    }

    public static boolean containsDuplicateTtmlEntries(File subtitleFile) {
        if (subtitleFile == null || !subtitleFile.exists()) return false;

        try {
            String content = readFileToString(subtitleFile);
            return containsDuplicatedEntries(content);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean containsDuplicatedEntries(String subtitleContent) {
        if (true == stringIsNullOrEmpty(subtitleContent)) {
            return false;
        }

        Matcher matcher = getTtmlMatcher(subtitleContent);

        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String key = getSubtitleKeyOfTtml(matcher);

            if (seen.contains(key)) {
                return true;
            }
            seen.add(key);
        }

        return false;
    }

    private static String readFileToString(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public static String deduplicateTtmlFile(File subtitleFile) {
        if (subtitleFile == null || !subtitleFile.exists()) return "";

        try {
            String content = readFileToString(subtitleFile);
            return deduplicateContent(content);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String deduplicateContent(String subtitleContent) {
        if (true == stringIsNullOrEmpty(subtitleContent)) {
            return subtitleContent;
        }

        Matcher matcher = getTtmlMatcher(subtitleContent);

        Set<String> seen = new HashSet<>();
        StringBuilder result = new StringBuilder();

        int lastIndex = 0;
        while (matcher.find()) {
            result.append(subtitleContent, lastIndex, matcher.start());

            String key = getSubtitleKeyOfTtml(matcher);

            if (!seen.contains(key)) {
                result.append(matcher.group(0));
                seen.add(key);
            }

            lastIndex = matcher.end();
        }

        result.append(subtitleContent.substring(lastIndex));
        return result.toString();
    }

    private static boolean stringIsNullOrEmpty(String inputString) {
        if (null == inputString) {
            return true;
        }

        if (true == inputString.isEmpty()) {
            return true;
        }

        return false;
    }

    private static Pattern defineTtmlSubtitlePattern() {
        return Pattern.compile(
            "<p[^>]*begin=\"([^\"]+)\"[^>]*end=\"([^\"]+)\"[^>]*>(.*?)</p>",
            Pattern.DOTALL
        );
    }

    private static Matcher getTtmlMatcher(String subtitleContent) {
        Pattern pattern = defineTtmlSubtitlePattern();
        return pattern.matcher(subtitleContent);
    }

    private static String getSubtitleKeyOfTtml(Matcher matcher) {
        String begin = matcher.group(1).trim();
        String end = matcher.group(2).trim();
        String content = matcher.group(3).trim().replaceAll("\\s+", " ");
        String key = begin + "|" + end + "|" + content;
        return key;
    }

    private static String buildLocalFileUri(File subtitleCacheFile) {
        String path = LOCAL_SUBTITLE_URL_PREFIX + subtitleCacheFile.getAbsolutePath();

        return path;
    }

    private static String storeItToCacheDir(String subtitleContent,
                                            String subtitleUrl,
                                            MediaFormat format,
                                            SubtitleOrigin currentSubtitleOrigin,
                                            File currentCacheFile) {
        File cacheFile = currentCacheFile;

        String cacheFilePathForExoplayer = buildLocalFileUri(cacheFile);

        if (false == ensureItsParentDirExist(cacheFile)) {
            return null;
        }

        if (null == writeDeduplicatedContentToCachefile(subtitleContent, cacheFile)) {
            return cacheFilePathForExoplayer;
        } else {
            System.err.println(TAG + ": Failed to write cache file: " + cacheFile.getAbsolutePath());
            return null;
        }
    }

    // filename without dir path
    private static String computeFilename(String subtitleUrl,
                                                MediaFormat format,
                                                SubtitleOrigin currentSubtitleOrigin,
                                            SubtitleState currentSubtitleState) {
        String videoId = getVideoId(subtitleUrl);

        String languageCode = resolveSubtitleLanguage(
                subtitleUrl,
                currentSubtitleOrigin
        );

        String filename = buildSubtitleCacheFilename(videoId,
                                                     languageCode,
                                                     currentSubtitleOrigin,
                                                     currentSubtitleState,
                                                     format.getSuffix());

        return filename;
    }

    public static SubtitleOrigin getSubtitleOrigin(boolean autoGenerated,
                                                   boolean autoTranslate) {
        if (true == autoTranslate) {
            return SubtitleOrigin.AUTO_TRANSLATED;
        }
        if (true == autoGenerated) {
            return SubtitleOrigin.AUTO_GENERATED;
        }
        return SubtitleOrigin.UPLOADED;
    }

    @Nonnull
    private static String buildSubtitleCacheFilename(
            @Nonnull String videoId,
            @Nonnull String language,
            @Nonnull SubtitleOrigin origin,
            @Nonnull SubtitleState state,
            @Nonnull String extension
    ) {
        return videoId
                + "--" + language
                + "--" + origin.getId()
                + "--" + state.getId()
                + "." + extension;
    }

    private static String checkAutoTranslateLanguage(String subtitleUrl) {
        String language_autoTranslate = getAutoTranslateLanguage(subtitleUrl);

        if(true == stringIsNullOrEmpty(language_autoTranslate)) {
            return null;
        } else {
            return language_autoTranslate;
        }
    }

    private static String getLanguageCode(String remoteSubtitleUrl) {
        String languageCode = null;
        languageCode = YoutubeParsingHelper.extractLanguageCode(remoteSubtitleUrl);
        return languageCode;
    }

    private static String getAutoTranslateLanguage(String remoteSubtitleUrl) {
        // For auto-translate subtitles Url, there are two language code in it:
        // one is 'lang', now its meaning is source language;
        // the other is 'tlang', its meaning is target language.
        String target_autoTranslate = null;
        target_autoTranslate = YoutubeParsingHelper.extractTranslationCode(
                remoteSubtitleUrl
        );
        return target_autoTranslate;
    }

    // For auto-translate subtitles, the cache filename language
    // represents the target language (tlang), not the source language.
    private static String resolveSubtitleLanguage(
            String subtitleUrl,
            SubtitleOrigin origin
    ) {
        if (origin == SubtitleOrigin.AUTO_TRANSLATED) {
            String targetLang = getAutoTranslateLanguage(subtitleUrl);

            if (!stringIsNullOrEmpty(targetLang)) {
                return targetLang;
            } else {
                String UNKNOWN_LANGUAGE = "unknownLanguage";
                return UNKNOWN_LANGUAGE;
            }
        }

        return getLanguageCode(subtitleUrl);
    }

    // Extract the videoId (e.g., "lUDPjyfmJrs") from a subtitle URL
    // (e.g., .../api/timedtext?v=lUDPjyfmJrs)
    // for use in generating unique filenames.
    private static String getVideoId(String remoteSubtitleUrl) {
        String videoId = YoutubeParsingHelper.extractVideoId(remoteSubtitleUrl);
        return videoId;
    }

    private static File getCacheFile(String subtitleUrl,
                                        MediaFormat format,
                                        SubtitleOrigin currentSubtitleOrigin,
                                        SubtitleState currentSubtitleState) {
        String cachefilename = computeFilename(subtitleUrl,
                                                format,
                                                currentSubtitleOrigin,
                                                currentSubtitleState);

        File cacheFile = new File(CACHE_DIR, cachefilename);

        return cacheFile;
    }

    private static boolean theSubtitleWasStoredBefore(
        String remoteSubtitleUrl,
        MediaFormat format,
        SubtitleOrigin currentSubtitleOrigin
    ) {
        File storedFile = findStoredCacheFile(
                remoteSubtitleUrl,
                format,
                currentSubtitleOrigin
        );

        if (null == storedFile) {
            return false;
        } else {
            return true;
        }
    }

    private static File findStoredCacheFile(
            String remoteSubtitleUrl,
            MediaFormat format,
            SubtitleOrigin currentSubtitleOrigin
    ) {
        for (SubtitleState state : SubtitleState.values()) {
            File subtitleFile = getCacheFile(
                    remoteSubtitleUrl,
                    format,
                    currentSubtitleOrigin,
                    state
            );

            if (subtitleFile.exists() && subtitleFile.length() > 0) {
                return subtitleFile;
            }
        }

        return null;
    }

    private static boolean isFileEmpty(File file) {
        if(0 == file.length()) {
            return true;
        } else {
            return false;
        }
    }

    private static boolean ensureItsParentDirExist(File tempCacheFile) {
        File parentDir = tempCacheFile.getParentFile();

        if (parentDir.exists()) {
            return true;
        } else {
            boolean success = parentDir.mkdirs();
            if (true == success) {
                return true;
            } else {
                return false;
            }
        }
    }

    private static String writeDeduplicatedContentToCachefile(
                                                String subtitleContent,
                                                File tempCacheFile) {
        String result = writeContentToFile(subtitleContent, tempCacheFile);
        return result;
    }

    private static String writeContentToFile(String content, File tempFile) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tempFile), StandardCharsets.UTF_8))) {
            writer.write(content);
            return null;//ok
        } catch (IOException e) {
            String errorMessage = e.getMessage();
            System.err.println(TAG + ": Failed to write cache file: " + errorMessage);
            return errorMessage;
        }
    }

}
