package at.minecraftschurli.mods.dynassetgen.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/// Checks that the data packs of the test mod are found and loaded by a server.
@ExtendWith(EphemeralTestServerProvider.class)
class PackLoadingTest {
    @Test
    void recipes(MinecraftServer server) {
        assertTrue(server.getRecipeManager().byKey(TestPacks.recipeKey("dirt_to_diamond")).isPresent(),
            "generated shaped recipe dirt_to_diamond is missing");
        assertTrue(server.getRecipeManager().byKey(TestPacks.recipeKey("json_stick")).isPresent(),
            "raw JSON recipe json_stick is missing");
    }

    @Test
    void disabledPack(MinecraftServer server) {
        assertFalse(server.getPackRepository().isAvailable(TestSupport.PACK_PREFIX + "dynassetgen_test/disabled"),
            "disabled pack is available although its mod condition is false");
        assertTrue(server.getRecipeManager().byKey(TestPacks.recipeKey("disabled_recipe")).isEmpty(),
            "recipe of the disabled pack was loaded");
    }

    @Test
    void itemTag(MinecraftServer server) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, TestPacks.id(TestPacks.NAMESPACE, "generated"));
        HolderSet.Named<Item> set = server.reloadableRegistries().lookup().lookupOrThrow(Registries.ITEM).get(tag)
            .orElse(null);
        assertNotNull(set, "item tag dynassetgen_test:generated is missing");
        assertTrue(set.stream().anyMatch(holder -> holder.value() == Items.STICK),
            "item tag dynassetgen_test:generated does not contain minecraft:stick");
    }

    @Test
    void lootTable(MinecraftServer server) {
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
            TestPacks.id(TestPacks.NAMESPACE, "blocks/generated"));
        assertTrue(server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).get(key).isPresent(),
            "loot table blocks/generated is missing");
    }

    @Test
    void advancement(MinecraftServer server) {
        assertNotNull(server.getAdvancements().get(TestPacks.id(TestPacks.NAMESPACE, "progress/generated")),
            "advancement progress/generated is missing");
    }

    @Test
    void scalePack(MinecraftServer server) {
        for (int index : new int[] {0, 1, 42, TestPacks.SCALE_FILES - 1}) {
            Identifier file = TestPacks.scaleFile(index);
            assertTrue(server.getResourceManager().getResource(file).isPresent(),
                "scale pack file " + file + " is not visible");
        }
    }
}
