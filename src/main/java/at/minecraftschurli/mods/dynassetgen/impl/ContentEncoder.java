package at.minecraftschurli.mods.dynassetgen.impl;

import com.google.gson.FormattingStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

/// Encodes the content of files written through [at.minecraftschurli.mods.dynassetgen.api.ResourceOutput]. 
@ApiStatus.Internal
public final class ContentEncoder {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setFormattingStyle(FormattingStyle.COMPACT).create();

    private ContentEncoder() {}

    /// Encodes JSON as UTF-8 with pretty printing.
    ///
    /// @param json the element
    /// @return the bytes
    public static byte[] json(JsonElement json) {
        return GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
    }

    /// Encodes a value with a codec, using the built-in registries, and then as JSON.
    ///
    /// @param location the location, used in the error message
    /// @param codec    the codec
    /// @param value    the value
    /// @param <T>      the value type
    /// @return the bytes
    /// @throws IllegalStateException if the value cannot be encoded
    public static <T> byte[] codec(Identifier location, Codec<T> codec, T value) {
        JsonElement json = codec.encodeStart(StaticRegistries.get().createSerializationContext(JsonOps.INSTANCE), value)
            .getOrThrow(message -> new IllegalStateException("Failed to encode " + location + ": " + message));
        return json(json);
    }
}
