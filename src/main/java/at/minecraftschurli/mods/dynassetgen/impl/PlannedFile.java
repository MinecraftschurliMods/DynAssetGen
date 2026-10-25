package at.minecraftschurli.mods.dynassetgen.impl;

import at.minecraftschurli.mods.dynassetgen.api.ContentSupplier;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

/// A file planned by a generator, not yet produced.
///
/// @param generatorId the id of the generator that planned the file
/// @param content     the content producer
@ApiStatus.Internal
record PlannedFile(Identifier generatorId, ContentSupplier content) {}
