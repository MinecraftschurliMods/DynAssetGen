package at.minecraftschurli.mods.dynassetgen.test;

import at.minecraftschurli.mods.dynassetgen.api.DynamicPack;
import at.minecraftschurli.mods.dynassetgen.impl.GeneratedPackResources;
import at.minecraftschurli.mods.dynassetgen.impl.GeneratorCache;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;

/// Shared helpers of the tests: reading packs directly and inspecting the cache on disk.
final class TestSupport {
    /// Prefix of the ids of all packs added by DynAssetGen.
    static final String PACK_PREFIX = "dynassetgen/";
    /// Magic number of the cache blob format, see `GeneratorCache`.
    private static final int BLOB_MAGIC = 0x44414732;
    /// Version of the cache blob format, see `GeneratorCache`.
    private static final int BLOB_VERSION = 1;

    private TestSupport() {}

    /// A blob read back: its key hash, and its files as text by location.
    ///
    /// @param keyHash the key hash stored in the blob
    /// @param files   the files as text by location
    record Blob(String keyHash, Map<String, String> files) {}

    /// Creates a fresh resources instance of a pack, as the pack repository would.
    static GeneratedPackResources resourcesFor(DynamicPack pack) {
        PackLocationInfo location = new PackLocationInfo(PACK_PREFIX + pack.id().getNamespace() + "/"
            + pack.id().getPath(), Component.literal(pack.id().toString()), PackSource.DEFAULT, Optional.empty());
        return new GeneratedPackResources(location, pack);
    }

    /// Counts the files of a scale pack, listed from a fresh resources instance.
    static int listScale(DynamicPack pack) {
        Set<Identifier> listed = new HashSet<>();
        try (GeneratedPackResources resources = resourcesFor(pack)) {
            resources.listResources(PackType.SERVER_DATA, TestPacks.SCALE_NAMESPACE, "tags/item/gen",
                (id, _) -> listed.add(id));
        }
        return listed.size();
    }

    /// Reads one data file from a fresh resources instance, then closes it.
    static @Nullable String readAndClose(DynamicPack pack, Identifier file) {
        try (GeneratedPackResources resources = resourcesFor(pack)) {
            return read(resources, PackType.SERVER_DATA, file);
        }
    }

    /// Reads one file as text, or returns `null` if the pack does not have it.
    static @Nullable String read(GeneratedPackResources resources, PackType type, Identifier file) {
        var supplier = resources.getResource(type, file);
        if (supplier == null) return null;
        try (InputStream in = supplier.get()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    /// Starts a cache test from a cold cache: no blob of the pack and no memory entry of any pack.
    static void reset(DynamicPack pack) {
        GeneratorCache.awaitWrites();
        deleteTree(GeneratorCache.rootOf(pack.id()));
        GeneratorCache.clearMemory();
    }

    /// Returns the blob of the single generator of a cache test pack or of a scale pack.
    static Path blobOf(DynamicPack pack) {
        boolean scale = TestPacks.SCALE_NAMESPACE.equals(pack.id().getNamespace());
        String generator = scale ? TestPacks.SCALE_GENERATOR : TestPacks.CACHE_GENERATOR;
        return GeneratorCache.blobOf(pack.id(), TestPacks.id(pack.id().getNamespace(), generator));
    }

    /// Returns the root of the whole DynAssetGen cache.
    static Path cacheBase() {
        return FMLPaths.GAMEDIR.get().resolve(".cache").resolve("dynassetgen");
    }

    /// Reads a blob: magic, version, key hash, then the count and each file's location and bytes.
    static Blob readBlob(Path blob) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(blob)))) {
            if (in.readInt() != BLOB_MAGIC || in.readInt() != BLOB_VERSION) {
                throw new IllegalStateException("unexpected header in " + blob);
            }
            String keyHash = in.readUTF();
            int count = in.readInt();
            Map<String, String> files = new HashMap<>();
            for (int i = 0; i < count; i++) {
                String location = in.readUTF();
                int length = in.readInt();
                files.put(location, new String(in.readNBytes(length), StandardCharsets.UTF_8));
            }
            return new Blob(keyHash, files);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read cache blob " + blob, e);
        }
    }

    /// Deletes a directory tree, or nothing if it does not exist.
    static void deleteTree(Path root) {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// Returns all regular files below a directory, or none if it does not exist.
    static List<Path> files(Path root) {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
