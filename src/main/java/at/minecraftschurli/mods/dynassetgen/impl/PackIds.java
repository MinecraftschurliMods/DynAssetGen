package at.minecraftschurli.mods.dynassetgen.impl;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

/// Keeps the ids of all created packs, so a duplicate is rejected across all registrars. 
@ApiStatus.Internal
public final class PackIds {
    private static final Set<Identifier> IDS = ConcurrentHashMap.newKeySet();

    private PackIds() {}

    /// Claims a pack id.
    ///
    /// @param id the pack id
    /// @throws IllegalArgumentException if the id is already claimed by a pack
    public static void claim(Identifier id) {
        if (!IDS.add(id)) {
            throw new IllegalArgumentException("Duplicate DynAssetGen pack id: " + id);
        }
    }
}
