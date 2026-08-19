package org.subboost.android.core;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Produces a standalone Mihomo config using the same template concepts as the web core. */
public final class ConfigGenerator {
    public String generate(List<Map<String, Object>> nodes) {
        return generate(nodes, new ConfigOptions());
    }

    public String generate(List<Map<String, Object>> sourceNodes, ConfigOptions rawOptions) {
        if (sourceNodes == null || sourceNodes.isEmpty()) throw new IllegalArgumentException("请先导入至少一个有效节点");
        ConfigOptions options = rawOptions == null ? new ConfigOptions() : rawOptions;
        options.normalize();

        List<Map<String, Object>> nodes = prepareNodes(sourceNodes, options);
        if (nodes.isEmpty()) throw new IllegalArgumentException("所有节点均已被删除，无法生成配置");
        List<Map<String, Object>> dialerGroups = applyDialers(nodes, options);
        List<String> nodeNames = names(nodes);
        Map<String, String> moduleNames = resolveModuleNames(options);
        List<Map<String, Object>> groups = generateGroups(nodes, nodeNames, moduleNames, options);
        groups.addAll(2 > groups.size() ? groups.size() : 2, dialerGroups);
        groups = reorderGroups(groups, options.proxyGroupOrder);

        Map<String, Object> providers = new LinkedHashMap<>();
        List<String> rules = generateRules(moduleNames, providers, options);
        Map<String, Object> root = parseBaseConfig(options.baseConfigYaml);
        int effectiveMixedPort = intValue(root.get("mixed-port"), options.mixedPort);
        List<Map<String, Object>> baseListeners = extractListeners(root.get("listeners"));
        List<Map<String, Object>> listeners = generateListeners(nodes, groups, options, effectiveMixedPort, baseListeners);
        root.putIfAbsent("mixed-port", options.mixedPort);
        root.putIfAbsent("allow-lan", options.allowLan);
        root.putIfAbsent("mode", "rule");
        root.putIfAbsent("log-level", "info");
        root.putIfAbsent("ipv6", true);
        root.putIfAbsent("unified-delay", true);
        root.putIfAbsent("tcp-concurrent", true);
        root.putIfAbsent("find-process-mode", "strict");
        root.putIfAbsent("global-client-fingerprint", "chrome");
        root.putIfAbsent("profile", profile());
        root.putIfAbsent("sniffer", sniffer());
        root.putIfAbsent("dns", defaultDns());
        root.put("proxies", outputNodes(nodes));
        root.put("proxy-groups", groups);
        if (!providers.isEmpty()) root.put("rule-providers", providers);
        root.put("rules", rules);
        if (!baseListeners.isEmpty() || !listeners.isEmpty()) {
            List<Map<String, Object>> combined = new ArrayList<>(baseListeners);
            combined.addAll(listeners);
            root.put("listeners", combined);
        }
        return yaml().dump(root);
    }

    private List<Map<String, Object>> generateGroups(
            List<Map<String, Object>> nodes,
            List<String> nodeNames,
            Map<String, String> moduleNames,
            ConfigOptions options) {
        List<Map<String, Object>> groups = new ArrayList<>();
        Set<String> enabled = new LinkedHashSet<>(options.enabledModules);
        String select = enabled.contains("select") ? moduleNames.get("select") : null;
        String auto = enabled.contains("auto") ? moduleNames.get("auto") : null;

        for (ModuleCatalog.Module module : ModuleCatalog.all()) {
            if (!enabled.contains(module.id)) continue;
            ConfigOptions.GroupAdvanced advanced = options.advancedMode ? options.groupAdvanced.get(module.id) : null;
            String type = advanced == null || blank(advanced.groupType) ? module.groupType : advanced.groupType;
            List<String> defaults;
            if (module.id.equals("select")) defaults = unique(auto, "DIRECT", "REJECT", nodeNames);
            else if (module.id.equals("auto") || type.equals("url-test") || type.equals("fallback") || type.equals("load-balance")) defaults = new ArrayList<>(nodeNames);
            else if (type.equals("direct-first")) defaults = unique("DIRECT", "REJECT", select, auto, nodeNames);
            else if (type.equals("reject-first")) defaults = unique("REJECT", "DIRECT", select);
            else defaults = unique(select, auto, "DIRECT", "REJECT", nodeNames);
            List<String> members = resolveMembers(defaults, nodes, advanced);
            groups.add(buildGroup(moduleNames.get(module.id), type, members,
                    advanced == null ? null : advanced.strategy, options));
        }

        for (ConfigOptions.CustomGroup custom : options.customGroups) {
            if (custom == null || !custom.enabled || blank(custom.name)) continue;
            ConfigOptions.GroupAdvanced advanced = custom.advanced == null ? new ConfigOptions.GroupAdvanced() : custom.advanced;
            String type = blank(custom.groupType) ? "select" : custom.groupType;
            if (!options.advancedMode) advanced = null;
            List<String> defaults = type.equals("direct-first")
                    ? unique("DIRECT", "REJECT", nodeNames)
                    : type.equals("reject-first") ? unique("REJECT", "DIRECT", nodeNames) : new ArrayList<>(nodeNames);
            groups.add(buildGroup(custom.name.trim(), type, resolveMembers(defaults, nodes, advanced), custom.strategy, options));
        }
        return groups;
    }

    private Map<String, Object> buildGroup(String name, String requestedType, List<String> members, String strategy, ConfigOptions options) {
        String type = validGroupType(requestedType) ? requestedType : "select";
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("name", name);
        if (type.equals("direct-first") || type.equals("reject-first")) type = "select";
        group.put("type", type);
        group.put("proxies", members.isEmpty() ? Collections.singletonList("DIRECT") : members);
        if (type.equals("url-test") || type.equals("fallback") || type.equals("load-balance")) {
            group.put("url", options.testUrl);
            group.put("interval", options.testInterval);
            group.put("lazy", true);
        }
        if (type.equals("url-test")) group.put("tolerance", 50);
        if (type.equals("load-balance")) group.put("strategy", validStrategy(strategy) ? strategy : "consistent-hashing");
        return group;
    }

    private List<String> resolveMembers(List<String> defaults, List<Map<String, Object>> nodes, ConfigOptions.GroupAdvanced advanced) {
        if (advanced == null) return dedupe(defaults);
        List<String> members = advanced.members == null || advanced.members.isEmpty()
                ? new ArrayList<>(defaults) : new ArrayList<>(advanced.members);
        Pattern include = regex(advanced.includeRegex);
        Pattern exclude = regex(advanced.excludeRegex);
        Set<String> nodeNames = new LinkedHashSet<>(names(nodes));
        members = localizeNodeReferences(members, nodeNames);
        members.removeIf(name -> nodeNames.contains(name) && (!RegionCatalog.matches(name, advanced.regions)
                || !matchesSource(name, nodes, advanced.sourceIds)
                || (include != null && !include.matcher(name).find())
                || (exclude != null && exclude.matcher(name).find())));
        if (advanced.extraMembers != null) members.addAll(localizeNodeReferences(advanced.extraMembers, nodeNames));
        if (advanced.excludedMembers != null) members.removeAll(localizeNodeReferences(advanced.excludedMembers, nodeNames));
        return dedupe(members);
    }

    private List<Map<String, Object>> applyDialers(List<Map<String, Object>> nodes, ConfigOptions options) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> assigned = new LinkedHashSet<>();
        Set<String> nodeNames = new LinkedHashSet<>(names(nodes));
        for (ConfigOptions.DialerGroup dialer : options.dialerGroups) {
            if (dialer == null || !dialer.enabled || blank(dialer.name)) continue;
            List<String> relays = existing(dialer.relayNodes, nodeNames);
            List<String> targets = existing(dialer.targetNodes, nodeNames);
            for (String target : targets) {
                if (relays.contains(target)) throw new IllegalArgumentException("节点“" + target + "”不能同时作为中转和落地节点");
                if (!assigned.add(target)) continue;
                for (Map<String, Object> node : nodes) {
                    if (target.equals(String.valueOf(node.get("name")))) node.put("dialer-proxy", dialer.name.trim());
                }
            }
            if (!relays.isEmpty()) result.add(buildGroup(dialer.name.trim(), dialer.type, relays, dialer.strategy, options));
        }
        return result;
    }

    private List<String> generateRules(Map<String, String> moduleNames, Map<String, Object> providers, ConfigOptions options) {
        List<String> rules = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        Set<String> enabled = new LinkedHashSet<>(options.enabledModules);
        Set<String> disabled = new LinkedHashSet<>(options.disabledBuiltinRules);
        String base = trimSlash(options.ruleProviderBaseUrl);


        int customIndex = 0;
        for (ConfigOptions.CustomRule custom : options.customRules) {
            if (custom == null || blank(custom.type) || blank(custom.value) || blank(custom.target)) continue;
            String rule = custom.type.trim().toUpperCase(Locale.ROOT) + "," + custom.value.trim() + "," + custom.target.trim();
            if (custom.noResolve) rule += ",no-resolve";
            rules.add(rule);
            keys.add("custom:" + customIndex++);
        }
        for (ConfigOptions.CustomRuleSet custom : options.customRuleSets) {
            if (custom == null || blank(custom.id) || blank(custom.path) || blank(custom.target)) continue;
            if (isBuiltinRuleId(custom.id)) throw new IllegalArgumentException("自定义规则集 id 与内置规则冲突：" + custom.id);
            providers.put(custom.id, provider(custom.behavior, resolveRuleUrl(base, custom.path), custom.id));
            rules.add("RULE-SET," + custom.id + "," + custom.target + (custom.noResolve ? ",no-resolve" : ""));
            keys.add("ruleset:" + custom.id);
        }
        for (ModuleCatalog.Module module : ModuleCatalog.all()) {
            if (!enabled.contains(module.id) || module.id.equals("final")) continue;
            for (ModuleCatalog.Rule rule : module.rules) {
                String key = module.id + ":" + rule.id;
                if (disabled.contains(key)) continue;
                providers.put(rule.id, provider(rule.behavior, base + "/" + rule.path, rule.id));
                String target = options.builtinRuleTargets.get(key);
                if (blank(target)) target = moduleNames.get(module.id);
                boolean noResolve = rule.noResolve;
                if (module.id.equals("cn") && rule.id.equals("cn-ip")) noResolve = options.cnIpNoResolve;
                rules.add("RULE-SET," + rule.id + "," + target + (noResolve ? ",no-resolve" : ""));
                keys.add(key);
            }
        }
        if (options.experimentalCnRuleSet && enabled.contains("cn")) {
            providers.put("cn", provider("domain", base + "/geosite/cn.mrs", "cn"));
            rules.add("RULE-SET,cn," + moduleNames.get("cn"));
            keys.add("cn:cn");
        }
        String finalTarget = enabled.contains("final") ? moduleNames.get("final")
                : enabled.contains("select") ? moduleNames.get("select") : "DIRECT";
        rules.add("MATCH," + finalTarget);
        keys.add("special:match");
        return reorderRules(rules, keys, options.ruleOrder);
    }

    private Map<String, Object> provider(String behavior, String url, String id) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", "http");
        value.put("behavior", "ipcidr".equals(behavior) ? "ipcidr" : "domain");
        value.put("format", "mrs");
        value.put("url", url);
        value.put("path", "./ruleset/" + id + ".mrs");
        value.put("interval", 86400);
        return value;
    }

    private List<Map<String, Object>> generateListeners(List<Map<String, Object>> nodes, List<Map<String, Object>> groups,
                                                         ConfigOptions options, int effectiveMixedPort,
                                                         List<Map<String, Object>> baseListeners) {
        List<Map<String, Object>> listeners = new ArrayList<>();
        Set<Integer> ports = new LinkedHashSet<>();
        ports.add(effectiveMixedPort);
        for (Map<String, Object> listener : baseListeners) {
            int port = intValue(listener.get("port"), 0);
            if (port > 0) ports.add(port);
        }
        Set<String> nodeNames = new LinkedHashSet<>(names(nodes));
        Set<String> groupNames = new LinkedHashSet<>();
        for (Map<String, Object> group : groups) groupNames.add(String.valueOf(group.get("name")));
        int index = 0;
        for (Map.Entry<String, Integer> entry : options.nodeListenerPorts.entrySet()) {
            String nodeName = RegionCatalog.localizeNodeName(entry.getKey());
            if (!nodeNames.contains(nodeName)) continue;
            validateListenerPort(entry.getValue(), ports);
            listeners.add(listener("node-mixed-" + index++, entry.getValue(), nodeName, false));
        }
        for (ConfigOptions.Listener binding : options.groupListeners) {
            if (binding == null || !binding.enabled || !groupNames.contains(binding.group)) continue;
            validateListenerPort(binding.port, ports);
            listeners.add(listener("group-mixed-" + index++, binding.port, binding.group, binding.allowLan));
        }
        return listeners;
    }

    private void validateListenerPort(int port, Set<Integer> used) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("监听端口必须在 1-65535 之间");
        if (!used.add(port)) throw new IllegalArgumentException("监听端口 " + port + " 与现有端口冲突");
    }

    private Map<String, Object> listener(String name, int port, String proxy, boolean allowLan) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", name);
        value.put("type", "mixed");
        value.put("listen", allowLan ? "0.0.0.0" : "127.0.0.1");
        value.put("port", port);
        value.put("proxy", proxy);
        value.put("udp", true);
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBaseConfig(String yamlText) {
        Map<String, Object> root = new LinkedHashMap<>();
        if (blank(yamlText)) return root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(20);
            options.setCodePointLimit(2 * 1024 * 1024);
            Object value = new Yaml(new SafeConstructor(options)).load(yamlText);
            if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("基础配置 YAML 顶层必须是对象");
            for (Map.Entry<?, ?> entry : map.entrySet()) root.put(String.valueOf(entry.getKey()), entry.getValue());
            root.remove("proxies"); root.remove("proxy-groups"); root.remove("rule-providers"); root.remove("rules");
            return root;
        } catch (RuntimeException error) {
            if (error instanceof IllegalArgumentException) throw error;
            throw new IllegalArgumentException("基础配置 YAML 无效：" + error.getMessage());
        }
    }

    private Map<String, String> resolveModuleNames(ConfigOptions options) {
        Map<String, String> names = new LinkedHashMap<>();
        for (ModuleCatalog.Module module : ModuleCatalog.all()) {
            String override = options.groupNameOverrides.get(module.id);
            names.put(module.id, blank(override) ? module.name : override.trim());
        }
        return names;
    }

    private Map<String, Object> defaultDns() {
        Map<String, Object> dns = new LinkedHashMap<>();
        dns.put("enable", true);
        dns.put("listen", "127.0.0.1:5335");
        dns.put("ipv6", true);
        dns.put("use-system-hosts", false);
        dns.put("enhanced-mode", "fake-ip");
        dns.put("fake-ip-range", "198.18.0.1/16");
        dns.put("respect-rules", true);
        dns.put("default-nameserver", Arrays.asList("223.5.5.5", "119.29.29.29", "8.8.8.8"));
        dns.put("nameserver", Arrays.asList("https://dns.alidns.com/dns-query", "https://doh.pub/dns-query", "https://cloudflare-dns.com/dns-query"));
        dns.put("proxy-server-nameserver", Arrays.asList("223.5.5.5", "119.29.29.29"));
        dns.put("fake-ip-filter", Arrays.asList("+.lan", "+.local", "geosite:private", "geosite:cn"));
        return dns;
    }

    private Map<String, Object> profile() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("store-selected", true); value.put("store-fake-ip", false); return value;
    }

    private Map<String, Object> sniffer() {
        Map<String, Object> sniff = new LinkedHashMap<>();
        sniff.put("TLS", Collections.singletonMap("ports", Arrays.asList(443, 8443)));
        sniff.put("HTTP", Collections.singletonMap("ports", Arrays.asList(80, "8080-8880")));
        sniff.put("QUIC", Collections.singletonMap("ports", Arrays.asList(443, 8443)));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("enable", true); value.put("parse-pure-ip", true); value.put("sniff", sniff); return value;
    }

    private DumperOptions dumpOptions() {
        DumperOptions value = new DumperOptions();
        value.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        value.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        value.setPrettyFlow(true); value.setIndent(2); value.setIndicatorIndent(2);
        value.setIndentWithIndicator(true); value.setSplitLines(false); value.setWidth(120);
        return value;
    }

    private Yaml yaml() { return new Yaml(dumpOptions()); }

    private static List<Map<String, Object>> cloneNodes(List<Map<String, Object>> nodes) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> node : nodes) out.add(new LinkedHashMap<>(node));
        return out;
    }

    private static List<Map<String, Object>> prepareNodes(List<Map<String, Object>> source, ConfigOptions options) {
        List<Map<String, Object>> nodes = cloneNodes(source);
        nodes.removeIf(node -> options.excludedNodeNames.contains(String.valueOf(node.get("name"))));
        Set<String> used = new LinkedHashSet<>();
        for (Map<String, Object> node : nodes) {
            String original = String.valueOf(node.get("name"));
            String base = options.nodeNameOverrides.get(original);
            if (blank(base)) base = original;
            String name = RegionCatalog.localizeNodeName(base.trim());
            int suffix = 2;
            while (!used.add(name)) name = base.trim() + " (" + suffix++ + ")";
            node.put("name", name);
        }
        return nodes;
    }

    private static List<Map<String, Object>> outputNodes(List<Map<String, Object>> nodes) {
        List<Map<String, Object>> out = cloneNodes(nodes);
        for (Map<String, Object> node : out) node.keySet().removeIf(key -> key.startsWith("_subboost-"));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractListeners(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> listener = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) listener.put(String.valueOf(entry.getKey()), entry.getValue());
            out.add(listener);
        }
        return out;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (RuntimeException error) { return fallback; }
    }

    private static boolean isBuiltinRuleId(String id) {
        if ("cn".equals(id)) return true;
        for (ModuleCatalog.Module module : ModuleCatalog.all()) for (ModuleCatalog.Rule rule : module.rules) if (rule.id.equals(id)) return true;
        return false;
    }

    private static List<Map<String, Object>> reorderGroups(List<Map<String, Object>> groups, List<String> order) {
        if (order == null || order.isEmpty()) return groups;
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map<String, Object> group : groups) byName.put(String.valueOf(group.get("name")), group);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String name : order) {
            Map<String, Object> group = byName.remove(name);
            if (group != null) out.add(group);
        }
        out.addAll(byName.values());
        return out;
    }

    private static List<String> reorderRules(List<String> rules, List<String> keys, List<String> order) {
        if (order == null || order.isEmpty()) return rules;
        Map<String, String> byKey = new LinkedHashMap<>();
        for (int i = 0; i < rules.size(); i++) byKey.put(keys.get(i), rules.get(i));
        List<String> out = new ArrayList<>();
        for (String key : order) {
            String rule = byKey.remove(key);
            if (rule != null) out.add(rule);
        }
        out.addAll(byKey.values());
        return out;
    }

    private static List<String> names(List<Map<String, Object>> nodes) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> node : nodes) out.add(String.valueOf(node.get("name"))); return out;
    }

    private static List<String> unique(Object... values) {
        List<String> out = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof List<?> list) for (Object item : list) add(out, item);
            else add(out, value);
        }
        return out;
    }

    private static void add(List<String> out, Object value) {
        if (value == null) return; String text = String.valueOf(value).trim(); if (!text.isEmpty() && !out.contains(text)) out.add(text);
    }

    private static List<String> dedupe(List<String> values) { return unique(values); }
    private static List<String> existing(List<String> values, Set<String> allowed) {
        List<String> out = new ArrayList<>();
        if (values != null) for (String value : values) {
            String localized = RegionCatalog.localizeNodeName(value);
            if (allowed.contains(localized)) out.add(localized);
        }
        return dedupe(out);
    }

    private static List<String> localizeNodeReferences(List<String> values, Set<String> nodeNames) {
        List<String> out = new ArrayList<>();
        if (values == null) return out;
        for (String value : values) {
            String localized = RegionCatalog.localizeNodeName(value);
            out.add(nodeNames.contains(localized) ? localized : value);
        }
        return out;
    }
    private static String trimSlash(String value) { String out = value.trim(); while (out.endsWith("/")) out = out.substring(0, out.length() - 1); return out; }
    private static String resolveRuleUrl(String base, String path) { return path.startsWith("https://") ? path : base + "/" + path.replaceFirst("^/+", ""); }
    private static boolean validGroupType(String value) { return Arrays.asList("select", "url-test", "fallback", "load-balance", "direct-first", "reject-first").contains(value); }
    private static boolean validStrategy(String value) { return Arrays.asList("consistent-hashing", "round-robin", "sticky-sessions").contains(value); }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static Pattern regex(String value) {
        if (blank(value)) return null;
        try { return Pattern.compile(value, Pattern.CASE_INSENSITIVE); }
        catch (PatternSyntaxException error) { throw new IllegalArgumentException("高级筛选正则无效：" + value); }
    }

    private static boolean matchesSource(String name, List<Map<String, Object>> nodes, List<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) return true;
        for (Map<String, Object> node : nodes) {
            if (!name.equals(String.valueOf(node.get("name")))) continue;
            return sourceIds.contains(String.valueOf(node.get("_subboost-source")));
        }
        return false;
    }
}
