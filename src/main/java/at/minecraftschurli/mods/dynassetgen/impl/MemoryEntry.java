package at.minecraftschurli.mods.dynassetgen.impl;

import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

/// The output of a generator held in memory.
///
/// @param key   the cache key the output was generated for
/// @param files the bytes of every file the generator emitted, by location
@ApiStatus.Internal
record MemoryEntry(Object key, Map<Identifier, byte[]> files) {}
