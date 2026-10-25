package at.minecraftschurli.mods.dynassetgen.test;

import at.minecraftschurli.mods.dynassetgen.api.DynamicPacks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/// Entrypoint of the test mod, only present in the test source set. The JUnit run loads this source set as a mod,
/// declared by `test/resources/META-INF/neoforge.mods.toml`.
@Mod("dynassetgen_test")
public final class DynAssetGenTest {
    /// Registers the test packs.
    ///
    /// @param modBus the mod event bus
    public DynAssetGenTest(IEventBus modBus) {
        TestPacks.register(DynamicPacks.create(modBus));
    }
}
