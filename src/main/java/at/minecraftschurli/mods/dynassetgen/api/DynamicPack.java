package at.minecraftschurli.mods.dynassetgen.api;

import at.minecraftschurli.mods.dynassetgen.impl.RegisteredGenerator;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/// A pack whose files are generated at runtime by code. Create one in mod construction with
/// [DynamicPacks#data(Identifier)] or [DynamicPacks#assets(Identifier)], then add generators to it.
public final class DynamicPack {
    private final DynamicPacks owner;
    private final Identifier id;
    private final PackType packType;
    private final List<RegisteredGenerator> generators = new ArrayList<>();
    private final List<BooleanSupplier> conditions = new CopyOnWriteArrayList<>();
    private final List<Runnable> resetListeners = new CopyOnWriteArrayList<>();
    private volatile Pack.Position position = Pack.Position.BOTTOM;
    private volatile Component title;

    DynamicPack(DynamicPacks owner, Identifier id, PackType packType) {
        this.owner = owner;
        this.id = id;
        this.packType = packType;
        this.title = Component.literal("Generated: " + id);
    }

    /// Throws if the registrar that owns this pack no longer accepts changes. Packs call this before they change, so
    /// that generators cannot be added after the pack was handed to the game.
    ///
    /// @throws IllegalStateException if the pack can no longer be changed
    @ApiStatus.Internal
    public void checkNotFrozen() {
        owner.checkNotFrozen();
    }

    /// Sets where the pack sits in the pack list. Defaults to [Pack.Position#BOTTOM].
    ///
    /// @param position the position
    /// @return this pack
    public DynamicPack position(Pack.Position position) {
        checkNotFrozen();
        this.position = position;
        return this;
    }

    /// Sets the title shown in the pack list. Defaults to `Generated: <id>`.
    ///
    /// @param title the title
    /// @return this pack
    public DynamicPack title(Component title) {
        checkNotFrozen();
        this.title = title;
        return this;
    }

    /// Adds a condition for the whole pack. Multiple conditions are combined with AND.
    ///
    /// @param condition the condition, evaluated when packs are collected
    /// @return this pack
    public DynamicPack onlyIf(BooleanSupplier condition) {
        checkNotFrozen();
        conditions.add(condition);
        return this;
    }

    /// Adds a condition for the whole pack that requires all given mods to be loaded.
    ///
    /// @param modIds the mod ids
    /// @return this pack
    public DynamicPack onlyIfModsLoaded(String... modIds) {
        return onlyIf(Conditions.modsLoaded(modIds));
    }

    /// Adds a generator. Its id names its cache blob, so it must be unique within this pack.
    ///
    /// @param generatorId the generator id, e.g. `mymod:recipes`
    /// @param generator   the generator
    /// @return this pack
    /// @throws IllegalStateException    if packs can no longer be changed
    /// @throws IllegalArgumentException if a generator with this id already exists in this pack
    public synchronized DynamicPack add(Identifier generatorId, ResourceGenerator generator) {
        checkNotFrozen();
        Objects.requireNonNull(generatorId, "generatorId");
        Objects.requireNonNull(generator, "generator");
        for (RegisteredGenerator existing : generators) {
            if (existing.id().equals(generatorId)) {
                throw new IllegalArgumentException(id + " already has a generator with id " + generatorId);
            }
        }
        generators.add(new RegisteredGenerator(generatorId, generator));
        return this;
    }

    /// Adds a JSON file. A `null` result means no file. The generator id is the location.
    ///
    /// @param location the pack-relative location
    /// @param json     the JSON producer
    /// @throws IllegalArgumentException if a generator with this id already exists in this pack
    public void addJson(Identifier location, Function<GenerationContext, @Nullable JsonElement> json) {
        add(location, (ctx, output) -> {
            JsonElement element = json.apply(ctx);
            if (element != null) output.json(location, () -> element);
        });
    }

    /// Adds a JSON file encoded with a codec. A `null` value means no file. The generator id is the location.
    ///
    /// Registry references are encoded with the built-in registries of [GenerationContext#registries()].
    ///
    /// @param location the pack-relative location
    /// @param codec    the codec
    /// @param value    the value producer
    /// @param <T>      the value type
    /// @throws IllegalArgumentException if a generator with this id already exists in this pack
    public <T> void addCodec(Identifier location, Codec<T> codec, Function<GenerationContext, @Nullable T> value) {
        add(location, (ctx, output) -> {
            T v = value.apply(ctx);
            if (v != null) output.codec(location, codec, () -> v);
        });
    }

    /// Adds a listener that runs each time the pack is opened for a reload.
    ///
    /// @param listener the listener
    public void onReset(Runnable listener) {
        checkNotFrozen();
        resetListeners.add(listener);
    }

    /// Returns the pack id.
    ///
    /// @return the id
    public Identifier id() {
        return id;
    }

    /// Returns the pack type.
    ///
    /// @return the type
    public PackType packType() {
        return packType;
    }

    /// Returns the position in the pack list.
    ///
    /// @return the position
    public Pack.Position position() {
        return position;
    }

    /// Returns the title shown in the pack list.
    ///
    /// @return the title
    public Component title() {
        return title;
    }

    /// Returns whether all pack-level conditions hold.
    ///
    /// @return `true` if the pack should be loaded
    public boolean isEnabled() {
        for (BooleanSupplier condition : conditions) {
            if (!condition.getAsBoolean()) return false;
        }
        return true;
    }

    /// Returns the registered generators in insertion order.
    ///
    /// @return the registered generators
    @ApiStatus.Internal
    public synchronized List<RegisteredGenerator> generators() {
        return List.copyOf(generators);
    }

    /// Returns the listeners that run when the pack is opened.
    ///
    /// @return the reset listeners
    @ApiStatus.Internal
    public List<Runnable> resetListeners() {
        return resetListeners;
    }
}
