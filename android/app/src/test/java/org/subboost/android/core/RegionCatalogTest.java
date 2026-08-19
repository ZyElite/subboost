package org.subboost.android.core;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RegionCatalogTest {
    @Test
    public void keepsCountryCodeWhenNameAlreadyContainsChineseCountry() {
        assertEquals("VLESS-加拿大-CA-32",
                RegionCatalog.localizeNodeName("VLESS-加拿大-CA-32"));
        assertEquals("Hysteria2-荷兰-NL-01",
                RegionCatalog.localizeNodeName("Hysteria2-荷兰-NL-01"));
    }

    @Test
    public void convertsChineseNamesAndLegacyCodesToStableCodes() {
        assertEquals(List.of("hk", "jp", "other"),
                RegionCatalog.codes(List.of("香港", "JP", "其他地区")));
        assertEquals("香港、日本", RegionCatalog.displayNames(List.of("hk", "日本")));
    }

    @Test
    public void matchesChineseAndLegacyNodeNames() {
        assertTrue(RegionCatalog.matches("香港 IPLC 01", List.of("香港")));
        assertTrue(RegionCatalog.matches("JP Tokyo 01", List.of("jp")));
        assertTrue(RegionCatalog.matches("未知地区 01", List.of("other")));
    }

    @Test
    public void localizesStandaloneCountryCodesInNodeNames() {
        assertEquals("香港-01", RegionCatalog.localizeNodeName("HK-01"));
        assertEquals("日本 02", RegionCatalog.localizeNodeName("JP 02"));
        assertEquals("美国03", RegionCatalog.localizeNodeName("US03"));
        assertEquals("英国 Premium", RegionCatalog.localizeNodeName("GB Premium"));
        assertEquals("HKT Premium", RegionCatalog.localizeNodeName("HKT Premium"));
    }
}
