package com.magazinelibrary.app;

/** Semantic-version comparison for the manual update check. */
final class Versions {
    private Versions() {}

    static boolean isNewer(String latest, String current) {
        String[] a = normalize(latest), b = normalize(current);
        for (int n = 0; n < Math.max(a.length, b.length); n++) {
            int left = n < a.length ? number(a[n]) : 0;
            int right = n < b.length ? number(b[n]) : 0;
            if (left != right) return left > right;
        }
        return false;
    }

    private static String[] normalize(String value) {
        if (value == null) return new String[0];
        return value.trim().replaceFirst("^[vV]", "").split("[.+-]");
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value.replaceAll("\\D.*$", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
