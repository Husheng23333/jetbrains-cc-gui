package com.github.claudecodegui.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

/**
 * Merges the cc-switch "common config" (通用配置, stored in the settings table of
 * cc-switch.db) into the providers imported from cc-switch.
 *
 * <p>The common config is stored as a raw string whose format depends on the app type:
 * JSON for Claude ({@code common_config_claude}), TOML for Codex ({@code common_config_codex}).
 * Provider-specific values always win on conflict — cc-switch itself applies the common
 * config as a base layer beneath the provider config.
 */
final class CcSwitchCommonConfigMerger {

    static final String CLAUDE_APP_TYPE = "claude";
    static final String CODEX_APP_TYPE = "codex";

    private CcSwitchCommonConfigMerger() {
    }

    /**
     * Merges the common config into a single parsed provider (in place).
     *
     * @param provider          the parsed provider object; modified in place
     * @param appType           the cc-switch app_type ("claude" or "codex")
     * @param commonConfigRaw   the raw common config string, or null/blank when absent
     * @throws Exception when the common config cannot be parsed or merged
     */
    static void merge(JsonObject provider, String appType, String commonConfigRaw) throws Exception {
        if (provider == null || commonConfigRaw == null || commonConfigRaw.trim().isEmpty()) {
            return;
        }
        if (CODEX_APP_TYPE.equals(appType)) {
            mergeCodexProvider(provider, commonConfigRaw);
        } else {
            mergeClaudeProvider(provider, commonConfigRaw);
        }
    }

    /**
     * Claude: both the common config and the provider's settingsConfig are JSON objects.
     * The common config is merged beneath {@code settingsConfig} (provider wins).
     */
    private static void mergeClaudeProvider(JsonObject provider, String commonConfigRaw) throws Exception {
        JsonObject common = JsonParser.parseString(commonConfigRaw).getAsJsonObject();
        JsonObject settingsConfig = provider.has("settingsConfig") && provider.get("settingsConfig").isJsonObject()
                ? provider.getAsJsonObject("settingsConfig")
                : new JsonObject();
        overlayJsonValues(settingsConfig, common);
        provider.add("settingsConfig", settingsConfig);
    }

    /**
     * Codex: the common config is TOML, the provider keeps a raw TOML string in
     * {@code configToml}. Both are parsed to maps, merged (provider wins) and
     * serialized back into {@code configToml}.
     */
    private static void mergeCodexProvider(JsonObject provider, String commonConfigRaw) throws Exception {
        String providerToml = provider.has("configToml") && provider.get("configToml").isJsonPrimitive()
                ? provider.get("configToml").getAsString()
                : "";
        String mergedToml = CodexSettingsManager.mergeCommonConfigToml(commonConfigRaw, providerToml);
        provider.addProperty("configToml", mergedToml);
    }

    /**
     * Recursively overlays {@code base} beneath {@code override}: keys missing from
     * {@code override} are copied from {@code base}, nested objects are merged, and
     * conflicts keep the {@code override} value. {@code override} is modified in place.
     */
    private static void overlayJsonValues(JsonObject override, JsonObject base) {
        for (Map.Entry<String, JsonElement> entry : base.entrySet()) {
            String key = entry.getKey();
            JsonElement baseValue = entry.getValue();
            if (!override.has(key)) {
                override.add(key, baseValue.deepCopy());
            } else if (override.get(key).isJsonObject() && baseValue.isJsonObject()) {
                overlayJsonValues(override.getAsJsonObject(key), baseValue.getAsJsonObject());
            }
        }
    }
}
