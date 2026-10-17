package com.magazinelibrary.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class NamesTest {
    @Test public void monthlyIssuesGroup(){assertEquals(Names.key("Charleston Magazine – October 2026.pdf"),Names.key("Charleston Magazine - September 2026.pdf"));assertEquals("Journal of The Air Force Historical Foundation",Names.series("Journal of The Air Force Historical Foundation – Spring 2026"));}
    @Test public void preserveNumbersAndEdition(){assertEquals("3D",Names.series("3D – October 2026"));assertEquals("Computer Arts",Names.series("Computer Arts – Issue 50 2026"));assertNotEquals(Names.key("Vogue UK – October 2026"),Names.key("Vogue USA – October 2026"));assertEquals("Anti-Inflammatory",Names.series("Anti-Inflammatory – Recipe Book 2026"));}
    @Test public void safeNames(){assertEquals("evil name.pdf",Names.fileName("evil/name"));assertFalse(Names.https("javascript:alert(1)"));assertFalse(Names.https("https://user:secret@example.com/test.pdf"));assertFalse(Names.downloadUrl("https://example.com/test.pdf.js"));}
    @Test public void downloadTitleUsesClickedAnchor(){assertEquals("Home Networking For Beginners - Autumn 2026",Names.clickedTitle("Home Networking For Beginners - Autumn 2026.pdf","https://cdn.example.com/file.pdf","China Report - October 2026"));}
    @Test public void redirectPagesAreNotChallenges(){assertFalse(Names.challenge("<title>VK</title><a>Download</a>"));assertTrue(Names.challenge("<title>Just a moment...</title><script src='/cdn-cgi/challenge-platform/cf-chl-1'></script>"));}
    @Test public void realWebsiteEndpoint(){String u="https://magazinelib.com/login/admin-ajax.php?action=vkpd_file&id=684285&file=Journal+of+The+Air+Force+Historical+Foundation+-+Spring+2026.pdf";assertTrue(Names.downloadUrl(u));assertEquals("Journal of The Air Force Historical Foundation - Spring 2026",Names.fromUrl(u));}
}
