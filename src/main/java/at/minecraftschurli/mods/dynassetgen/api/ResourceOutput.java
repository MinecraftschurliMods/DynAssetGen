package at.minecraftschurli.mods.dynassetgen.api;

import at.minecraftschurli.mods.dynassetgen.impl.ContentEncoder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

/// Receives the files a [ResourceGenerator] produces. Later calls for the same location replace earlier ones.
///
/// Caching is per generator, see [ResourceGenerator#createCacheKey]. Files written here are never cached on their
/// own.
@FunctionalInterface
public interface ResourceOutput {
    /// Adds a file. The content is produced lazily, only when the file is actually read, unless the generator is cached.
    ///
    /// @param location the pack-relative location, e.g. `mymod:recipe/foo.json`
    /// @param content  the content producer
    void accept(Identifier location, ContentSupplier content);

    /// Adds a JSON file, encoded as UTF-8 with pretty printing.
    ///
    /// @param location the pack-relative location
    /// @param json     the JSON producer
    default void json(Identifier location, Supplier<JsonElement> json) {
        accept(location, () -> ContentEncoder.json(json.get()));
    }

    /// Adds a JSON file encoded with a codec. Registry references use the built-in registries.
    ///
    /// @param location the pack-relative location
    /// @param codec    the codec
    /// @param value    the value producer
    /// @param <T>      the value type
    /// @throws IllegalStateException if the value cannot be encoded, when the file is produced
    default <T> void codec(Identifier location, Codec<T> codec, Supplier<T> value) {
        accept(location, () -> ContentEncoder.codec(location, codec, value.get()));
    }
}
