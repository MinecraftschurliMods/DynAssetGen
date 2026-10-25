package at.minecraftschurli.mods.dynassetgen.impl;

import at.minecraftschurli.mods.dynassetgen.api.ResourceGenerator;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

/// A generator registered on a pack.
///
/// @param id        the generator id, unique within the pack; names the generator's cache blob
/// @param generator the generator
@ApiStatus.Internal
public record RegisteredGenerator(Identifier id, ResourceGenerator generator) {}
