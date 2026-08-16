package org.subboost.android.core;

import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigGeneratorTest {
    @Test
    @SuppressWarnings("unchecked")
    public void generatesValidStandaloneMihomoYaml() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", "HK 01");
        node.put("type", "ss");
        node.put("server", "hk.example.com");
        node.put("port", 443);
        node.put("cipher", "aes-128-gcm");
        node.put("password", "secret");

        String output = new ConfigGenerator().generate(List.of(node));
        Map<String, Object> config = new Yaml().load(output);

        assertEquals(7897, config.get("mixed-port"));
        assertEquals(1, ((List<?>) config.get("proxies")).size());
        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("proxy-groups");
        assertEquals("🚀 节点选择", groups.get(0).get("name"));
        assertTrue(((List<?>) groups.get(0).get("proxies")).contains("HK 01"));
        List<?> rules = (List<?>) config.get("rules");
        assertEquals("MATCH,🐟 漏网之鱼", rules.get(rules.size() - 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesEmptyNodeList() {
        new ConfigGenerator().generate(List.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void appliesTemplateAdvancedFilteringAndLoadBalance() {
        ConfigOptions options = new ConfigOptions();
        options.applyTemplate("minimal");
        options.advancedMode = true;
        ConfigOptions.GroupAdvanced advanced = new ConfigOptions.GroupAdvanced();
        advanced.groupType = "load-balance";
        advanced.strategy = "round-robin";
        advanced.regions = List.of("hk");
        advanced.sourceIds = List.of("airport-a");
        options.groupAdvanced.put("auto", advanced);

        Map<String, Object> hk = node("香港 01", "hk.example.com");
        hk.put("_subboost-source", "airport-a");
        Map<String, Object> jp = node("日本 01", "jp.example.com");
        Map<String, Object> config = new Yaml().load(new ConfigGenerator().generate(List.of(hk, jp), options));
        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("proxy-groups");

        assertEquals(7, groups.size());
        Map<String, Object> auto = groups.stream().filter(group -> group.get("name").equals("⚡ 自动选择")).findFirst().orElseThrow();
        assertEquals("load-balance", auto.get("type"));
        assertEquals("round-robin", auto.get("strategy"));
        assertEquals(List.of("香港 01"), auto.get("proxies"));
        assertTrue(!((Map<?, ?>) ((List<?>) config.get("proxies")).get(0)).containsKey("_subboost-source"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void generatesCustomRulesDialerAndListeners() {
        ConfigOptions options = new ConfigOptions();
        options.advancedMode = true;
        ConfigOptions.DialerGroup dialer = new ConfigOptions.DialerGroup();
        dialer.name = "香港中转";
        dialer.relayNodes = List.of("香港入口");
        dialer.targetNodes = List.of("美国落地");
        options.dialerGroups.add(dialer);
        ConfigOptions.CustomRule rule = new ConfigOptions.CustomRule();
        rule.type = "DOMAIN-SUFFIX"; rule.value = "example.com"; rule.target = "香港中转";
        options.customRules.add(rule);
        ConfigOptions.CustomRuleSet set = new ConfigOptions.CustomRuleSet();
        set.id = "company"; set.path = "geosite/company.mrs"; set.target = "DIRECT";
        options.customRuleSets.add(set);
        ConfigOptions.Listener listener = new ConfigOptions.Listener();
        listener.group = "香港中转"; listener.port = 10080;
        options.groupListeners.add(listener);

        Map<String, Object> config = new Yaml().load(new ConfigGenerator().generate(
                List.of(node("香港入口", "hk.example.com"), node("美国落地", "us.example.com")), options));
        List<Map<String, Object>> proxies = (List<Map<String, Object>>) config.get("proxies");
        assertEquals("香港中转", proxies.get(1).get("dialer-proxy"));
        assertTrue(((Map<?, ?>) config.get("rule-providers")).containsKey("company"));
        assertEquals("DOMAIN-SUFFIX,example.com,香港中转", ((List<?>) config.get("rules")).get(0));
        assertEquals(10080, ((Map<?, ?>) ((List<?>) config.get("listeners")).get(0)).get("port"));
    }

    private static Map<String, Object> node(String name, String server) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", name); node.put("type", "ss"); node.put("server", server); node.put("port", 443);
        node.put("cipher", "aes-128-gcm"); node.put("password", "secret"); return node;
    }
}
