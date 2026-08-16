# SnakeYAML creates standard collection types; no application model is reflected.
-dontwarn org.yaml.snakeyaml.**

# Template JSON is a public, portable format; keep Gson field names stable in release builds.
-keepattributes Signature
-keep class org.subboost.android.core.ConfigOptions { *; }
-keep class org.subboost.android.core.ConfigOptions$* { *; }
