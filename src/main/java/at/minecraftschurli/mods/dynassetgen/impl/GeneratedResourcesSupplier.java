package at.minecraftschurli.mods.dynassetgen.impl;

import at.minecraftschurli.mods.dynassetgen.api.DynamicPack;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import org.jetbrains.annotations.ApiStatus;

/// Opens a new [GeneratedPackResources] for each reload of a pack.
///
/// @param pack the pack to serve
@ApiStatus.Internal
record GeneratedResourcesSupplier(DynamicPack pack) implements Pack.ResourcesSupplier {
    @Override
    public PackResources openPrimary(PackLocationInfo location) {
        return new GeneratedPackResources(location, pack);
    }

    @Override
    public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
        return new GeneratedPackResources(location, pack);
    }
}
