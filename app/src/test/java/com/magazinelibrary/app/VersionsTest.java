package com.magazinelibrary.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VersionsTest {
    @Test public void detectsPatchAndMinorReleases() {
        assertTrue(Versions.isNewer("v1.4.1", "1.4.0"));
        assertTrue(Versions.isNewer("1.5.0", "1.4.9"));
        assertTrue(Versions.isNewer("2.0.0", "1.99.99"));
    }

    @Test public void equalVersionsAreNotNewer() {
        assertFalse(Versions.isNewer("v1.4.0", "1.4.0"));
        assertFalse(Versions.isNewer("1.4", "1.4.0"));
        assertFalse(Versions.isNewer("1.4.0.0", "1.4.0"));
    }

    @Test public void olderVersionsAreNotNewer() {
        assertFalse(Versions.isNewer("1.3.9", "1.4.0"));
        assertFalse(Versions.isNewer("v1.3.0", "1.4.0"));
    }

    @Test public void handlesPreReleaseSuffixesAndMissingValues() {
        assertTrue(Versions.isNewer("1.4.0-rc2", "1.3.9"));
        assertFalse(Versions.isNewer("", "1.4.0"));
        assertFalse(Versions.isNewer(null, "1.4.0"));
    }
}
