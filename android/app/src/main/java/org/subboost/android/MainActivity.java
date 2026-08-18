package org.subboost.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.content.pm.PackageManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.subboost.android.core.ConfigGenerator;
import org.subboost.android.core.ConfigOptions;
import org.subboost.android.core.LocalConfigServer;
import org.subboost.android.core.ParseResult;
import org.subboost.android.core.SubscriptionParser;
import org.subboost.android.core.WifiCallingAnalyzer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_OPEN = 1001;
    private static final int REQUEST_SAVE = 1002;
    private static final int REQUEST_ADVANCED = 1003;
    private static final int MAX_INPUT_BYTES = 8 * 1024 * 1024;
    private static final String SETTINGS_NAME = "subboost-settings";
    private static final String SHARE_TOKEN_KEY = "share-token";

    private final SubscriptionParser parser = new SubscriptionParser();
    private final ConfigGenerator generator = new ConfigGenerator();
    private ConfigOptions configOptions = new ConfigOptions();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private List<Map<String, Object>> nodes = Collections.emptyList();
    private String pendingSave = "";

    private EditText urlInput;
    private EditText sourceInput;
    private EditText outputInput;
    private TextView status;
    private TextView nodePreview;
    private ScrollView nodePreviewScroll;
    private ProgressBar progress;
    private Button fetchButton;
    private TextView localShareLink;
    private TextView templateModeSummary;
    private String localShareToken;
    private final List<Button> templateModeButtons = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        localShareToken = loadOrCreateLocalShareToken();
        if (savedInstanceState == null) {
            String savedConfig = getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE).getString("config", "");
            if (!savedConfig.isEmpty()) {
                try { configOptions = ConfigOptions.fromJson(savedConfig); }
                catch (RuntimeException ignored) { configOptions = new ConfigOptions(); }
            }
        }
        setContentView(buildUi());
        if (savedInstanceState != null) {
            sourceInput.setText(savedInstanceState.getString("source", ""));
            outputInput.setText(savedInstanceState.getString("output", ""));
            urlInput.setText(savedInstanceState.getString("url", ""));
            String configJson = savedInstanceState.getString("config", "");
            if (!configJson.isEmpty()) {
                try { configOptions = ConfigOptions.fromJson(configJson); }
                catch (RuntimeException ignored) { configOptions = new ConfigOptions(); }
            }
            if (!isBlank(sourceInput.getText().toString())) parseSource(false);
        }
        refreshTemplateModeUi();
    }

    private View buildUi() {
        LinearLayout screen = vertical();

        TextView toolbar = new TextView(this);
        toolbar.setText("SubBoost · Android");
        toolbar.setTextColor(Color.WHITE);
        toolbar.setTextSize(21);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(20), 0, dp(20), 0);
        toolbar.setBackgroundColor(Color.rgb(36, 84, 58));
        screen.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = vertical();
        content.setPadding(dp(18), dp(18), dp(18), dp(32));
        scroll.addView(content);

        content.addView(section("1. 导入订阅"), topMargin(4));
        urlInput = edit("HTTPS 订阅地址（每行一个，可聚合多个来源）", true, dp(96));
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        configureInnerScrolling(urlInput, false);
        content.addView(urlInput, fixedHeight(96));

        fetchButton = button("获取订阅", this::fetchSubscription);
        content.addView(fetchButton, topMargin(8));

        sourceInput = edit("粘贴节点链接、Base64 订阅，或 Clash YAML", true, dp(160));
        configureInnerScrolling(sourceInput, true);
        content.addView(sourceInput, fixedHeightTopMargin(160, 12));

        content.addView(buttonRow(Arrays.asList(
                button("打开文件", view -> openFile()),
                button("解析节点", view -> parseSource(true)),
                button("清空", view -> clearAll())
        )), topMargin(8));

        LinearLayout resultHeader = horizontal();
        status = text("等待导入", 14);
        status.setTextColor(Color.rgb(60, 122, 87));
        resultHeader.addView(status, new LinearLayout.LayoutParams(0, dp(42), 1));
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        resultHeader.addView(progress, new LinearLayout.LayoutParams(dp(36), dp(36)));
        content.addView(resultHeader, topMargin(6));

        nodePreview = text("", 13);
        nodePreview.setLineSpacing(0, 1.15f);
        nodePreview.setTextIsSelectable(true);
        nodePreview.setPadding(dp(10), dp(8), dp(10), dp(8));
        nodePreviewScroll = new ScrollView(this);
        nodePreviewScroll.setFillViewport(true);
        nodePreviewScroll.setVerticalScrollBarEnabled(true);
        nodePreviewScroll.setVisibility(View.GONE);
        nodePreviewScroll.addView(nodePreview, matchWrap());
        nodePreviewScroll.setOnTouchListener((view, event) -> {
            boolean touching = event.getActionMasked() != android.view.MotionEvent.ACTION_UP
                    && event.getActionMasked() != android.view.MotionEvent.ACTION_CANCEL;
            view.getParent().requestDisallowInterceptTouchEvent(touching);
            return false;
        });
        content.addView(nodePreviewScroll, fixedHeightTopMargin(132, 4));

        content.addView(section("2. 生成配置"), topMargin(18));
        content.addView(text("默认模式", 14), topMargin(8));
        LinearLayout templateModes = horizontal();
        templateModes.addView(templateModeButton("精简版", "minimal"), weightedButton(0));
        templateModes.addView(templateModeButton("标准版", "standard"), weightedButton(6));
        templateModes.addView(templateModeButton("完整版", "full"), weightedButton(6));
        content.addView(templateModes, topMargin(6));
        templateModeSummary = text("", 13);
        content.addView(templateModeSummary, topMargin(5));
        content.addView(button("高级设置", view -> openAdvancedSettings()), topMargin(8));
        content.addView(button("生成 Mihomo YAML", view -> generateConfig()), topMargin(8));

        outputInput = edit("生成的 config.yaml 会显示在这里", true, dp(190));
        configureInnerScrolling(outputInput, true);
        outputInput.setTextSize(12);
        outputInput.setTypeface(android.graphics.Typeface.MONOSPACE);
        content.addView(outputInput, fixedHeightTopMargin(190, 12));

        content.addView(buttonRow(Arrays.asList(
                button("复制", view -> copyOutput()),
                button("保存文件", view -> saveOutput())
        )), topMargin(8));

        content.addView(section("3. 局域网本地连接"), topMargin(22));
        TextView shareHint = text("生成配置后，可启动带令牌的 HTTP 链接供局域网设备导入。令牌会保存在本机，重新打开 App、生成配置并启动连接后，原链接仍可继续使用。", 13);
        shareHint.setLineSpacing(0, 1.15f);
        content.addView(shareHint, matchWrap());
        localShareLink = text("服务未启动", 13);
        localShareLink.setTextIsSelectable(true);
        localShareLink.setTypeface(android.graphics.Typeface.MONOSPACE);
        content.addView(localShareLink, topMargin(8));
        content.addView(buttonRow(Arrays.asList(
                button("启动/更新连接", view -> startLocalShare()),
                button("复制链接", view -> copyLocalShareLink()),
                button("更换令牌", view -> rotateLocalShareToken()),
                button("停止", view -> stopLocalShare())
        )), topMargin(6));

        screen.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        applySystemBarInsets(screen, toolbar, scroll);
        refreshTemplateModeUi();
        refreshLocalShareUi();
        return screen;
    }

    private Button templateModeButton(String label, String template) {
        Button button = button(label, view -> applyTemplateMode(template));
        button.setTag(template);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        templateModeButtons.add(button);
        return button;
    }

    private void applyTemplateMode(String template) {
        configOptions.applyTemplate(template);
        getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE)
                .edit()
                .putString("config", configOptions.toJson())
                .apply();
        outputInput.setText("");
        status.setText("已切换为“" + templateName(template) + "”默认模式");
        refreshTemplateModeUi();
    }

    private void refreshTemplateModeUi() {
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int selectedColor = Color.rgb(36, 84, 58);
        int idleColor = night ? Color.rgb(48, 54, 50) : Color.rgb(235, 240, 236);
        int idleTextColor = night ? Color.rgb(225, 232, 227) : selectedColor;
        for (Button button : templateModeButtons) {
            boolean selected = String.valueOf(button.getTag()).equals(configOptions.template);
            GradientDrawable background = new GradientDrawable();
            background.setColor(selected ? selectedColor : idleColor);
            background.setCornerRadius(dp(6));
            background.setStroke(dp(1), selected ? selectedColor : Color.rgb(153, 171, 159));
            button.setBackground(background);
            button.setTextColor(selected ? Color.WHITE : idleTextColor);
            button.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            button.setSelected(selected);
            button.setActivated(selected);
            button.setContentDescription(button.getText() + (selected ? "，已选择" : ""));
        }
        if (templateModeSummary != null) {
            int count = configOptions.enabledModules == null ? 0 : configOptions.enabledModules.size();
            String advanced = configOptions.advancedMode ? " · 高级设置已启用" : "";
            templateModeSummary.setText(templateName(configOptions.template) + " · " + count + " 个策略组" + advanced);
        }
    }

    private void fetchSubscription(View ignored) {
        List<String> urls = new ArrayList<>();
        for (String line : urlInput.getText().toString().split("\\R")) if (!line.trim().isEmpty()) urls.add(line.trim());
        if (urls.isEmpty()) {
            show("请至少填写一个 HTTPS 订阅地址");
            return;
        }
        if (urls.stream().anyMatch(value -> !value.startsWith("https://"))) {
            show("为保护订阅凭据，只允许 HTTPS 地址");
            return;
        }
        setBusy(true, "正在获取 " + urls.size() + " 个订阅…");
        executor.execute(() -> {
            List<Map<String, Object>> aggregated = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            List<String> contents = new ArrayList<>();
            Map<String, Integer> sourceCounts = new java.util.LinkedHashMap<>();
            for (String value : urls) {
                try {
                    String content = download(value);
                    contents.add(content);
                    ParseResult parsed = parser.parse(content);
                    String host = new URL(value).getHost();
                    int count = sourceCounts.getOrDefault(host, 0) + 1;
                    sourceCounts.put(host, count);
                    String sourceId = count == 1 ? host : host + "#" + count;
                    for (Map<String, Object> item : parsed.nodes()) {
                        Map<String, Object> node = new java.util.LinkedHashMap<>(item);
                        node.put("_subboost-source", sourceId);
                        aggregated.add(node);
                    }
                    for (String error : parsed.errors()) errors.add(sourceId + "：" + error);
                } catch (Exception error) {
                    errors.add(sourceLabel(value) + "：" + message(error));
                }
            }
            List<Map<String, Object>> unique = uniqueNodeNames(aggregated);
            runOnUiThread(() -> {
                sourceInput.setText(String.join("\n", contents));
                nodes = unique;
                setBusy(false, unique.isEmpty() ? "订阅获取失败" : "多订阅聚合完成");
                renderParseResult(errors, true);
            });
        });
    }

    private String download(String value) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(value).openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(20_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "text/plain, application/yaml, text/yaml, */*");
        connection.setRequestProperty("User-Agent", "SubBoost-Android/1.0");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) { connection.disconnect(); throw new IOException("服务器返回 HTTP " + code); }
        try (InputStream input = connection.getInputStream()) { return readLimited(input); }
        finally { connection.disconnect(); }
    }

    private String sourceLabel(String value) {
        try { return new URL(value).getHost(); }
        catch (Exception error) { return "订阅来源"; }
    }

    private void parseSource(boolean notify) {
        String source = sourceInput.getText().toString();
        if (isBlank(source)) {
            nodes = Collections.emptyList();
            status.setText("请先粘贴或导入订阅内容");
            nodePreview.setText("");
            nodePreviewScroll.setVisibility(View.GONE);
            return;
        }
        ParseResult result = parser.parse(source);
        List<Map<String, Object>> manual = new ArrayList<>();
        for (Map<String, Object> item : result.nodes()) {
            Map<String, Object> node = new java.util.LinkedHashMap<>(item);
            node.put("_subboost-source", "manual");
            manual.add(node);
        }
        nodes = manual;
        renderParseResult(result.errors(), notify);
    }

    private void renderParseResult(List<String> errors, boolean notify) {
        String summary = "已解析 " + nodes.size() + " 个节点";
        if (!errors.isEmpty()) summary += "，" + errors.size() + " 项失败";
        status.setText(summary);

        List<String> lines = new ArrayList<>();
        int ready = 0;
        int unknown = 0;
        int unsupported = 0;
        for (Map<String, Object> node : nodes) {
            WifiCallingAnalyzer.Status wifiStatus = WifiCallingAnalyzer.analyze(node).status;
            if (wifiStatus == WifiCallingAnalyzer.Status.READY) ready++;
            else if (wifiStatus == WifiCallingAnalyzer.Status.UNKNOWN) unknown++;
            else unsupported++;
        }
        if (!nodes.isEmpty()) {
            lines.add("WiFi Calling 配置分析：可尝试 " + ready + " · 待确认 " + unknown + " · 不支持 " + unsupported);
            lines.add("判断条件：节点支持 UDP；出口 UDP 500/4500 仍需在代理客户端中实测。");
        }
        for (int i = 0; i < nodes.size(); i++) {
            Map<String, Object> node = nodes.get(i);
            String source = String.valueOf(node.getOrDefault("_subboost-source", "manual"));
            WifiCallingAnalyzer.Analysis wifi = WifiCallingAnalyzer.analyze(node);
            lines.add("• " + node.get("name") + "  [" + node.get("type") + " · " + source + "]\n  WiFi Calling：" + wifi.message);
        }
        for (int i = 0; i < Math.min(errors.size(), 3); i++) lines.add("⚠ " + errors.get(i));
        nodePreview.setText(String.join("\n", lines));
        nodePreviewScroll.setVisibility(lines.isEmpty() ? View.GONE : View.VISIBLE);
        nodePreviewScroll.scrollTo(0, 0);
        if (notify) show(summary);
    }

    private List<Map<String, Object>> uniqueNodeNames(List<Map<String, Object>> input) {
        List<Map<String, Object>> out = new ArrayList<>();
        java.util.LinkedHashSet<String> used = new java.util.LinkedHashSet<>();
        for (Map<String, Object> raw : input) {
            Map<String, Object> node = new java.util.LinkedHashMap<>(raw);
            String base = String.valueOf(node.get("name")).trim();
            String name = base;
            int suffix = 2;
            while (!used.add(name)) name = base + " (" + suffix++ + ")";
            node.put("name", name);
            out.add(node);
        }
        return out;
    }

    private void generateConfig() {
        if (nodes.isEmpty()) parseSource(false);
        try {
            String yaml = generator.generate(nodes, configOptions);
            outputInput.setText(yaml);
            outputInput.setSelection(0);
            if (LocalConfigServer.get().isRunning()) LocalConfigServer.get().update(yaml);
            status.setText("配置已生成，共 " + nodes.size() + " 个节点");
            show("config.yaml 已生成");
        } catch (IllegalArgumentException error) {
            show(message(error));
        }
    }

    private void openFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_OPEN);
    }

    private void saveOutput() {
        pendingSave = outputInput.getText().toString();
        if (isBlank(pendingSave)) {
            show("请先生成配置");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/yaml");
        intent.putExtra(Intent.EXTRA_TITLE, "config.yaml");
        startActivityForResult(intent, REQUEST_SAVE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQUEST_ADVANCED) {
            try {
                String json = data.getStringExtra(AdvancedSettingsActivity.EXTRA_CONFIG);
                configOptions = ConfigOptions.fromJson(json);
                if (LocalConfigServer.get().isRunning() && LocalConfigServer.get().port() != configOptions.localSharePort) {
                    LocalConfigServer.get().stop();
                    stopService(new Intent(this, LocalShareService.class));
                }
                getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE).edit().putString("config", configOptions.toJson()).apply();
                outputInput.setText("");
                status.setText("已应用“" + templateName(configOptions.template) + "”模板" + (configOptions.advancedMode ? "及高级模式" : ""));
                refreshTemplateModeUi();
                show("配置设置已应用");
                refreshLocalShareUi();
            } catch (RuntimeException error) {
                show("设置读取失败：" + message(error));
            }
            return;
        }
        if (data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQUEST_OPEN) {
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException("无法读取文件");
                    sourceInput.setText(readLimited(input));
                }
                parseSource(true);
            } else if (requestCode == REQUEST_SAVE) {
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new IOException("无法写入文件");
                    output.write(pendingSave.getBytes(StandardCharsets.UTF_8));
                }
                show("config.yaml 已保存");
            }
        } catch (Exception error) {
            show("文件操作失败：" + message(error));
        }
    }

    private void copyOutput() {
        String value = outputInput.getText().toString();
        if (isBlank(value)) { show("请先生成配置"); return; }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("SubBoost config.yaml", value));
        show("已复制到剪贴板");
    }

    private void openAdvancedSettings() {
        Intent intent = new Intent(this, AdvancedSettingsActivity.class);
        intent.putExtra(AdvancedSettingsActivity.EXTRA_CONFIG, configOptions.toJson());
        startActivityForResult(intent, REQUEST_ADVANCED);
    }

    private void startLocalShare() {
        String yaml = outputInput.getText().toString();
        if (isBlank(yaml)) { show("请先生成配置"); return; }
        try {
            LocalConfigServer.get().start(configOptions.localSharePort, yaml, localShareToken());
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 2001);
            }
            Intent service = new Intent(this, LocalShareService.class).setAction(LocalShareService.ACTION_START);
            startForegroundService(service);
            refreshLocalShareUi();
            show("局域网本地连接已启动");
        } catch (Exception error) {
            LocalConfigServer.get().stop();
            stopService(new Intent(this, LocalShareService.class));
            show("启动失败：" + message(error));
        }
    }

    private void stopLocalShare() {
        LocalConfigServer.get().stop();
        stopService(new Intent(this, LocalShareService.class));
        refreshLocalShareUi();
        show("局域网链接已停止");
    }

    private void copyLocalShareLink() {
        String link = currentLocalShareLink();
        if (isBlank(link)) { show("请先启动局域网链接"); return; }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("SubBoost LAN config link", link));
        show("局域网链接已复制");
    }

    private void rotateLocalShareToken() {
        String token = LocalConfigServer.newToken();
        getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE).edit().putString(SHARE_TOKEN_KEY, token).commit();
        localShareToken = token;
        if (LocalConfigServer.get().isRunning()) {
            try { LocalConfigServer.get().start(LocalConfigServer.get().port(), outputInput.getText().toString(), token); }
            catch (Exception error) { show("更换令牌失败：" + message(error)); return; }
        }
        refreshLocalShareUi();
        show("访问令牌已更换，旧链接已失效");
    }

    private String localShareToken() {
        return localShareToken;
    }

    private String loadOrCreateLocalShareToken() {
        SharedPreferences settings = getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE);
        String token = settings.getString(SHARE_TOKEN_KEY, "");
        if (isBlank(token)) {
            token = LocalConfigServer.newToken();
            settings.edit().putString(SHARE_TOKEN_KEY, token).commit();
        }
        return token;
    }

    private String currentLocalShareLink() {
        if (!LocalConfigServer.get().isRunning()) return "";
        String address = LocalConfigServer.findLanIpv4();
        if (isBlank(address)) return "";
        return LocalConfigServer.get().link(address);
    }

    private void refreshLocalShareUi() {
        if (localShareLink == null) return;
        if (!LocalConfigServer.get().isRunning()) {
            localShareLink.setText("服务未启动 · 配置端口 " + configOptions.localSharePort);
            return;
        }
        String link = currentLocalShareLink();
        if (isBlank(link)) localShareLink.setText("服务已启动，但未检测到可用的局域网 IPv4 地址");
        else localShareLink.setText(link + "\n令牌已保存在本机；重新启动连接后可继续使用此链接");
    }

    private void clearAll() {
        sourceInput.setText("");
        outputInput.setText("");
        nodePreview.setText("");
        nodePreviewScroll.setVisibility(View.GONE);
        status.setText("等待导入");
        nodes = Collections.emptyList();
    }

    private String readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > MAX_INPUT_BYTES) throw new IOException("内容超过 8 MiB 限制");
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private void setBusy(boolean busy, String message) {
        fetchButton.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        status.setText(message);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("source", sourceInput.getText().toString());
        state.putString("output", outputInput.getText().toString());
        state.putString("url", urlInput.getText().toString());
        state.putString("config", configOptions.toJson());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLocalShareUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView section(String value) {
        TextView view = text(value, 18);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        return view;
    }

    private EditText edit(String hint, boolean multiline, int height) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setMinHeight(height);
        input.setMaxHeight(multiline ? dp(420) : height);
        input.setSingleLine(!multiline);
        if (multiline) input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return input;
    }

    private void configureInnerScrolling(EditText input, boolean horizontal) {
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setHorizontallyScrolling(horizontal);
        input.setHorizontalScrollBarEnabled(horizontal);
        input.setVerticalScrollBarEnabled(true);
        input.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        input.setOnTouchListener((view, event) -> {
            boolean touching = event.getActionMasked() != android.view.MotionEvent.ACTION_UP
                    && event.getActionMasked() != android.view.MotionEvent.ACTION_CANCEL;
            view.getParent().requestDisallowInterceptTouchEvent(touching);
            return false;
        });
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

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private View buttonRow(List<Button> buttons) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = horizontal();
        for (Button button : buttons) row.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        scroll.addView(row);
        return scroll;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams topMargin(int value) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(value);
        return params;
    }

    private LinearLayout.LayoutParams weightedButton(int startMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
        params.leftMargin = dp(startMargin);
        return params;
    }

    private LinearLayout.LayoutParams fixedHeightTopMargin(int height, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height));
        params.topMargin = dp(margin);
        return params;
    }

    private LinearLayout.LayoutParams fixedHeight(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void show(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private static String message(Throwable error) {
        return isBlank(error.getMessage()) ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String templateName(String value) {
        if ("minimal".equals(value)) return "精简版";
        if ("full".equals(value)) return "完整版";
        return "标准版";
    }
}
