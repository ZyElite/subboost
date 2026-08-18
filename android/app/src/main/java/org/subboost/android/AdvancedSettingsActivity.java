package org.subboost.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.subboost.android.core.ConfigOptions;
import org.subboost.android.core.ModuleCatalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Full template and advanced configuration editor. */
public final class AdvancedSettingsActivity extends Activity {
    public static final String EXTRA_CONFIG = "config_json";

    private ConfigOptions options;
    private Spinner templateSpinner;
    private Switch advancedSwitch;
    private TextView moduleSummary;
    private EditText mixedPort;
    private Switch allowLan;
    private EditText testUrl;
    private EditText testInterval;
    private EditText ruleBaseUrl;
    private EditText localSharePort;
    private CheckBox cnNoResolve;
    private CheckBox experimentalCn;

    private Spinner advancedGroup;
    private Spinner groupType;
    private Spinner strategy;
    private EditText regions;
    private EditText sourceIds;
    private EditText includeRegex;
    private EditText excludeRegex;
    private EditText members;
    private EditText extraMembers;
    private EditText excludedMembers;
    private String editingGroupId;
    private boolean groupSpinnerReady;
    private boolean templateSpinnerReady;

    private EditText customGroups;
    private EditText customRuleSets;
    private EditText customRules;
    private EditText dialers;
    private EditText groupListeners;
    private EditText nodeListeners;
    private EditText nodeOverrides;
    private EditText excludedNodes;
    private EditText proxyGroupOrder;
    private EditText ruleOrder;
    private EditText groupOverrides;
    private EditText disabledRules;
    private EditText ruleTargets;
    private EditText baseYaml;
    private EditText templateJson;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            String json = getIntent().getStringExtra(EXTRA_CONFIG);
            options = blank(json) ? new ConfigOptions() : ConfigOptions.fromJson(json);
        } catch (RuntimeException error) {
            options = new ConfigOptions();
            Toast.makeText(this, "配置读取失败，已恢复默认值", Toast.LENGTH_LONG).show();
        }
        setContentView(buildUi());
        loadOptions();
    }

    private View buildUi() {
        LinearLayout screen = vertical();
        TextView toolbar = text("模板与高级模式", 21);
        toolbar.setTextColor(Color.WHITE);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(20), 0, dp(20), 0);
        toolbar.setBackgroundColor(Color.rgb(36, 84, 58));
        screen.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = vertical();
        content.setPadding(dp(18), dp(14), dp(18), dp(36));
        scroll.addView(content);

        content.addView(section("内置模板"), top(4));
        templateSpinner = spinner(Arrays.asList("精简版 · 基础分流", "标准版 · 常用服务", "完整版 · 全部分流"));
        content.addView(templateSpinner, match());
        templateSpinner.setOnItemSelectedListener(new SimpleSelection() {
            @Override public void selected(int position) {
                if (!templateSpinnerReady) { templateSpinnerReady = true; return; }
                options.applyTemplate(position == 0 ? "minimal" : position == 1 ? "standard" : "full");
                updateModuleSummary();
            }
        });
        moduleSummary = text("", 13);
        content.addView(moduleSummary, top(4));
        content.addView(button("选择启用的策略组", view -> selectModules()), top(5));

        advancedSwitch = new Switch(this);
        advancedSwitch.setText("启用策略组高级模式");
        content.addView(advancedSwitch, top(12));

        content.addView(section("基础参数"), top(18));
        mixedPort = field("Mixed 端口（1-65535）", false, dp(52));
        mixedPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        content.addView(mixedPort, match());
        allowLan = new Switch(this);
        allowLan.setText("允许局域网访问");
        content.addView(allowLan, top(4));
        testUrl = field("策略组测速 URL", false, dp(52)); content.addView(testUrl, top(6));
        testInterval = field("测速间隔（秒）", false, dp(52)); testInterval.setInputType(InputType.TYPE_CLASS_NUMBER); content.addView(testInterval, top(6));
        ruleBaseUrl = field("远程规则集基础 URL", false, dp(52)); content.addView(ruleBaseUrl, top(6));
        localSharePort = field("局域网导入链接端口（1024-65535）", false, dp(52));
        localSharePort.setInputType(InputType.TYPE_CLASS_NUMBER); content.addView(localSharePort, top(6));
        cnNoResolve = check("国内 IP 规则使用 no-resolve"); content.addView(cnNoResolve, top(4));
        experimentalCn = check("额外启用 geosite/cn 规则集"); content.addView(experimentalCn, top(0));

        content.addView(section("策略组高级设置"), top(20));
        content.addView(help("选择任一内置策略组，可修改组类型、负载均衡算法、地区/正则筛选和精确成员顺序。成员填写节点名、策略组名、DIRECT 或 REJECT，以逗号分隔。"), match());
        List<String> groupLabels = new ArrayList<>();
        for (ModuleCatalog.Module module : ModuleCatalog.all()) groupLabels.add(module.name + "  [" + module.id + "]");
        advancedGroup = spinner(groupLabels); content.addView(advancedGroup, top(6));
        groupType = spinner(Arrays.asList("select", "url-test", "fallback", "load-balance", "direct-first", "reject-first")); content.addView(groupType, top(4));
        strategy = spinner(Arrays.asList("consistent-hashing", "round-robin", "sticky-sessions")); content.addView(strategy, top(4));
        regions = field("地区筛选代号：hk,jp,sg,us,tw,kr,uk,de,fr,ca,au,other", false, dp(52));
        content.addView(regions, top(4));
        sourceIds = field("来源 ID（多订阅筛选，逗号分隔）", false, dp(52)); content.addView(sourceIds, top(4));
        includeRegex = field("节点名包含正则", false, dp(52)); content.addView(includeRegex, top(4));
        excludeRegex = field("节点名排除正则", false, dp(52)); content.addView(excludeRegex, top(4));
        members = field("精确成员与顺序（留空使用默认）", true, dp(78)); content.addView(members, top(4));
        extraMembers = field("额外成员", true, dp(68)); content.addView(extraMembers, top(4));
        excludedMembers = field("排除成员", true, dp(68)); content.addView(excludedMembers, top(4));
        content.addView(button("保存当前策略组高级设置", view -> saveGroupEditor()), top(5));
        advancedGroup.setOnItemSelectedListener(new SimpleSelection() {
            @Override public void selected(int position) {
                String next = ModuleCatalog.all().get(position).id;
                if (groupSpinnerReady && editingGroupId != null) saveGroupEditor(editingGroupId, false);
                editingGroupId = next;
                loadGroupEditor(next);
                groupSpinnerReady = true;
            }
        });

        customGroups = block(content, "自定义策略组", "每行：名称|类型|算法|地区代号|包含正则|排除正则|成员", 110);
        customRuleSets = block(content, "自定义远程规则集", "每行：id|domain/ipcidr|路径或HTTPS地址|目标组|no-resolve", 110);
        customRules = block(content, "自定义规则", "每行：DOMAIN-SUFFIX|example.com|目标组|no-resolve", 110);
        dialers = block(content, "链式代理 / 中转组", "每行：组名|类型|算法|中转节点1,2|落地节点1,2", 110);
        groupListeners = block(content, "策略组监听端口", "每行：策略组名|端口|allow-lan", 95);
        nodeListeners = block(content, "节点监听端口", "每行：节点名|端口", 95);
        nodeOverrides = block(content, "节点批量改名", "每行：原节点名=新节点名", 95);
        excludedNodes = block(content, "删除节点", "填写不参与生成的节点名，使用逗号或换行分隔", 75);
        proxyGroupOrder = block(content, "策略组顺序", "按名称填写，以逗号分隔；未列出的组自动追加", 75);
        groupOverrides = block(content, "内置策略组改名", "每行：模块id=新名称，例如 ai=🧠 AI 专线", 95);
        disabledRules = block(content, "停用内置规则", "逗号分隔 module:rule，例如 google:google-ip", 75);
        ruleTargets = block(content, "内置规则目标改写", "每行：module:rule=目标策略组", 95);
        ruleOrder = block(content, "规则顺序", "逗号分隔：module:rule、custom:0、ruleset:id、cn:cn、special:match", 80);
        baseYaml = block(content, "基础与 DNS YAML", "可覆盖 mixed-port、dns、sniffer、profile 等；proxies/groups/rules 仍由应用生成", 210);

        content.addView(section("模板 JSON 导入导出"), top(20));
        content.addView(help("导出的 JSON 包含以上所有设置，可复制到另一台设备后载入。"), match());
        templateJson = field("subboost-android-config/v1 JSON", true, dp(190));
        templateJson.setTypeface(android.graphics.Typeface.MONOSPACE);
        content.addView(templateJson, top(5));
        LinearLayout jsonButtons = horizontal();
        jsonButtons.addView(button("导出当前设置", view -> exportJson()));
        jsonButtons.addView(button("从 JSON 载入", view -> importJson()));
        content.addView(jsonButtons, top(5));

        LinearLayout actions = horizontal();
        actions.setGravity(Gravity.END);
        actions.addView(button("取消", view -> finish()));
        actions.addView(button("应用设置", view -> applyAndFinish()));
        content.addView(actions, top(22));

        screen.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        applySystemBarInsets(screen, toolbar, scroll);
        return screen;
    }

    private void applySystemBarInsets(LinearLayout screen, TextView toolbar, ScrollView scroll) {
        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            toolbar.setPadding(dp(20), top, dp(20), 0);
            ViewGroup.LayoutParams toolbarParams = toolbar.getLayoutParams();
            toolbarParams.height = dp(64) + top;
            toolbar.setLayoutParams(toolbarParams);
            scroll.setPadding(0, 0, 0, bottom);
            return insets;
        });
    }

    private void loadOptions() {
        templateSpinner.setSelection(options.template.equals("minimal") ? 0 : options.template.equals("full") ? 2 : 1);
        advancedSwitch.setChecked(options.advancedMode);
        mixedPort.setText(String.valueOf(options.mixedPort));
        allowLan.setChecked(options.allowLan);
        testUrl.setText(options.testUrl);
        testInterval.setText(String.valueOf(options.testInterval));
        ruleBaseUrl.setText(options.ruleProviderBaseUrl);
        localSharePort.setText(String.valueOf(options.localSharePort));
        cnNoResolve.setChecked(options.cnIpNoResolve);
        experimentalCn.setChecked(options.experimentalCnRuleSet);
        customGroups.setText(formatCustomGroups());
        customRuleSets.setText(formatRuleSets());
        customRules.setText(formatCustomRules());
        dialers.setText(formatDialers());
        groupListeners.setText(formatGroupListeners());
        nodeListeners.setText(formatNodeListeners());
        nodeOverrides.setText(formatMap(options.nodeNameOverrides));
        excludedNodes.setText(ConfigOptions.join(options.excludedNodeNames));
        proxyGroupOrder.setText(ConfigOptions.join(options.proxyGroupOrder));
        groupOverrides.setText(formatMap(options.groupNameOverrides));
        disabledRules.setText(ConfigOptions.join(options.disabledBuiltinRules));
        ruleTargets.setText(formatMap(options.builtinRuleTargets));
        ruleOrder.setText(ConfigOptions.join(options.ruleOrder));
        baseYaml.setText(options.baseConfigYaml);
        templateJson.setText(options.toJson());
        updateModuleSummary();
        editingGroupId = ModuleCatalog.all().get(0).id;
        loadGroupEditor(editingGroupId);
    }

    private void selectModules() {
        List<ModuleCatalog.Module> catalog = ModuleCatalog.all();
        String[] labels = new String[catalog.size()];
        boolean[] checked = new boolean[catalog.size()];
        for (int i = 0; i < catalog.size(); i++) {
            labels[i] = catalog.get(i).name;
            checked[i] = options.enabledModules.contains(catalog.get(i).id);
        }
        new AlertDialog.Builder(this)
                .setTitle("启用策略组")
                .setMultiChoiceItems(labels, checked, (dialog, which, value) -> checked[which] = value)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    options.enabledModules.clear();
                    for (int i = 0; i < catalog.size(); i++) if (checked[i]) options.enabledModules.add(catalog.get(i).id);
                    options.normalize();
                    updateModuleSummary();
                }).show();
    }

    private void updateModuleSummary() {
        moduleSummary.setText("已启用 " + options.enabledModules.size() + " 个策略组（节点选择、WiFi Calling 和漏网之鱼为必选）");
    }

    private void saveGroupEditor() {
        saveGroupEditor(editingGroupId, true);
    }

    private void saveGroupEditor(String id, boolean notify) {
        if (id == null) return;
        ConfigOptions.GroupAdvanced value = new ConfigOptions.GroupAdvanced();
        value.groupType = String.valueOf(groupType.getSelectedItem());
        value.strategy = String.valueOf(strategy.getSelectedItem());
        value.regions = ConfigOptions.csv(regions.getText().toString());
        value.sourceIds = ConfigOptions.csv(sourceIds.getText().toString());
        value.includeRegex = includeRegex.getText().toString().trim();
        value.excludeRegex = excludeRegex.getText().toString().trim();
        value.members = ConfigOptions.csv(members.getText().toString());
        value.extraMembers = ConfigOptions.csv(extraMembers.getText().toString());
        value.excludedMembers = ConfigOptions.csv(excludedMembers.getText().toString());
        options.groupAdvanced.put(id, value);
        if (notify) toast("已保存 " + ModuleCatalog.byId(id).name + " 的高级设置");
    }

    private void loadGroupEditor(String id) {
        ModuleCatalog.Module module = ModuleCatalog.byId(id);
        ConfigOptions.GroupAdvanced value = options.groupAdvanced.get(id);
        select(groupType, value == null || blank(value.groupType) ? module.groupType : value.groupType);
        select(strategy, value == null ? "consistent-hashing" : value.strategy);
        regions.setText(value == null ? "" : ConfigOptions.join(value.regions));
        sourceIds.setText(value == null ? "" : ConfigOptions.join(value.sourceIds));
        includeRegex.setText(value == null ? "" : value.includeRegex);
        excludeRegex.setText(value == null ? "" : value.excludeRegex);
        members.setText(value == null ? "" : ConfigOptions.join(value.members));
        extraMembers.setText(value == null ? "" : ConfigOptions.join(value.extraMembers));
        excludedMembers.setText(value == null ? "" : ConfigOptions.join(value.excludedMembers));
    }

    private void collectOptions() {
        saveGroupEditor(editingGroupId, false);
        options.advancedMode = advancedSwitch.isChecked();
        options.mixedPort = number(mixedPort, "Mixed 端口");
        options.allowLan = allowLan.isChecked();
        options.testUrl = testUrl.getText().toString().trim();
        options.testInterval = number(testInterval, "测速间隔");
        options.ruleProviderBaseUrl = ruleBaseUrl.getText().toString().trim();
        options.localSharePort = number(localSharePort, "局域网链接端口");
        options.cnIpNoResolve = cnNoResolve.isChecked();
        options.experimentalCnRuleSet = experimentalCn.isChecked();
        options.customGroups = parseCustomGroups(customGroups.getText().toString());
        options.customRuleSets = parseRuleSets(customRuleSets.getText().toString());
        options.customRules = parseCustomRules(customRules.getText().toString());
        options.dialerGroups = parseDialers(dialers.getText().toString());
        options.groupListeners = parseGroupListeners(groupListeners.getText().toString());
        options.nodeListenerPorts = parseNodeListeners(nodeListeners.getText().toString());
        options.nodeNameOverrides = parseMap(nodeOverrides.getText().toString());
        options.excludedNodeNames = ConfigOptions.csv(excludedNodes.getText().toString().replace('\n', ','));
        options.proxyGroupOrder = ConfigOptions.csv(proxyGroupOrder.getText().toString().replace('\n', ','));
        options.groupNameOverrides = parseMap(groupOverrides.getText().toString());
        options.disabledBuiltinRules = ConfigOptions.csv(disabledRules.getText().toString().replace('\n', ','));
        options.builtinRuleTargets = parseMap(ruleTargets.getText().toString());
        options.ruleOrder = ConfigOptions.csv(ruleOrder.getText().toString().replace('\n', ','));
        options.baseConfigYaml = baseYaml.getText().toString();
        options.normalize();
    }

    private void applyAndFinish() {
        try {
            collectOptions();
            Intent result = new Intent();
            result.putExtra(EXTRA_CONFIG, options.toJson());
            setResult(RESULT_OK, result);
            finish();
        } catch (RuntimeException error) {
            toast(error.getMessage());
        }
    }

    private void exportJson() {
        try { collectOptions(); templateJson.setText(options.toJson()); toast("模板 JSON 已更新"); }
        catch (RuntimeException error) { toast(error.getMessage()); }
    }

    private void importJson() {
        try {
            options = ConfigOptions.fromJson(templateJson.getText().toString());
            groupSpinnerReady = false;
            templateSpinnerReady = false;
            loadOptions();
            toast("模板已载入");
        } catch (RuntimeException error) { toast("载入失败：" + error.getMessage()); }
    }

    private List<ConfigOptions.CustomGroup> parseCustomGroups(String raw) {
        List<ConfigOptions.CustomGroup> out = new ArrayList<>();
        int index = 0;
        for (String line : lines(raw)) {
            String[] p = columns(line, 2, "自定义策略组");
            ConfigOptions.CustomGroup value = new ConfigOptions.CustomGroup();
            value.id = "custom-" + index++;
            value.name = p[0]; value.groupType = p[1];
            if (p.length > 2 && !blank(p[2])) value.strategy = p[2];
            if (p.length > 3) value.advanced.regions = ConfigOptions.csv(p[3]);
            if (p.length > 4) value.advanced.includeRegex = p[4];
            if (p.length > 5) value.advanced.excludeRegex = p[5];
            if (p.length > 6) value.advanced.members = ConfigOptions.csv(p[6]);
            out.add(value);
        }
        return out;
    }

    private List<ConfigOptions.CustomRuleSet> parseRuleSets(String raw) {
        List<ConfigOptions.CustomRuleSet> out = new ArrayList<>();
        for (String line : lines(raw)) {
            String[] p = columns(line, 4, "自定义规则集");
            ConfigOptions.CustomRuleSet value = new ConfigOptions.CustomRuleSet();
            value.id = p[0]; value.behavior = p[1]; value.path = p[2]; value.target = p[3];
            value.noResolve = p.length > 4 && truthy(p[4]); out.add(value);
        }
        return out;
    }

    private List<ConfigOptions.CustomRule> parseCustomRules(String raw) {
        List<ConfigOptions.CustomRule> out = new ArrayList<>();
        for (String line : lines(raw)) {
            String[] p = columns(line, 3, "自定义规则");
            ConfigOptions.CustomRule value = new ConfigOptions.CustomRule();
            value.type = p[0]; value.value = p[1]; value.target = p[2];
            value.noResolve = p.length > 3 && truthy(p[3]); out.add(value);
        }
        return out;
    }

    private List<ConfigOptions.DialerGroup> parseDialers(String raw) {
        List<ConfigOptions.DialerGroup> out = new ArrayList<>();
        for (String line : lines(raw)) {
            String[] p = columns(line, 5, "中转组");
            ConfigOptions.DialerGroup value = new ConfigOptions.DialerGroup();
            value.name = p[0]; value.type = p[1]; value.strategy = p[2];
            value.relayNodes = ConfigOptions.csv(p[3]); value.targetNodes = ConfigOptions.csv(p[4]); out.add(value);
        }
        return out;
    }

    private List<ConfigOptions.Listener> parseGroupListeners(String raw) {
        List<ConfigOptions.Listener> out = new ArrayList<>();
        for (String line : lines(raw)) {
            String[] p = columns(line, 2, "策略组监听");
            ConfigOptions.Listener value = new ConfigOptions.Listener();
            value.group = p[0]; value.port = integer(p[1], "策略组监听端口");
            value.allowLan = p.length > 2 && truthy(p[2]); out.add(value);
        }
        return out;
    }

    private Map<String, Integer> parseNodeListeners(String raw) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String line : lines(raw)) {
            String[] p = columns(line, 2, "节点监听"); out.put(p[0], integer(p[1], "节点监听端口"));
        }
        return out;
    }

    private Map<String, String> parseMap(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : lines(raw)) {
            int eq = line.indexOf('=');
            if (eq < 1 || eq == line.length() - 1) throw new IllegalArgumentException("映射格式应为 key=value：" + line);
            out.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return out;
    }

    private String formatCustomGroups() {
        List<String> out = new ArrayList<>();
        for (ConfigOptions.CustomGroup v : options.customGroups) out.add(v.name + "|" + v.groupType + "|" + v.strategy + "|" + ConfigOptions.join(v.advanced.regions) + "|" + v.advanced.includeRegex + "|" + v.advanced.excludeRegex + "|" + ConfigOptions.join(v.advanced.members));
        return String.join("\n", out);
    }
    private String formatRuleSets() { List<String> out = new ArrayList<>(); for (ConfigOptions.CustomRuleSet v : options.customRuleSets) out.add(v.id+"|"+v.behavior+"|"+v.path+"|"+v.target+(v.noResolve?"|no-resolve":"")); return String.join("\n",out); }
    private String formatCustomRules() { List<String> out = new ArrayList<>(); for (ConfigOptions.CustomRule v : options.customRules) out.add(v.type+"|"+v.value+"|"+v.target+(v.noResolve?"|no-resolve":"")); return String.join("\n",out); }
    private String formatDialers() { List<String> out = new ArrayList<>(); for (ConfigOptions.DialerGroup v : options.dialerGroups) out.add(v.name+"|"+v.type+"|"+v.strategy+"|"+ConfigOptions.join(v.relayNodes)+"|"+ConfigOptions.join(v.targetNodes)); return String.join("\n",out); }
    private String formatGroupListeners() { List<String> out = new ArrayList<>(); for (ConfigOptions.Listener v : options.groupListeners) out.add(v.group+"|"+v.port+(v.allowLan?"|allow-lan":"")); return String.join("\n",out); }
    private String formatNodeListeners() { List<String> out = new ArrayList<>(); for (Map.Entry<String,Integer> v : options.nodeListenerPorts.entrySet()) out.add(v.getKey()+"|"+v.getValue()); return String.join("\n",out); }
    private String formatMap(Map<String,String> map) { List<String> out = new ArrayList<>(); for (Map.Entry<String,String> v : map.entrySet()) out.add(v.getKey()+"="+v.getValue()); return String.join("\n",out); }

    private EditText block(LinearLayout parent, String title, String hint, int height) {
        parent.addView(section(title), top(20)); parent.addView(help(hint), match());
        EditText value = field(hint, true, dp(height)); value.setGravity(Gravity.TOP | Gravity.START); parent.addView(value, top(4)); return value;
    }
    private EditText field(String hint, boolean multiline, int height) {
        EditText value = new EditText(this); value.setHint(hint); value.setMinHeight(height); value.setPadding(dp(10), dp(8), dp(10), dp(8));
        value.setSingleLine(!multiline); if (multiline) value.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); return value;
    }
    private Spinner spinner(List<String> values) { Spinner spinner = new Spinner(this); ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter); return spinner; }
    private void select(Spinner spinner, String value) { for (int i=0;i<spinner.getCount();i++) if (value.equals(String.valueOf(spinner.getItemAtPosition(i)))) { spinner.setSelection(i); return; } }
    private CheckBox check(String label) { CheckBox value = new CheckBox(this); value.setText(label); return value; }
    private Button button(String label, View.OnClickListener listener) { Button value = new Button(this); value.setText(label); value.setAllCaps(false); value.setOnClickListener(listener); return value; }
    private TextView section(String value) { TextView text = text(value,18); text.setTypeface(null,android.graphics.Typeface.BOLD); return text; }
    private TextView help(String value) { TextView text = text(value,13); text.setLineSpacing(0,1.15f); return text; }
    private TextView text(String value,float size) { TextView text = new TextView(this); text.setText(value); text.setTextSize(size); return text; }
    private LinearLayout vertical() { LinearLayout value = new LinearLayout(this); value.setOrientation(LinearLayout.VERTICAL); return value; }
    private LinearLayout horizontal() { LinearLayout value = new LinearLayout(this); value.setOrientation(LinearLayout.HORIZONTAL); value.setGravity(Gravity.CENTER_VERTICAL); return value; }
    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams top(int dp) { LinearLayout.LayoutParams value=match(); value.topMargin=dp(dp); return value; }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private int number(EditText value,String label) { return integer(value.getText().toString(),label); }
    private int integer(String raw,String label) { try { return Integer.parseInt(raw.trim()); } catch (RuntimeException error) { throw new IllegalArgumentException(label+"必须是整数"); } }
    private String[] columns(String line,int min,String label) { String[] value=line.split("\\|",-1); if(value.length<min) throw new IllegalArgumentException(label+"格式错误："+line); for(int i=0;i<value.length;i++) value[i]=value[i].trim(); return value; }
    private List<String> lines(String raw) { List<String> out=new ArrayList<>(); for(String line:raw.split("\\R")){ String value=line.trim(); if(!value.isEmpty()&&!value.startsWith("#")) out.add(value); } return out; }
    private boolean truthy(String value) { String v=value.trim().toLowerCase(); return v.equals("true")||v.equals("1")||v.equals("yes")||v.equals("allow-lan")||v.equals("no-resolve"); }
    private boolean blank(String value) { return value==null||value.trim().isEmpty(); }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_SHORT).show(); }

    private abstract static class SimpleSelection implements AdapterView.OnItemSelectedListener {
        public abstract void selected(int position);
        @Override public final void onItemSelected(AdapterView<?> parent, View view, int position, long id) { selected(position); }
        @Override public final void onNothingSelected(AdapterView<?> parent) { }
    }
}
