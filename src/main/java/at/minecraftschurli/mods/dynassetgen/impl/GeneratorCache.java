package at.minecraftschurli.mods.dynassetgen.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.fml.loading.FMLPaths;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/// The two cache layers for generator output, both keyed by the cache key of a generator.
///
///   - The memory layer holds the output of each generator for the rest of the game session.
///   - The disk layer holds one blob per generator, at `<gamedir>/.cache/dynassetgen/<packNs>/<packPath>/<genNs>/
/// <genPath>.bin`. The blob holds the magic, the format version, the key hash, and then the file count and, for each
///     file, its location and bytes.
///
/// Enabled unless `-Ddynassetgen.cache=false` is set, which disables both layers.
@ApiStatus.Internal
public final class GeneratorCache {
    private static final int MAGIC = 0x44414732; // "DAG2"
    private static final int FORMAT_VERSION = 1;
    private static final boolean ENABLED = !"false".equals(System.getProperty("dynassetgen.cache"));
    private static final Map<CacheId, MemoryEntry> MEMORY = new ConcurrentHashMap<>();
    private static final Set<Class<?>> WARNED_KEY_CLASSES = ConcurrentHashMap.newKeySet();
    private static final Queue<CompletableFuture<Void>> PENDING_WRITES = new ConcurrentLinkedQueue<>();

    private GeneratorCache() {}

    /// Returns whether caching is enabled.
    ///
    /// @return `true` unless disabled with `-Ddynassetgen.cache=false`
    public static boolean isEnabled() {
        return ENABLED;
    }

    /// Returns the cache root of a pack: `<gamedir>/.cache/dynassetgen/<namespace>/<path>`.
    ///
    /// @param packId the pack id
    /// @return the cache root
    public static Path rootOf(Identifier packId) {
        return FMLPaths.GAMEDIR.get()
            .resolve(".cache")
            .resolve(DynAssetGen.ID)
            .resolve(packId.getNamespace())
            .resolve(packId.getPath());
    }

    /// Returns the blob of a generator: `<root of the pack>/<genNs>/<genPath>.bin`.
    ///
    /// @param packId      the pack id
    /// @param generatorId the generator id
    /// @return the blob file, which may not exist
    public static Path blobOf(Identifier packId, Identifier generatorId) {
        return rootOf(packId).resolve(generatorId.getNamespace()).resolve(generatorId.getPath() + ".bin");
    }

    /// Returns the output of a generator from memory, if it was generated for an equal key.
    ///
    /// @param id  the pack and generator
    /// @param key the current cache key
    /// @return the bytes of every file by location, or `null` on a miss
    static @Nullable Map<Identifier, byte[]> memory(CacheId id, Object key) {
        MemoryEntry entry = MEMORY.get(id);
        return entry != null && entry.key().equals(key) ? entry.files() : null;
    }

    /// Stores the output of a generator in memory, replacing any earlier output.
    ///
    /// @param id    the pack and generator
    /// @param key   the cache key the output was generated for
    /// @param files the bytes of every file by location
    static void remember(CacheId id, Object key, Map<Identifier, byte[]> files) {
        MEMORY.put(id, new MemoryEntry(key, Collections.unmodifiableMap(new LinkedHashMap<>(files))));
    }

    /// Reads the output of a generator from its disk blob. A missing, corrupt or foreign blob, or one whose key hash
    /// differs, is a miss. A key whose class does not override `toString()` has no disk form, so it is a miss.
    ///
    /// @param packId      the pack id
    /// @param generatorId the generator id
    /// @param key         the current cache key
    /// @return the bytes of every file by location, or `null` on a miss
    public static @Nullable Map<Identifier, byte[]> disk(Identifier packId, Identifier generatorId, Object key) {
        String keyHash = hash(key);
        if (keyHash == null) return null;
        Path blob = blobOf(packId, generatorId);
        if (!Files.isRegularFile(blob)) return null;
        try {
            return read(blob, keyHash);
        } catch (IOException | RuntimeException e) {
            DynAssetGen.LOGGER.debug("Cache blob {} is unreadable, regenerating", blob, e);
            return null;
        }
    }

    /// Writes the output of a generator to its disk blob, on the IO pool. A failure is logged and never thrown. A key
    /// without a disk form is not written.
    ///
    /// @param packId      the pack id
    /// @param generatorId the generator id
    /// @param key         the cache key the output was generated for
    /// @param files       the bytes of every file by location
    public static void store(Identifier packId, Identifier generatorId, Object key, Map<Identifier, byte[]> files) {
        String keyHash = hash(key);
        if (keyHash == null) return;
        Path blob = blobOf(packId, generatorId);
        byte[] bytes;
        try {
            bytes = encode(keyHash, files);
        } catch (IOException e) {
            DynAssetGen.LOGGER.warn("Could not encode cache blob {}", blob, e);
            return;
        }
        PENDING_WRITES.removeIf(CompletableFuture::isDone);
        PENDING_WRITES.add(CompletableFuture.runAsync(() -> {
            try {
                writeAtomically(blob, bytes);
            } catch (IOException | RuntimeException e) {
                DynAssetGen.LOGGER.warn("Could not write cache blob {}", blob, e);
            }
        }, Util.ioPool()));
    }

    /// Drops every output held in memory. The disk blobs are kept. Used by tests. 
    @ApiStatus.Internal
    public static void clearMemory() {
        MEMORY.clear();
    }

    /// Blocks until every blob queued by [#store] has been written. Used by tests. 
    @ApiStatus.Internal
    public static void awaitWrites() {
        CompletableFuture<Void> pending;
        while ((pending = PENDING_WRITES.poll()) != null) {
            pending.join();
        }
    }

    /// Hashes a cache key as SHA-256 over `class name + '\\n' + toString()`, as lowercase hex. A key whose class
    /// does not override `toString()` has no such hash. That is reported once per class.
    ///
    /// @param key the cache key
    /// @return the hash, or `null` if the key has no disk form
    private static @Nullable String hash(Object key) {
        Class<?> type = key.getClass();
        if (!overridesToString(type)) {
            if (WARNED_KEY_CLASSES.add(type)) {
                DynAssetGen.LOGGER.warn("Cache key class {} does not override toString(), so its generators are"
                    + " not cached on disk", type.getName());
            }
            return null;
        }
        try {
            byte[] input = (type.getName() + '\n' + key).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static byte[] encode(String keyHash, Map<Identifier, byte[]> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeUTF(keyHash);
            out.writeInt(files.size());
            for (Map.Entry<Identifier, byte[]> file : files.entrySet()) {
                out.writeUTF(file.getKey().toString());
                out.writeInt(file.getValue().length);
                out.write(file.getValue());
            }
        }
        return bytes.toByteArray();
    }

    /// Returns the files of a blob, or `null` if its header or key hash does not match. 
    private static @Nullable Map<Identifier, byte[]> read(Path blob, String keyHash) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(blob)))) {
            if (in.readInt() != MAGIC || in.readInt() != FORMAT_VERSION) {
                DynAssetGen.LOGGER.debug("Ignoring cache blob {} with an unknown format", blob);
                return null;
            }
            if (!keyHash.equals(in.readUTF())) return null;
            int count = in.readInt();
            if (count < 0) return null;
            Map<Identifier, byte[]> files = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                Identifier location = Identifier.parse(in.readUTF());
                int length = in.readInt();
                if (length < 0) return null;
                byte[] content = new byte[length];
                in.readFully(content);
                files.put(location, content);
            }
            return files;
        }
    }

    private static void writeAtomically(Path target, byte[] content) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(tmp, content);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static boolean overridesToString(Class<?> type) {
        try {
            return type.getMethod("toString").getDeclaringClass() != Object.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
