package at.minecraftschurli.mods.dynassetgen.impl;

import at.minecraftschurli.mods.dynassetgen.api.ContentSupplier;
import at.minecraftschurli.mods.dynassetgen.api.ResourceOutput;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

/// Collects the files of one generator run as [PlannedFile]s, without producing their content. 
@ApiStatus.Internal
final class CollectingOutput implements ResourceOutput {
    private final Identifier generatorId;
    private final Map<Identifier, PlannedFile> files = new LinkedHashMap<>();

    /// Creates a collector for one generator.
    ///
    /// @param generatorId the id of the generator that writes to this output
    CollectingOutput(Identifier generatorId) {
        this.generatorId = generatorId;
    }

    @Override
    public void accept(Identifier location, ContentSupplier content) {
        store(files, location, new PlannedFile(generatorId, content));
    }

    /// Returns the files collected so far, keyed by location.
    ///
    /// @return the planned files
    Map<Identifier, PlannedFile> files() {
        return files;
    }

    /// Stores a planned file. A later file for the same location replaces the earlier one.
    ///
    /// @param target   the map to store into
    /// @param location the location
    /// @param file     the planned file
    static void store(Map<Identifier, PlannedFile> target, Identifier location, PlannedFile file) {
        if (target.put(location, file) != null) {
            DynAssetGen.LOGGER.debug("{} is overwritten by a later generator ({})", location, file.generatorId());
        }
    }
}
