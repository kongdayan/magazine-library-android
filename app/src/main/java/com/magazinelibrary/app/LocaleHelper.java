package com.magazinelibrary.app;

import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/** App language: system per-app locales on Android 13+, a stored override before that. */
final class LocaleHelper {
    private static final String PREFS = "settings";
    private static final String KEY = "language";
    static final String SYSTEM = "system";
    static final String[] TAGS = {"en", "zh-CN", "ja", "ko", "ru"};

    private LocaleHelper() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String current(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            if (manager != null) {
                LocaleList locales = manager.getApplicationLocales();
                if (locales != null && !locales.isEmpty()) return locales.toLanguageTags();
            }
            return SYSTEM;
        }
        return prefs(context).getString(KEY, SYSTEM);
    }

    static Context wrap(Context base) {
        if (Build.VERSION.SDK_INT >= 33) return base;
        String tag = prefs(base).getString(KEY, SYSTEM);
        if (tag == null || tag.isEmpty() || SYSTEM.equals(tag)) return base;
        return localized(base, tag);
    }

    static void set(Context context, String tag) {
        String normalized = tag == null || tag.isEmpty() ? SYSTEM : tag;
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            if (manager != null) {
                manager.setApplicationLocales(SYSTEM.equals(normalized)
                        ? LocaleList.getEmptyLocaleList()
                        : LocaleList.forLanguageTags(normalized));
            }
        }
        prefs(context).edit().putString(KEY, normalized).apply();
        // Keep application-level resources in sync so notifications and helpers switch at once.
        applyResources(context, normalized);
    }

    static void applyResources(Context context, String tag) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(SYSTEM.equals(tag) ? Locale.getDefault() : Locale.forLanguageTag(tag));
        configuration.setLayoutDirection(configuration.getLocales().get(0));
        context.getResources().updateConfiguration(configuration, context.getResources().getDisplayMetrics());
    }

    private static Context localized(Context base, String tag) {
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        Locale locale = Locale.forLanguageTag(tag);
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        return base.createConfigurationContext(configuration);
    }
}
