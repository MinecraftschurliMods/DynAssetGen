package at.minecraftschurli.mods.dynassetgen.impl;

import at.minecraftschurli.mods.dynassetgen.api.DynamicPack;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.ApiStatus;

/// Adds packs to the pack repository. 
@ApiStatus.Internal
public final class PackFinder {
    private PackFinder() {}

    /// Adds every enabled pack of the event's type.
    ///
    /// @param event the event
    /// @param packs the packs to offer
    public static void addPacks(AddPackFindersEvent event, List<DynamicPack> packs) {
        for (DynamicPack pack : packs) {
            if (pack.packType() != event.getPackType() || !pack.isEnabled()) continue;
            // Runs for trusted and untrusted events alike.
            event.addRepositorySource(consumer -> consumer.accept(createPack(pack)));
        }
    }

    private static Pack createPack(DynamicPack pack) {
        String packId = DynAssetGen.ID + "/" + pack.id().getNamespace() + "/" + pack.id().getPath();
        // knownPackInfo stays empty on purpose so generated registry data always syncs to clients.
        PackLocationInfo location = new PackLocationInfo(packId, pack.title(), PackSource.DEFAULT, Optional.empty());
        Pack.Metadata metadata = new Pack.Metadata(
            pack.title(), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of(), true);
        PackSelectionConfig selection = new PackSelectionConfig(true, pack.position(), true);
        return new Pack(location, new GeneratedResourcesSupplier(pack), metadata, selection);
    }
}
