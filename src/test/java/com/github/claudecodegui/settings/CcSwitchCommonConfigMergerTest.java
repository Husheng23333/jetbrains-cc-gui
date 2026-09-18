package com.github.claudecodegui.settings;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CcSwitchCommonConfigMergerTest {

    @Test
    public void claudeCommonConfigMergesBeneathProviderSettingsConfig() throws Exception {
        String commonConfig = "{\"env\":{\"MCP_TIMEOUT\":\"60000\",\"ANTHROPIC_BASE_URL\":\"https://common.example\"},"
                + "\"permissions\":{\"defaultMode\":\"bypassPermissions\"}}";
        JsonObject provider = JsonParser.parseString(
                "{\"id\":\"p1\",\"settingsConfig\":{\"env\":{\"ANTHROPIC_BASE_URL\":\"https://provider.example\","
                        + "\"ANTHROPIC_AUTH_TOKEN\":\"sk-1\"}}}").getAsJsonObject();

        CcSwitchCommonConfigMerger.merge(provider, "claude", commonConfig);

        JsonObject settingsConfig = provider.getAsJsonObject("settingsConfig");
        // provider value wins on conflict
        assertEquals("https://provider.example", settingsConfig.getAsJsonObject("env").get("ANTHROPIC_BASE_URL").getAsString());
        // common-only keys are filled in
        assertEquals("60000", settingsConfig.getAsJsonObject("env").get("MCP_TIMEOUT").getAsString());
        assertEquals("bypassPermissions", settingsConfig.getAsJsonObject("permissions").get("defaultMode").getAsString());
        // untouched provider fields survive
        assertEquals("p1", provider.get("id").getAsString());
    }

    @Test
    public void codexCommonConfigMergesIntoProviderToml() throws Exception {
        String commonConfig = "model_reasoning_effort = \"high\"\n"
                + "disable_response_storage = true\n\n"
                + "[desktop]\nmode = \"queue\"\n";
        JsonObject provider = JsonParser.parseString(
                "{\"id\":\"p2\",\"configToml\":\"model = \\\"gpt-5\\\"\\n\\n[model_providers.cc]\\nbase_url = \\\"https://p.example\\\"\\n\"}")
                .getAsJsonObject();

        CcSwitchCommonConfigMerger.merge(provider, "codex", commonConfig);

        String mergedToml = provider.get("configToml").getAsString();
        // The merged TOML must round-trip and contain both layers with provider winning
        var merged = CodexSettingsManager.parseToml(mergedToml);
        assertEquals("gpt-5", merged.get("model"));
        assertEquals("high", merged.get("model_reasoning_effort"));
        assertEquals(Boolean.TRUE, merged.get("disable_response_storage"));
        @SuppressWarnings("unchecked")
        var desktop = (java.util.Map<String, Object>) merged.get("desktop");
        assertEquals("queue", desktop.get("mode"));
        @SuppressWarnings("unchecked")
        var modelProviders = (java.util.Map<String, Object>) merged.get("model_providers");
        @SuppressWarnings("unchecked")
        var cc = (java.util.Map<String, Object>) modelProviders.get("cc");
        assertEquals("https://p.example", cc.get("base_url"));
    }

    @Test
    public void codexProviderValueOverridesCommonConfigOnConflict() throws Exception {
        String commonConfig = "sandbox_mode = \"read-only\"\n";
        JsonObject provider = JsonParser.parseString(
                "{\"id\":\"p3\",\"configToml\":\"sandbox_mode = \\\"danger-full-access\\\"\\n\"}").getAsJsonObject();

        CcSwitchCommonConfigMerger.merge(provider, "codex", commonConfig);

        var merged = CodexSettingsManager.parseToml(provider.get("configToml").getAsString());
        assertEquals("danger-full-access", merged.get("sandbox_mode"));
    }

    @Test
    public void blankCommonConfigLeavesProviderUntouched() throws Exception {
        JsonObject provider = JsonParser.parseString(
                "{\"id\":\"p4\",\"configToml\":\"model = \\\"gpt-5\\\"\\n\"}").getAsJsonObject();
        String tomlBefore = provider.get("configToml").getAsString();

        CcSwitchCommonConfigMerger.merge(provider, "codex", "   ");
        CcSwitchCommonConfigMerger.merge(provider, "claude", null);

        assertEquals(tomlBefore, provider.get("configToml").getAsString());
        assertFalse(provider.has("settingsConfig"));
        assertNull(provider.get("settingsConfig"));
    }
}
