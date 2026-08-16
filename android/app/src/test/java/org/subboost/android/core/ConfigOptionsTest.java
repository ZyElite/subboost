package org.subboost.android.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigOptionsTest {
    @Test
    public void roundTripsFullTemplateConfiguration() {
        ConfigOptions options = new ConfigOptions();
        options.applyTemplate("full");
        options.advancedMode = true;
        options.groupNameOverrides.put("ai", "AI 专线");
        ConfigOptions.GroupAdvanced group = new ConfigOptions.GroupAdvanced();
        group.includeRegex = "Premium|专线";
        options.groupAdvanced.put("ai", group);

        ConfigOptions decoded = ConfigOptions.fromJson(options.toJson());

        assertEquals("full", decoded.template);
        assertTrue(decoded.enabledModules.size() > 30);
        assertEquals("AI 专线", decoded.groupNameOverrides.get("ai"));
        assertEquals("Premium|专线", decoded.groupAdvanced.get("ai").includeRegex);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownSchema() {
        ConfigOptions.fromJson("{\"schema\":\"unknown\",\"template\":\"full\"}");
    }
}
