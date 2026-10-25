package at.minecraftschurli.mods.dynassetgen.impl;

import at.minecraftschurli.mods.dynassetgen.api.DynamicPack;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.ApiStatus;

/// Error policy for generators: log in production, rethrow in development. 
@ApiStatus.Internal
public final class GeneratorErrors {
    private GeneratorErrors() {}

    /// Handles a failure. In production the failure is logged and the failed generator or file is skipped. In
    /// development it is rethrown as a [RuntimeException].
    ///
    /// @param pack      the pack whose generation failed
    /// @param target    the generator id, or the location of the file that failed
    /// @param throwable the failure
    /// @throws RuntimeException in a development environment
    public static void handle(DynamicPack pack, Identifier target, Throwable throwable) {
        DynAssetGen.LOGGER.error("{}: generation failed for {}", pack.id(), target, throwable);
        if (FMLEnvironment.isProduction()) return;
        throw new RuntimeException(pack.id() + ": generation failed for " + target, throwable);
    }
}
