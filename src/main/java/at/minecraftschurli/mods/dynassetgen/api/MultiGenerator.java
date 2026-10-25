package at.minecraftschurli.mods.dynassetgen.api;

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// A generator that delegates to multiple generators. See [ResourceGenerator#multi].
/// @param generators the generators to delegate to
public record MultiGenerator(List<ResourceGenerator> generators) implements ResourceGenerator {
    public MultiGenerator {
        generators = ImmutableList.copyOf(generators);
    }

    @Override
    public @Nullable Object createCacheKey(GenerationContext ctx) {
        ImmutableList.Builder<Object> builder = ImmutableList.builder();
        for (ResourceGenerator generator : generators) {
            Object key = generator.createCacheKey(ctx);
            if (key == null) {
                return null; // If any generator is not cacheable, the whole MultiGenerator is not cacheable
            }
            builder.add(key);
        }
        return new CacheKey(builder.build());
    }

    @Override
    public void generate(GenerationContext ctx, ResourceOutput output) {
        for (ResourceGenerator generator : generators) {
            generator.generate(ctx, output);
        }
    }
    
    private record CacheKey(List<Object> keys) {}
}
