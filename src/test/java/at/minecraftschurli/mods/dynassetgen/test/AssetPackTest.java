package at.minecraftschurli.mods.dynassetgen.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.minecraftschurli.mods.dynassetgen.impl.GeneratedPackResources;
import com.google.gson.JsonParser;
import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.Test;

/// Checks the asset pack of the test mod. The tests run without a client, so the pack is read directly.
class AssetPackTest {
    @Test
    void lang() {
        String json;
        try (GeneratedPackResources resources = TestSupport.resourcesFor(TestPacks.assets)) {
            json = TestSupport.read(resources, PackType.CLIENT_RESOURCES,
                TestPacks.id(TestPacks.NAMESPACE, "lang/en_us.json"));
        }
        assertNotNull(json, "lang/en_us.json was not generated");
        assertTrue(JsonParser.parseString(json).getAsJsonObject().has(TestPacks.NAMESPACE + ".lang.hello"),
            "lang key is missing from " + json);
    }
}
