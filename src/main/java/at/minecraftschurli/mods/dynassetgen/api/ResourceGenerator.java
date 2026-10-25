package at.minecraftschurli.mods.dynassetgen.api;

import java.util.List;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/// Produces the files of one dynamic pack. Register it with [DynamicPack#add]. 
@FunctionalInterface
public interface ResourceGenerator {
    /// Writes every file this generator produces to the output. Called once per reload, unless the output is served
    /// from the cache.
    ///
    /// @param ctx    the generation context
    /// @param output the output that receives the files
    void generate(GenerationContext ctx, ResourceOutput output);

    /// Collects all data that influences what this generator emits, like NeoForge's `createGeometryKey`. If the
    /// key equals the key of a previous run, the previous output is reused and [#generate] is not called.
    ///
    /// `null`, the default, means the generator is not cacheable. The key must implement `equals` and
    /// `hashCode`. For reuse across game launches its `toString()` must also be deterministic and cover all
    /// data. Records of strings, numbers, identifiers and enums do this automatically. The library adds nothing to the
    /// key, so include your own mod version etc. if the output depends on it.
    ///
    /// @param ctx the generation context
    /// @return the cache key, or `null` if the generator is not cacheable
    default @Nullable Object createCacheKey(GenerationContext ctx) {
        return null;
    }

    /// Wraps a generator so it emits nothing when the condition is false. The condition is evaluated on each reload.
    ///
    /// @param condition the condition
    /// @param generator the wrapped generator
    /// @return the conditional generator
    static ResourceGenerator conditional(BooleanSupplier condition, ResourceGenerator generator) {
        return new ConditionalGenerator(condition, generator);
    }

    /// Creates a generator that delegates to multiple generators. See [MultiGenerator].
    ///
    /// @param generators the generators to delegate to
    /// @return the multi generator
    static ResourceGenerator multi(ResourceGenerator... generators) {
        return new MultiGenerator(List.of(generators));
    }
}
