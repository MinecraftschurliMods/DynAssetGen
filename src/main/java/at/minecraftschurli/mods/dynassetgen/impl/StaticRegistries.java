package at.minecraftschurli.mods.dynassetgen.impl;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.ApiStatus;

/// Memoised access to the built-in registries, used for encoding registry references. 
@ApiStatus.Internal
public final class StaticRegistries {
    private static final RegistryAccess.Frozen INSTANCE =
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    private StaticRegistries() {}

    /// Returns the built-in registries. The lookup is created once, on first use.
    ///
    /// @return the built-in registry access
    public static HolderLookup.Provider get() {
        return INSTANCE;
    }
}
