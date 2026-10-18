package com.example.walletbot.platform.logging;

import java.util.regex.Pattern;

public final class SecretMasker {

    private static final String MASK = "***";

    private static final Pattern URL_QUERY_SECRET = Pattern.compile(
            "(?i)([?&](?:api[-_]?key|apikey|access[-_]?token|auth[-_]?token|token|secret|key)=)"
                    + "[^&\\s\"'<>)\\]}]+");

    private static final Pattern ALCHEMY_PATH_KEY = Pattern.compile(
            "(?i)(alchemy\\.com/v2/)[^/\\s\"'?&#]+");

    private SecretMasker() {
    }

    public static String mask(String line) {
        if (line == null || line.isEmpty() || !mightContainSecret(line)) {
            return line;
        }
        String masked = URL_QUERY_SECRET.matcher(line).replaceAll("$1" + MASK);
        masked = ALCHEMY_PATH_KEY.matcher(masked).replaceAll("$1" + MASK);
        return masked;
    }

    private static boolean mightContainSecret(String line) {
        return containsIgnoreCase(line, "key=")
                || containsIgnoreCase(line, "token=")
                || containsIgnoreCase(line, "secret=")
                || containsIgnoreCase(line, "alchemy.com/v2/");
    }

    private static boolean containsIgnoreCase(String haystack, String needleLower) {
        int limit = haystack.length() - needleLower.length();
        for (int i = 0; i <= limit; i++) {
            if (haystack.regionMatches(true, i, needleLower, 0, needleLower.length())) {
                return true;
            }
        }
        return false;
    }
}
