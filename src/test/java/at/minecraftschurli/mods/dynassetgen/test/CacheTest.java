package at.minecraftschurli.mods.dynassetgen.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.minecraftschurli.mods.dynassetgen.api.DynamicPack;
import at.minecraftschurli.mods.dynassetgen.impl.GeneratorCache;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/// Checks the memory and disk layers of the generator cache by reading the cache test packs directly.
class CacheTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier CACHE_FILE = TestPacks.id(TestPacks.NAMESPACE, TestPacks.CACHE_FILE);
    private static final Identifier EXTRA_FILE = TestPacks.id(TestPacks.NAMESPACE, TestPacks.EXTRA_FILE);

    @Test
    void memoryHit() {
        DynamicPack pack = TestPacks.memoryHit;
        TestSupport.reset(pack);
        TestPacks.Probe probe = TestPacks.MEMORY_PROBE;
        probe.value.set("v1");

        int before = probe.generations.get();
        String first = TestSupport.readAndClose(pack, CACHE_FILE);
        assertNotNull(first, "first read returned no file");
        assertTrue(first.contains("\"v1\""), "first content is wrong: " + first);
        assertEquals(before + 1, probe.generations.get(), "first read must run the generator once");

        String second = TestSupport.readAndClose(pack, CACHE_FILE);
        assertEquals(before + 1, probe.generations.get(), "generator ran again on a memory hit");
        assertEquals(first, second, "cached content differs");
    }

    @Test
    void diskHit() {
        DynamicPack pack = TestPacks.diskHit;
        TestSupport.reset(pack);
        TestPacks.Probe probe = TestPacks.DISK_PROBE;
        probe.value.set("v1");

        int before = probe.generations.get();
        String first = TestSupport.readAndClose(pack, CACHE_FILE);
        assertNotNull(first, "disk_hit file is missing on the first read");
        assertEquals(before + 1, probe.generations.get(), "first read must run the generator once");
        GeneratorCache.awaitWrites();
        Path blob = TestSupport.blobOf(pack);
        assertTrue(Files.isRegularFile(blob), "blob missing after the first read: " + blob);

        GeneratorCache.clearMemory();
        int afterFirst = probe.generations.get();
        String second = TestSupport.readAndClose(pack, CACHE_FILE);
        assertNotNull(second, "disk_hit file is missing after the memory was cleared");
        assertEquals(afterFirst, probe.generations.get(), "generator ran on a disk hit");
        assertEquals(first, second, "blob content differs");
    }

    @Test
    void keyChange() {
        DynamicPack pack = TestPacks.keyChange;
        TestSupport.reset(pack);
        TestPacks.Probe probe = TestPacks.KEY_CHANGE_PROBE;
        probe.value.set("v1");

        int before = probe.generations.get();
        String first = TestSupport.readAndClose(pack, CACHE_FILE);
        assertNotNull(first, "first read returned no file");
        assertTrue(first.contains("\"v1\""), "first content is wrong: " + first);
        assertNotNull(TestSupport.readAndClose(pack, EXTRA_FILE), "extra file is missing while the value is v1");
        assertEquals(before + 1, probe.generations.get(), "first read must run the generator once");
        GeneratorCache.awaitWrites();
        Path blob = TestSupport.blobOf(pack);
        TestSupport.Blob oldBlob = TestSupport.readBlob(blob);
        assertEquals(Set.of(CACHE_FILE.toString(), EXTRA_FILE.toString()), oldBlob.files().keySet(),
            "blob after v1 holds the wrong files");

        probe.value.set("v2");
        String second = TestSupport.readAndClose(pack, CACHE_FILE);
        assertEquals(before + 2, probe.generations.get(), "generator was not run after the key changed");
        assertNotNull(second, "second read returned no file");
        assertTrue(second.contains("\"v2\"") && !second.contains("\"v1\""),
            "content after the key change is wrong: " + second);
        assertNull(TestSupport.readAndClose(pack, EXTRA_FILE),
            "extra file is still served although the generator no longer emits it");
        GeneratorCache.awaitWrites();
        TestSupport.Blob newBlob = TestSupport.readBlob(blob);
        assertNotEquals(oldBlob.keyHash(), newBlob.keyHash(), "blob was not rewritten for the new key");
        assertEquals(Set.of(CACHE_FILE.toString()), newBlob.files().keySet(), "rewritten blob holds the wrong files");
    }

    @Test
    void noToStringKey() {
        DynamicPack pack = TestPacks.noToStringKey;
        TestSupport.reset(pack);
        TestPacks.Probe probe = TestPacks.PLAIN_PROBE;
        probe.value.set("v1");

        int before = probe.generations.get();
        String first = TestSupport.readAndClose(pack, CACHE_FILE);
        String second = TestSupport.readAndClose(pack, CACHE_FILE);
        assertNotNull(first, "first read returned no file");
        assertEquals(first, second, "cached content differs");
        assertEquals(before + 1, probe.generations.get(), "memory hit must work for a key without toString()");
        GeneratorCache.awaitWrites();
        assertFalse(Files.exists(TestSupport.blobOf(pack)), "a key without toString() wrote a blob");

        GeneratorCache.clearMemory();
        TestSupport.readAndClose(pack, CACHE_FILE);
        assertEquals(before + 2, probe.generations.get(),
            "a key without toString() must not be served from disk after the memory is cleared");
        GeneratorCache.awaitWrites();
        assertFalse(Files.exists(TestSupport.blobOf(pack)), "a key without toString() wrote a blob");
    }

    @Test
    void oneBlobPerGenerator() {
        DynamicPack pack = TestPacks.scale;
        TestSupport.deleteTree(TestSupport.cacheBase());
        GeneratorCache.clearMemory();
        assertEquals(TestPacks.SCALE_FILES, TestSupport.listScale(pack), "scale pack did not list all files");
        GeneratorCache.awaitWrites();

        List<Path> blobs = TestSupport.files(GeneratorCache.rootOf(pack.id())).stream()
            .filter(path -> path.getFileName().toString().endsWith(".bin")).toList();
        assertEquals(List.of(TestSupport.blobOf(pack)), blobs, "expected exactly one blob for the scale generator");

        List<Path> stray = TestSupport.files(TestSupport.cacheBase()).stream()
            .filter(path -> path.getFileName().toString().endsWith(".index")
                || path.getFileName().toString().endsWith(".key")).toList();
        assertTrue(stray.isEmpty(), "unexpected index or key files in the cache: " + stray);
    }

    /// Runs the scale pack through every cache layer and logs the timings.
    @Test
    void scaleLayers() {
        // Uncached: the generator runs on every read.
        long start = System.nanoTime();
        int uncached = TestSupport.listScale(TestPacks.scaleUncached);
        GeneratorCache.awaitWrites();
        timed("uncached read", start, uncached);
        assertEquals(TestPacks.SCALE_FILES, uncached, "uncached scale files");

        // Cold miss: no memory entry and no blob, so the generator runs and writes the blob.
        TestSupport.reset(TestPacks.scale);
        int generations = TestPacks.SCALE_GENERATIONS.get();
        start = System.nanoTime();
        int cold = TestSupport.listScale(TestPacks.scale);
        GeneratorCache.awaitWrites();
        timed("cold miss (generate and write blob)", start, cold);
        assertEquals(TestPacks.SCALE_FILES, cold, "cold scale files");
        assertEquals(generations + 1, TestPacks.SCALE_GENERATIONS.get(), "cold miss must run the generator once");
        assertTrue(Files.isRegularFile(TestSupport.blobOf(TestPacks.scale)), "scale blob missing after the cold miss");

        // Disk hit: the memory is cleared, so the files come from the blob.
        GeneratorCache.clearMemory();
        generations = TestPacks.SCALE_GENERATIONS.get();
        start = System.nanoTime();
        int disk = TestSupport.listScale(TestPacks.scale);
        timed("disk hit", start, disk);
        assertEquals(TestPacks.SCALE_FILES, disk, "disk scale files");
        assertEquals(generations, TestPacks.SCALE_GENERATIONS.get(), "disk hit must not run the generator");

        // Memory hit: the same key is served from memory.
        start = System.nanoTime();
        int memory = TestSupport.listScale(TestPacks.scale);
        timed("memory hit", start, memory);
        assertEquals(TestPacks.SCALE_FILES, memory, "memory scale files");
        assertEquals(generations, TestPacks.SCALE_GENERATIONS.get(), "memory hit must not run the generator");
    }

    private static void timed(String label, long start, int files) {
        LOGGER.info("SCALE_TEST: {}: {} files in {} ms", label, files, (System.nanoTime() - start) / 1_000_000);
    }
}
