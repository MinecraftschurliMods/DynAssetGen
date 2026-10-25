package at.minecraftschurli.mods.dynassetgen.impl;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

/// Identifies one generator of one pack in the memory cache.
///
/// @param packId      the pack id
/// @param generatorId the generator id
@ApiStatus.Internal
record CacheId(Identifier packId, Identifier generatorId) {}
