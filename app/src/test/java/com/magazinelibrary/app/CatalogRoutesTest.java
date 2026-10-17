package com.magazinelibrary.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogRoutesTest {
    @Test public void searchAndFiltersAreOneScopedRequest(){String url=CatalogRoutes.query("Home Networking & Wi-Fi","https://magazinelib.com/technology/computers/","2026/10");assertEquals("https://magazinelib.com/?s=Home+Networking+%26+Wi-Fi&year=2026&monthnum=10&category_name=computers",url);}
    @Test public void categoryWithoutSearchUsesAdvertisedUrl(){assertEquals("https://magazinelib.com/technology/",CatalogRoutes.query("","https://magazinelib.com/technology/",""));}
    @Test public void paginationPreservesSearchAndFilters(){String url="https://magazinelib.com/page/2/?s=android&category_name=technology";assertEquals(2,CatalogRoutes.number(url));assertEquals("https://magazinelib.com/page/3/?s=android&category_name=technology",CatalogRoutes.page(url,3));assertEquals("https://magazinelib.com/?s=android&category_name=technology",CatalogRoutes.page(url,1));}
    @Test public void rejectExternalOrCredentialUrls(){assertFalse(CatalogRoutes.site("https://magazinelib.com.evil.test/"));assertFalse(CatalogRoutes.site("https://user@magazinelib.com/"));assertFalse(CatalogRoutes.site("https://magazinelib.com:8443/"));assertFalse(CatalogRoutes.site("http://magazinelib.com/"));assertTrue(CatalogRoutes.site("https://magazinelib.com/all/"));}
}
