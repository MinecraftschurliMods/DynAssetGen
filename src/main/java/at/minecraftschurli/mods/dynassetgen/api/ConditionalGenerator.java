package at.minecraftschurli.mods.dynassetgen.api;

import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/// A generator that emits nothing while its condition is false. See [ResourceGenerator#conditional].
///
/// @param condition the condition
/// @param delegate  the wrapped generator
record ConditionalGenerator(BooleanSupplier condition, ResourceGenerator delegate) implements ResourceGenerator {
    @Override
    public @Nullable Object createCacheKey(GenerationContext ctx) {
        return condition.getAsBoolean() ? delegate.createCacheKey(ctx) : null;
    }

    @Override
    public void generate(GenerationContext ctx, ResourceOutput output) {
        if (condition.getAsBoolean()) delegate.generate(ctx, output);
    }
}
