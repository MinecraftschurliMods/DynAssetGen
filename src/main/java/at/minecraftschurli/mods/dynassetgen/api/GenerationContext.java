package at.minecraftschurli.mods.dynassetgen.api;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

/// Information available to generators while they produce files. 
public interface GenerationContext {
    /// Returns the id of the pack being generated.
    ///
    /// @return the pack id
    Identifier packId();

    /// Returns the type of the pack being generated.
    ///
    /// @return the pack type
    PackType packType();

    /// Returns the registries used to encode registry references.
    ///
    /// Only built-in (static) registries are available. Data-driven registries and tags are NOT available,
    /// because generation can run before they are loaded.
    ///
    /// @return the built-in registry lookup
    HolderLookup.Provider registries();

    /// Returns whether a mod is loaded.
    ///
    /// @param modId the mod id
    /// @return `true` if the mod is loaded
    boolean isModLoaded(String modId);
}
