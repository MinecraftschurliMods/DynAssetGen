package at.minecraftschurli.mods.dynassetgen.impl;

import at.minecraftschurli.mods.dynassetgen.api.DynamicPack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.ApiStatus;

/// Writes every generated file to disk for inspection. Enabled with `-Ddynassetgen.dump=true`. 
@ApiStatus.Internal
public final class DebugDump {
    private static final boolean ENABLED = "true".equals(System.getProperty("dynassetgen.dump"));

    private DebugDump() {}

    /// Writes a generated file to `<gamedir>/.dynassetgen/dump/<pack>/<path>`, if dumping is enabled.
    ///
    /// @param pack  the pack
    /// @param path  the pack-relative path
    /// @param bytes the generated content
    public static void dump(DynamicPack pack, Identifier path, byte[] bytes) {
        if (!ENABLED) return;
        Path file = FMLPaths.GAMEDIR.get()
            .resolve(".dynassetgen/dump")
            .resolve(pack.id().getNamespace())
            .resolve(pack.id().getPath())
            .resolve(path.getNamespace())
            .resolve(path.getPath());
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            DynAssetGen.LOGGER.warn("Could not write debug dump {}", file, e);
        }
    }
}
