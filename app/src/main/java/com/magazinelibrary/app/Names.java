package com.magazinelibrary.app;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative series detection: strip publication dates, preserve edition/country names. */
public final class Names {
    private static final String MONTHS = "January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec|Spring|Summer|Autumn|Fall|Winter";
    private static final Pattern PERIOD = Pattern.compile("(?i)(?:\\b(?:" + MONTHS + ")\\b|\\b(?:19|20)\\d{2}\\b|\\b(?:issue|vol(?:ume)?|no|number)\\.?\\s*\\d|\\d{1,2}月|第?\\d+期)");
    public static String title(String raw) {
        if (raw == null) return "Magazine";
        String s = raw.replaceAll("(?i)\\.pdf$", "").replace('_', ' ')
                .replaceAll("(?i)\\s*[-–|]\\s*(?:PDF download.*|Magazinelib.*)$", "")
                .replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", " ").replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? "Magazine" : s.substring(0, Math.min(180, s.length()));
    }
    public static String series(String raw) {
        String s = title(raw);
        String[] pieces = s.split("\\s+[-–—]\\s+");
        for (int i = 1; i < pieces.length; i++) {
            if (PERIOD.matcher(pieces[i]).find()) {
                int end = s.indexOf(pieces[i]);
                return s.substring(0, end).replaceAll("\\s+[-–—]\\s*$", "").trim();
            }
        }
        java.util.regex.Matcher m = Pattern.compile("(?i)\\s+(?:(?:" + MONTHS + ")\\b|(?:issue|vol(?:ume)?|no|number)\\.?\\s*\\d|(?:19|20)\\d{2}\\b|\\d{1,2}月)").matcher(s);
        if (m.find() && m.start() > 1) s = s.substring(0, m.start()).trim();
        return s.isEmpty() ? title(raw) : s;
    }
    public static String key(String raw) { return series(raw).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", ""); }
    public static String fileName(String title) { return title(title) + ".pdf"; }
    public static boolean https(String url) {
        try { URI u = URI.create(url); return "https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null && u.getUserInfo() == null; }
        catch (Exception e) { return false; }
    }
    public static String fromUrl(String url) {
        try {
            URI u = URI.create(url);
            if (u.getRawQuery() != null) for (String p : u.getRawQuery().split("&")) {
                if (p.startsWith("file=")) return title(URLDecoder.decode(p.substring(5), "UTF-8"));
            }
            String p = u.getRawPath();
            return title(URLDecoder.decode(p.substring(p.lastIndexOf('/') + 1), "UTF-8"));
        } catch (Exception e) { return "Magazine"; }
    }
    public static boolean challenge(String html) {
        String s=html.toLowerCase(Locale.ROOT);
        return s.contains("cf-chl-")||s.contains("challenges.cloudflare.com")||s.contains("<title>just a moment")||s.contains("verify you are human")||s.contains("g-recaptcha")||s.contains("h-captcha");
    }
    public static String clickedTitle(String clicked,String link,String fallback) {
        return clicked!=null&&!clicked.trim().isEmpty()?title(clicked):downloadUrl(link)?fromUrl(link):title(fallback);
    }
    public static boolean downloadUrl(String url) {
        if (!https(url)) return false;
        URI u = URI.create(url);
        return u.getPath().toLowerCase(Locale.ROOT).endsWith(".pdf") || (u.getHost().equals("magazinelib.com") && url.contains("action=vkpd_file"));
    }
}
