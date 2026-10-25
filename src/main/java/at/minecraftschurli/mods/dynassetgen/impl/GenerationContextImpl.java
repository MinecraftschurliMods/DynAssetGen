package at.minecraftschurli.mods.dynassetgen.impl;

import at.minecraftschurli.mods.dynassetgen.api.GenerationContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.ApiStatus;

/// Default [GenerationContext].
///
/// @param packId     the pack id
/// @param packType   the pack type
/// @param registries the built-in registries
@ApiStatus.Internal
public record GenerationContextImpl(Identifier packId, PackType packType, HolderLookup.Provider registries)
    implements GenerationContext {

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
