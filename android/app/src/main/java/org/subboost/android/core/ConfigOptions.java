package org.subboost.android.core;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serializable configuration edited by the Android advanced settings screen. */
public final class ConfigOptions {
    public String schema = "subboost-android-config/v1";
    public String template = "standard";
    public List<String> enabledModules = new ArrayList<>();
    public boolean advancedMode;
    public int mixedPort = 7897;
    public boolean allowLan = true;
    public String testUrl = "https://www.gstatic.com/generate_204";
    public int testInterval = 300;
    public String ruleProviderBaseUrl = "https://github.com/MetaCubeX/meta-rules-dat/raw/refs/heads/meta/geo";
    public boolean cnIpNoResolve = true;
    public boolean experimentalCnRuleSet = true;
    public String baseConfigYaml = "";
    public Map<String, GroupAdvanced> groupAdvanced = new LinkedHashMap<>();
    public Map<String, String> groupNameOverrides = new LinkedHashMap<>();
    public Map<String, String> builtinRuleTargets = new LinkedHashMap<>();
    public List<String> disabledBuiltinRules = new ArrayList<>();
    public List<CustomGroup> customGroups = new ArrayList<>();
    public List<CustomRuleSet> customRuleSets = new ArrayList<>();
    public List<CustomRule> customRules = new ArrayList<>();
    public List<DialerGroup> dialerGroups = new ArrayList<>();
    public List<Listener> groupListeners = new ArrayList<>();
    public Map<String, Integer> nodeListenerPorts = new LinkedHashMap<>();
    public Map<String, String> nodeNameOverrides = new LinkedHashMap<>();
    public List<String> excludedNodeNames = new ArrayList<>();
    public List<String> proxyGroupOrder = new ArrayList<>();
    public List<String> ruleOrder = new ArrayList<>();
    public int localSharePort = 17890;

    public ConfigOptions() {
        applyTemplate("standard");
    }

    public void applyTemplate(String value) {
        template = ModuleCatalog.isTemplate(value) ? value : "standard";
        enabledModules = new ArrayList<>(ModuleCatalog.modulesForTemplate(template));
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public static ConfigOptions fromJson(String json) {
        ConfigOptions value = new Gson().fromJson(json, ConfigOptions.class);
        if (value == null || !"subboost-android-config/v1".equals(value.schema)) {
            throw new IllegalArgumentException("模板 JSON schema 无效");
        }
        value.normalize();
        return value;
    }

    public void normalize() {
        if (!ModuleCatalog.isTemplate(template)) template = "standard";
        if (enabledModules == null || enabledModules.isEmpty()) enabledModules = new ArrayList<>(ModuleCatalog.modulesForTemplate(template));
        enabledModules.removeIf(id -> ModuleCatalog.byId(id) == null);
        if (!enabledModules.contains("select")) enabledModules.add(0, "select");
        if (!enabledModules.contains("wificalling")) enabledModules.add(Math.min(2, enabledModules.size()), "wificalling");
        if (!enabledModules.contains("final")) enabledModules.add("final");
        if (mixedPort < 1 || mixedPort > 65535) mixedPort = 7897;
        if (testInterval < 10) testInterval = 300;
        if (blank(testUrl)) testUrl = "https://www.gstatic.com/generate_204";
        if (blank(ruleProviderBaseUrl)) ruleProviderBaseUrl = "https://github.com/MetaCubeX/meta-rules-dat/raw/refs/heads/meta/geo";
        if (baseConfigYaml == null) baseConfigYaml = "";
        if (groupAdvanced == null) groupAdvanced = new LinkedHashMap<>();
        if (groupNameOverrides == null) groupNameOverrides = new LinkedHashMap<>();
        if (builtinRuleTargets == null) builtinRuleTargets = new LinkedHashMap<>();
        if (disabledBuiltinRules == null) disabledBuiltinRules = new ArrayList<>();
        if (customGroups == null) customGroups = new ArrayList<>();
        if (customRuleSets == null) customRuleSets = new ArrayList<>();
        if (customRules == null) customRules = new ArrayList<>();
        if (dialerGroups == null) dialerGroups = new ArrayList<>();
        if (groupListeners == null) groupListeners = new ArrayList<>();
        if (nodeListenerPorts == null) nodeListenerPorts = new LinkedHashMap<>();
        if (nodeNameOverrides == null) nodeNameOverrides = new LinkedHashMap<>();
        if (excludedNodeNames == null) excludedNodeNames = new ArrayList<>();
        if (proxyGroupOrder == null) proxyGroupOrder = new ArrayList<>();
        if (ruleOrder == null) ruleOrder = new ArrayList<>();
        if (localSharePort < 1024 || localSharePort > 65535) localSharePort = 17890;
        for (GroupAdvanced advanced : groupAdvanced.values()) normalizeAdvanced(advanced);
        for (CustomGroup group : customGroups) {
            if (group == null) continue;
            if (group.advanced == null) group.advanced = new GroupAdvanced();
            normalizeAdvanced(group.advanced);
        }
        for (DialerGroup group : dialerGroups) {
            if (group == null) continue;
            if (group.relayNodes == null) group.relayNodes = new ArrayList<>();
            if (group.targetNodes == null) group.targetNodes = new ArrayList<>();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void normalizeAdvanced(GroupAdvanced value) {
        if (value == null) return;
        value.regions = RegionCatalog.codes(value.regions);
        if (value.sourceIds == null) value.sourceIds = new ArrayList<>();
        if (value.members == null) value.members = new ArrayList<>();
        if (value.extraMembers == null) value.extraMembers = new ArrayList<>();
        if (value.excludedMembers == null) value.excludedMembers = new ArrayList<>();
        if (value.includeRegex == null) value.includeRegex = "";
        if (value.excludeRegex == null) value.excludeRegex = "";
    }

    public static final class GroupAdvanced {
        public String groupType = "select";
        public String strategy = "consistent-hashing";
        public List<String> regions = new ArrayList<>();
        public List<String> sourceIds = new ArrayList<>();
        public String includeRegex = "";
        public String excludeRegex = "";
        public List<String> members = new ArrayList<>();
        public List<String> extraMembers = new ArrayList<>();
        public List<String> excludedMembers = new ArrayList<>();
    }

    public static final class CustomGroup {
        public String id = "";
        public String name = "";
        public boolean enabled = true;
        public String groupType = "select";
        public String strategy = "consistent-hashing";
        public GroupAdvanced advanced = new GroupAdvanced();
    }

    public static final class CustomRuleSet {
        public String id = "";
        public String behavior = "domain";
        public String path = "";
        public String target = "🚀 节点选择";
        public boolean noResolve;
    }

    public static final class CustomRule {
        public String type = "DOMAIN-SUFFIX";
        public String value = "";
        public String target = "🚀 节点选择";
        public boolean noResolve;
    }

    public static final class DialerGroup {
        public String name = "";
        public boolean enabled = true;
        public String type = "select";
        public String strategy = "consistent-hashing";
        public List<String> relayNodes = new ArrayList<>();
        public List<String> targetNodes = new ArrayList<>();
    }

    public static final class Listener {
        public String group = "";
        public int port;
        public boolean allowLan;
        public boolean enabled = true;
    }

    public static List<String> csv(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String part : raw.split(",")) {
            String value = part.trim();
            if (!value.isEmpty() && !out.contains(value)) out.add(value);
        }
        return out;
    }

    public static String join(List<String> values) {
        return values == null ? "" : String.join(",", values);
    }
}
