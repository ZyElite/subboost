package org.subboost.android.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Android mirror of the built-in proxy-group and rule catalog in @subboost/core. */
public final class ModuleCatalog {
    private static final List<Module> MODULES = new ArrayList<>();
    private static final Map<String, Module> BY_ID = new LinkedHashMap<>();

    static {
        add("select", "🚀 节点选择", "select", "");
        add("auto", "⚡ 自动选择", "url-test", "");
        add("ad", "🛑 广告拦截", "reject-first", "category-ads-all,domain,geosite/category-ads-all.mrs");
        add("ai", "🤖 AI 服务", "select", "openai,domain,geosite/openai.mrs;anthropic,domain,geosite/anthropic.mrs;category-ai-chat-!cn,domain,geosite/category-ai-chat-!cn.mrs");
        add("gemini", "✨ Gemini", "select", "google-gemini,domain,geosite/google-gemini.mrs");
        add("youtube", "📹 油管视频", "select", "youtube,domain,geosite/youtube.mrs");
        add("google", "🔍 谷歌服务", "select", "google,domain,geosite/google.mrs;google-ip,ipcidr,geoip/google.mrs,true");
        add("microsoft", "Ⓜ️ 微软服务", "select", "microsoft,domain,geosite/microsoft.mrs;onedrive,domain,geosite/onedrive.mrs");
        add("apple", "🍏 苹果服务", "select", "apple,domain,geosite/apple.mrs;icloud,domain,geosite/icloud.mrs");
        add("telegram", "📲 电报消息", "select", "telegram,domain,geosite/telegram.mrs;telegram-ip,ipcidr,geoip/telegram.mrs,true");
        add("twitter", "🐦 推特/X", "select", "twitter,domain,geosite/twitter.mrs;twitter-ip,ipcidr,geoip/twitter.mrs,true");
        add("meta", "📘 Meta 系", "select", "facebook,domain,geosite/facebook.mrs;instagram,domain,geosite/instagram.mrs;whatsapp,domain,geosite/whatsapp.mrs;facebook-ip,ipcidr,geoip/facebook.mrs,true");
        add("discord", "🎙️ Discord", "select", "discord,domain,geosite/discord.mrs");
        add("social-other", "💬 其他社交", "select", "tiktok,domain,geosite/tiktok.mrs;line,domain,geosite/line.mrs;reddit,domain,geosite/reddit.mrs;linkedin,domain,geosite/linkedin.mrs;snap,domain,geosite/snap.mrs;pinterest,domain,geosite/pinterest.mrs;tumblr,domain,geosite/tumblr.mrs");
        add("netflix", "🎬 奈飞", "select", "netflix,domain,geosite/netflix.mrs;netflix-ip,ipcidr,geoip/netflix.mrs,true");
        add("disney", "🏰 迪士尼+", "select", "disney,domain,geosite/disney.mrs");
        add("streaming-west", "📺 欧美流媒体", "select", "hbo,domain,geosite/hbo.mrs;hulu,domain,geosite/hulu.mrs;primevideo,domain,geosite/primevideo.mrs;apple-tvplus,domain,geosite/apple-tvplus.mrs;spotify,domain,geosite/spotify.mrs;twitch,domain,geosite/twitch.mrs;dazn,domain,geosite/dazn.mrs");
        add("streaming-asia", "🎌 亚洲流媒体", "select", "bahamut,domain,geosite/bahamut.mrs;biliintl,domain,geosite/biliintl.mrs;niconico,domain,geosite/niconico.mrs;abema,domain,geosite/abema.mrs;viu,domain,geosite/viu.mrs;kktv,domain,geosite/kktv.mrs");
        add("steam", "🎮 Steam", "select", "steam,domain,geosite/steam.mrs");
        add("gaming-pc", "🖥️ PC 游戏", "select", "epicgames,domain,geosite/epicgames.mrs;ea,domain,geosite/ea.mrs;ubisoft,domain,geosite/ubisoft.mrs;blizzard,domain,geosite/blizzard.mrs;gog,domain,geosite/gog.mrs;riot,domain,geosite/riot.mrs");
        add("gaming-console", "🎯 主机游戏", "select", "playstation,domain,geosite/playstation.mrs;xbox,domain,geosite/xbox.mrs;nintendo,domain,geosite/nintendo.mrs");
        add("github", "🐱 代码托管", "select", "github,domain,geosite/github.mrs;gitlab,domain,geosite/gitlab.mrs;atlassian,domain,geosite/atlassian.mrs");
        add("cloud", "☁️ 云服务", "select", "aws,domain,geosite/aws.mrs;azure,domain,geosite/azure.mrs;cloudflare,domain,geosite/cloudflare.mrs;digitalocean,domain,geosite/digitalocean.mrs;vercel,domain,geosite/vercel.mrs;netlify,domain,geosite/netlify.mrs;cloudflare-ip,ipcidr,geoip/cloudflare.mrs,true");
        add("dev-tools", "🛠️ 开发工具", "select", "docker,domain,geosite/docker.mrs;npmjs,domain,geosite/npmjs.mrs;jetbrains,domain,geosite/jetbrains.mrs;stackexchange,domain,geosite/stackexchange.mrs");
        add("storage", "💾 网盘存储", "select", "dropbox,domain,geosite/dropbox.mrs;notion,domain,geosite/notion.mrs");
        add("payment", "💳 支付平台", "select", "paypal,domain,geosite/paypal.mrs;stripe,domain,geosite/stripe.mrs;wise,domain,geosite/wise.mrs");
        add("crypto", "₿ 加密货币", "select", "binance,domain,geosite/binance.mrs");
        add("google-scholar", "🎓 谷歌学术", "select", "google-scholar,domain,geosite/google-scholar.mrs");
        add("education", "📚 教育学术", "select", "category-scholar-!cn,domain,geosite/category-scholar-!cn.mrs;coursera,domain,geosite/coursera.mrs;udemy,domain,geosite/udemy.mrs;edx,domain,geosite/edx.mrs;khanacademy,domain,geosite/khanacademy.mrs;wikimedia,domain,geosite/wikimedia.mrs");
        add("news", "📰 新闻资讯", "select", "bbc,domain,geosite/bbc.mrs;cnn,domain,geosite/cnn.mrs;nytimes,domain,geosite/nytimes.mrs;wsj,domain,geosite/wsj.mrs;bloomberg,domain,geosite/bloomberg.mrs");
        add("shopping", "🛒 海淘购物", "select", "amazon,domain,geosite/amazon.mrs;ebay,domain,geosite/ebay.mrs");
        add("adult", "🔞 成人内容", "select", "category-porn,domain,geosite/category-porn.mrs");
        add("private", "🏠 私有网络", "direct-first", "private,domain,geosite/private.mrs;private-ip,ipcidr,geoip/private.mrs,true");
        add("cn", "🔒 国内服务", "direct-first", "geolocation-cn,domain,geosite/geolocation-cn.mrs;cn-ip,ipcidr,geoip/cn.mrs,true");
        add("global", "🌍 非中国", "select", "geolocation-!cn,domain,geosite/geolocation-!cn.mrs");
        add("final", "🐟 漏网之鱼", "select", "");
    }

    private static void add(String id, String name, String type, String rulesText) {
        List<Rule> rules = new ArrayList<>();
        if (!rulesText.isEmpty()) {
            for (String item : rulesText.split(";")) {
                String[] value = item.split(",", -1);
                rules.add(new Rule(value[0], value[1], value[2], value.length > 3 && Boolean.parseBoolean(value[3])));
            }
        }
        Module module = new Module(id, name, type, rules);
        MODULES.add(module);
        BY_ID.put(id, module);
    }

    public static List<Module> all() { return Collections.unmodifiableList(MODULES); }
    public static Module byId(String id) { return BY_ID.get(id); }
    public static boolean isTemplate(String value) { return "minimal".equals(value) || "standard".equals(value) || "full".equals(value); }

    public static List<String> modulesForTemplate(String template) {
        if ("minimal".equals(template)) return Arrays.asList("select", "auto", "ad", "private", "cn", "global", "final");
        if ("standard".equals(template)) return Arrays.asList("select", "auto", "ad", "private", "cn", "global", "ai", "youtube", "google", "microsoft", "apple", "github", "telegram", "final");
        List<String> out = new ArrayList<>();
        for (Module module : MODULES) {
            if (!module.id.equals("adult") && !module.id.equals("gemini") && !module.id.equals("google-scholar")) out.add(module.id);
        }
        return out;
    }

    public static final class Module {
        public final String id;
        public final String name;
        public final String groupType;
        public final List<Rule> rules;
        Module(String id, String name, String groupType, List<Rule> rules) {
            this.id = id; this.name = name; this.groupType = groupType; this.rules = rules;
        }
        @Override public String toString() { return name; }
    }

    public static final class Rule {
        public final String id;
        public final String behavior;
        public final String path;
        public final boolean noResolve;
        Rule(String id, String behavior, String path, boolean noResolve) {
            this.id = id; this.behavior = behavior; this.path = path; this.noResolve = noResolve;
        }
    }

    private ModuleCatalog() { }
}
