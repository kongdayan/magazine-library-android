package com.magazinelibrary.app;

/** Short access to localized strings from any class, including download and file helpers. */
final class T {
    private T() {}

    static String s(int id) {
        return App.get().getString(id);
    }

    static String s(int id, Object... args) {
        return App.get().getString(id, args);
    }
}
