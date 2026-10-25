package at.minecraftschurli.mods.dynassetgen.api;

import at.minecraftschurli.mods.dynassetgen.impl.PackFinder;
import at.minecraftschurli.mods.dynassetgen.impl.PackIds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/// Registrar for the dynamic packs of one mod. Each mod that uses DynAssetGen creates its own registrar, which owns the
/// packs it creates and adds only those packs to the pack repository.
public final class DynamicPacks {
    private final List<DynamicPack> packs = new ArrayList<>();
    private boolean frozen;

    private DynamicPacks() {}

    /// Creates a registrar. Call once from your mod's constructor with your mod event bus. It registers this
    /// registrar's own [AddPackFindersEvent] listener on that bus.
    ///
    /// @param modBus the mod event bus
    /// @return the new registrar
    public static DynamicPacks create(IEventBus modBus) {
        DynamicPacks registrar = new DynamicPacks();
        modBus.addListener(registrar::onAddPackFinders);
        return registrar;
    }

    /// Creates a data pack ([PackType#SERVER_DATA]).
    ///
    /// @param id the pack id, unique across all mods
    /// @return the new pack
    /// @throws IllegalStateException    if packs can no longer be registered
    /// @throws IllegalArgumentException if a pack with this id already exists
    public DynamicPack data(Identifier id) {
        return create(id, PackType.SERVER_DATA);
    }

    /// Creates a resource pack ([PackType#CLIENT_RESOURCES]). It has no effect on a dedicated server.
    ///
    /// @param id the pack id, unique across all mods
    /// @return the new pack
    /// @throws IllegalStateException    if packs can no longer be registered
    /// @throws IllegalArgumentException if a pack with this id already exists
    public DynamicPack assets(Identifier id) {
        return create(id, PackType.CLIENT_RESOURCES);
    }

    private synchronized DynamicPack create(Identifier id, PackType packType) {
        checkNotFrozen();
        PackIds.claim(id);
        DynamicPack pack = new DynamicPack(this, id, packType);
        packs.add(pack);
        return pack;
    }

    private synchronized void onAddPackFinders(AddPackFindersEvent event) {
        frozen = true;
        PackFinder.addPacks(event, List.copyOf(packs));
    }

    /// Throws if this registrar is frozen.
    ///
    /// @throws IllegalStateException if packs can no longer be registered or changed
    synchronized void checkNotFrozen() {
        if (frozen) {
            throw new IllegalStateException("DynAssetGen packs must be registered during mod construction");
        }
    }
}
