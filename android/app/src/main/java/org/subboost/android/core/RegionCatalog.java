package org.subboost.android.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chinese display names and node-name matching rules for region filters. */
public final class RegionCatalog {
    private static final List<Region> REGIONS = Arrays.asList(
            region("hk", "香港", "香港", " hk", "hong kong", "港"),
            region("jp", "日本", "日本", " jp", "japan", "东京", "大阪"),
            region("sg", "新加坡", "新加坡", " sg", "singapore", "狮城"),
            region("us", "美国", "美国", " us", "usa", "los angeles", "洛杉矶", "纽约"),
            region("tw", "台湾", "台湾", " tw", "taiwan", "台北"),
            region("kr", "韩国", "韩国", " kr", "korea", "首尔"),
            region("uk", "英国", "英国", " uk", "london", "伦敦"),
            region("de", "德国", "德国", " de", "germany", "frankfurt", "法兰克福"),
            region("fr", "法国", "法国", " fr", "france", "paris", "巴黎"),
            region("ca", "加拿大", "加拿大", " ca", "canada", "toronto", "多伦多"),
            region("au", "澳大利亚", "澳大利亚", " au", "australia", "sydney", "悉尼"),
            region("other", "其他地区")
    );

    public static List<Region> all() {
        return Collections.unmodifiableList(REGIONS);
    }

    public static List<String> codes(List<String> values) {
        Set<String> out = new LinkedHashSet<>();
        if (values == null) return new ArrayList<>();
        for (String raw : values) {
            if (raw == null) continue;
            String value = raw.trim();
            for (Region region : REGIONS) {
                if (region.code.equalsIgnoreCase(value) || region.name.equals(value)) {
                    out.add(region.code);
                    break;
                }
            }
        }
        return new ArrayList<>(out);
    }

    public static String displayNames(List<String> values) {
        List<String> codes = codes(values);
        List<String> names = new ArrayList<>();
        for (Region region : REGIONS) if (codes.contains(region.code)) names.add(region.name);
        return String.join("、", names);
    }

    public static String localizeNodeName(String nodeName) {
        String localized = String.valueOf(nodeName);
        for (Region region : REGIONS) {
            if (region.code.equals("other")) continue;
            String aliases = switch (region.code) {
                case "us" -> "US|USA";
                case "uk" -> "UK|GB";
                default -> region.code;
            };
            Pattern code = Pattern.compile("(?i)(?<![A-Za-z])(?:" + aliases + ")(?![A-Za-z])");
            localized = code.matcher(localized).replaceAll(Matcher.quoteReplacement(region.name));
        }
        return localized;
    }

    public static boolean matches(String nodeName, List<String> selectedValues) {
        List<String> selected = codes(selectedValues);
        if (selected.isEmpty()) return true;
        String normalized = " " + String.valueOf(nodeName).toLowerCase(Locale.ROOT);
        boolean known = false;
        for (Region region : REGIONS) {
            if (region.code.equals("other")) continue;
            boolean match = false;
            for (String keyword : region.keywords) {
                if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                    match = true;
                    break;
                }
            }
            if (match) {
                known = true;
                if (selected.contains(region.code)) return true;
            }
        }
        return selected.contains("other") && !known;
    }

    private static Region region(String code, String name, String... keywords) {
        return new Region(code, name, Arrays.asList(keywords));
    }

    public static final class Region {
        public final String code;
        public final String name;
        private final List<String> keywords;

        private Region(String code, String name, List<String> keywords) {
            this.code = code;
            this.name = name;
            this.keywords = keywords;
        }
    }

    private RegionCatalog() { }
}
