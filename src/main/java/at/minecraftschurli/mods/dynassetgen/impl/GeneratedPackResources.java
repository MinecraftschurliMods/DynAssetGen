package at.minecraftschurli.mods.dynassetgen.impl;

import at.minecraftschurli.mods.dynassetgen.api.DynamicPack;
import at.minecraftschurli.mods.dynassetgen.api.GenerationContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.InclusiveRange;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/// A [PackResources] that serves the files of one [DynamicPack]. A new instance is created for each
/// reload. The planned files are computed once per instance. Generators with a cache key are served from the cache
/// when possible, and the content of every other file is produced on first use and memoised.
@ApiStatus.Internal
public final class GeneratedPackResources implements PackResources {
    private final PackLocationInfo location;
    private final DynamicPack pack;
    private final GenerationContext ctx;
    private final Object planLock = new Object();
    private volatile @Nullable Map<Identifier, PlannedFile> planned;
    private final ConcurrentHashMap<Identifier, Optional<byte[]>> contents = new ConcurrentHashMap<>();

    /// Creates the resources and runs the pack's reset listeners.
    ///
    /// @param location the pack location
    /// @param pack     the pack to serve
    public GeneratedPackResources(PackLocationInfo location, DynamicPack pack) {
        this.location = location;
        this.pack = pack;
        for (Runnable listener : pack.resetListeners()) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                DynAssetGen.LOGGER.error("{}: reset listener failed", pack.id(), e);
            }
        }
        this.ctx = new GenerationContextImpl(pack.id(), pack.packType(), StaticRegistries.get());
    }

    /// Returns the planned files, running or reusing every generator once on first use. 
    private Map<Identifier, PlannedFile> plan() {
        synchronized (planLock) {
            Map<Identifier, PlannedFile> built = planned;
            if (built == null) {
                built = buildPlan();
                planned = built;
            }
            return built;
        }
    }

    private Map<Identifier, PlannedFile> buildPlan() {
        long start = System.nanoTime();
        Map<Identifier, PlannedFile> map = new LinkedHashMap<>();
        for (RegisteredGenerator registered : pack.generators()) {
            planGenerator(registered, map);
        }
        DynAssetGen.LOGGER.debug("{}: {} files planned in {} ms", pack.id(), map.size(),
            (System.nanoTime() - start) / 1_000_000);
        return Collections.unmodifiableMap(map);
    }

    /// Plans the files of one generator into the map, from the cache if its key is known, otherwise by running it. 
    private void planGenerator(RegisteredGenerator registered, Map<Identifier, PlannedFile> map) {
        Identifier generatorId = registered.id();
        Object key;
        try {
            key = registered.generator().createCacheKey(ctx);
        } catch (RuntimeException e) {
            GeneratorErrors.handle(pack, generatorId, e);
            return;
        }
        if (key == null || !GeneratorCache.isEnabled()) {
            planLazily(registered, map);
            return;
        }

        CacheId id = new CacheId(pack.id(), generatorId);
        Map<Identifier, byte[]> files = GeneratorCache.memory(id, key);
        if (files != null) {
            DynAssetGen.LOGGER.debug("{}: generator {} is a memory hit, {} files", pack.id(), generatorId, files.size());
            addProduced(map, generatorId, files);
            return;
        }
        files = GeneratorCache.disk(pack.id(), generatorId, key);
        if (files != null) {
            DynAssetGen.LOGGER.debug("{}: generator {} is a disk hit, {} files", pack.id(), generatorId, files.size());
            GeneratorCache.remember(id, key, files);
            addProduced(map, generatorId, files);
            return;
        }

        CollectingOutput output = new CollectingOutput(generatorId);
        try {
            registered.generator().generate(ctx, output);
        } catch (RuntimeException e) {
            GeneratorErrors.handle(pack, generatorId, e);
            return;
        }
        Map<Identifier, byte[]> produced = new LinkedHashMap<>();
        boolean complete = produceAll(output.files(), produced);
        DynAssetGen.LOGGER.debug("{}: generator {} is a miss, generated {} files", pack.id(), generatorId,
            produced.size());
        addProduced(map, generatorId, produced);
        // A failed file is left out. Caching the output would keep it missing across launches, so it is not cached.
        if (complete) {
            GeneratorCache.remember(id, key, produced);
            GeneratorCache.store(pack.id(), generatorId, key, produced);
        }
    }

    /// Runs an uncached generator. Its files are planned with lazy content. 
    private void planLazily(RegisteredGenerator registered, Map<Identifier, PlannedFile> map) {
        CollectingOutput output = new CollectingOutput(registered.id());
        try {
            registered.generator().generate(ctx, output);
        } catch (RuntimeException e) {
            GeneratorErrors.handle(pack, registered.id(), e);
            return;
        }
        for (Map.Entry<Identifier, PlannedFile> file : output.files().entrySet()) {
            CollectingOutput.store(map, file.getKey(), file.getValue());
        }
    }

    /// Produces the content of every file eagerly. Returns false if any file failed and was left out. 
    private boolean produceAll(Map<Identifier, PlannedFile> files, Map<Identifier, byte[]> produced) {
        boolean complete = true;
        for (Map.Entry<Identifier, PlannedFile> file : files.entrySet()) {
            try {
                produced.put(file.getKey(), file.getValue().content().get());
            } catch (IOException | RuntimeException e) {
                complete = false;
                GeneratorErrors.handle(pack, file.getKey(), e);
            }
        }
        return complete;
    }

    /// Plans already produced bytes. The content of each file is its bytes. 
    private void addProduced(Map<Identifier, PlannedFile> map, Identifier generatorId, Map<Identifier, byte[]> files) {
        for (Map.Entry<Identifier, byte[]> file : files.entrySet()) {
            byte[] bytes = file.getValue();
            CollectingOutput.store(map, file.getKey(), new PlannedFile(generatorId, () -> bytes));
        }
    }

    /// Returns the bytes of a file, producing and memoising them on first use. Returns null if there is no file. 
    private byte @Nullable [] produce(Identifier location) {
        if (!plan().containsKey(location)) return null;
        Optional<byte[]> cached = contents.get(location);
        if (cached == null) {
            Optional<byte[]> result = generate(location);
            Optional<byte[]> previous = contents.putIfAbsent(location, result);
            cached = previous != null ? previous : result;
        }
        return cached.orElse(null);
    }

    private Optional<byte[]> generate(Identifier location) {
        PlannedFile file = plan().get(location);
        if (file == null) return Optional.empty();
        try {
            byte[] bytes = file.content().get();
            DebugDump.dump(pack, location, bytes);
            return Optional.of(bytes);
        } catch (IOException | RuntimeException e) {
            GeneratorErrors.handle(pack, location, e);
            return Optional.empty();
        }
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier id) {
        if (type != pack.packType()) return null;
        byte[] bytes = produce(id);
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
        if (type != pack.packType()) return;
        String prefix = directory + "/";
        for (Identifier id : plan().keySet()) {
            if (!id.getNamespace().equals(namespace) || !id.getPath().startsWith(prefix)) continue;
            byte[] bytes = produce(id);
            if (bytes != null) output.accept(id, () -> new ByteArrayInputStream(bytes));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != pack.packType()) return Set.of();
        return plan().keySet().stream().map(Identifier::getNamespace).collect(Collectors.toSet());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getMetadataSection(MetadataSectionType<T> type) {
        // Every pack metadata section type is named "pack", so match on the name.
        if (!"pack".equals(type.name())) return null;
        PackMetadataSection section = new PackMetadataSection(
            pack.title(),
            new InclusiveRange<>(SharedConstants.getCurrentVersion().packVersion(pack.packType())));
        return (T) section;
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    /// Nothing to release. Caches are written when their generator runs, not when the resources are closed. 
    @Override
    public void close() {}
}
