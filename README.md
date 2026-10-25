# DynAssetGen

A small NeoForge library for generating data pack and resource pack files **at runtime**, in code. Typical uses are
compat content that only exists when other mods are loaded, or files derived from registries, such as one recipe per
wood type.

- Minecraft **26.1**, NeoForge **26.1.2.95+**, Java 25
- Packs are hidden, always enabled and fixed in position (bottom of the pack list by default)
- Optional per-generator caching in memory and on disk

## Installation

```kotlin
repositories {
    maven("https://maven.minecraftschurli.at/maven-public")
}

dependencies {
    implementation("at.minecraftschurli.mods:dynassetgen:1.0.0")
    // Bundle it in your jar:
    jarJar("at.minecraftschurli.mods:dynassetgen:[1.0.0,2.0.0)")
}
```

DynAssetGen is not a mod, so you don't need a dependency entry for it in your `neoforge.mods.toml`.

## Usage

Create a registrar in your mod constructor, create packs on it and add generators to the packs. Everything must be
registered during mod construction. Afterwards the packs are frozen and adding to them throws.

```java
@Mod("mymod")
public final class MyMod {
    public MyMod(IEventBus modBus) {
        DynamicPacks packs = DynamicPacks.create(modBus);

        DynamicPack compat = packs.data(Identifier.fromNamespaceAndPath("mymod", "compat"))
            .onlyIfModsLoaded("othermod");
        compat.add(Identifier.fromNamespaceAndPath("mymod", "planks_recipes"), new PlanksRecipes());

        DynamicPack lang = packs.assets(Identifier.fromNamespaceAndPath("mymod", "lang"));
        lang.add(Identifier.fromNamespaceAndPath("mymod", "lang"), new LangGenerator());
    }
}
```

- `data(id)` creates a data pack and `assets(id)` a resource pack. Pack ids must be unique across all mods.
- `onlyIf(condition)` / `onlyIfModsLoaded(modIds...)` turn off the whole pack. `position(...)` and `title(...)` change
  how it is listed.
- Each mod creates its own registrar from its own mod bus. The library registers the event listener for you.

### Generators

A `ResourceGenerator` writes **all** files of one unit of work to a `ResourceOutput`. Generating one file is just the
case of emitting once.

```java
public final class PlanksRecipes implements ResourceGenerator {
    @Override
    public void generate(GenerationContext ctx, ResourceOutput output) {
        for (WoodType wood : WoodTypes.all()) {
            Identifier location = Identifier.fromNamespaceAndPath("mymod", "recipe/" + wood.name() + "_planks.json");
            output.json(location, () -> planksRecipe(wood));
        }
    }
}
```

- Locations are pack-relative and include the folder, e.g. `mymod:recipe/oak_planks.json` ends up at
  `data/mymod/recipe/oak_planks.json`.
- `output.json(location, supplier)` writes a `JsonElement`. `output.codec(location, codec, supplier)` encodes a value
  with a codec. `output.accept(location, contentSupplier)` writes raw bytes.
- Content suppliers are lazy: an uncached generator only produces the bytes of the files that are actually read.
- Emitting the same location twice keeps the last one.
- `ResourceGenerator.conditional(condition, generator)` emits nothing while the condition is false.
  `ResourceGenerator.multi(generators...)` runs several generators as one.
- `Conditions.modsLoaded(...)`, `anyModLoaded(...)` and `not(...)` cover the common conditions.

For single files there is shorthand. In both, the location doubles as the generator id, and returning `null` means no
file:

```java
pack.addJson(Identifier.fromNamespaceAndPath("mymod", "tags/item/planks.json"), ctx -> planksTag());
pack.addCodec(Identifier.fromNamespaceAndPath("mymod", "loot_table/blocks/shelf.json"), LootTable.DIRECT_CODEC,
    ctx -> shelfLootTable());
```

### Caching

Generators run on every reload by default. To skip that, return a cache key from `createCacheKey`, similar to
NeoForge's `createGeometryKey`. The key must capture everything the output depends on:

```java
public record PlanksKey(String modVersion, List<String> woodTypes) {}

@Override
public @Nullable Object createCacheKey(GenerationContext ctx) {
    return new PlanksKey(MyMod.VERSION, WoodTypes.all().stream().map(WoodType::name).toList());
}
```

- **Memory:** when the key `equals` the key of the previous run in the same game session, the previous output is
  reused and `generate` is not called.
- **Disk:** keys are also hashed (class name + `toString()`) to reuse output across launches. There is one blob per
  generator in `<gamedir>/.cache/dynassetgen/`. This needs a deterministic `toString()` that covers all data. Records
  of strings, numbers, identifiers, enums and lists of those have one. Keys whose class does not override `toString()`
  are only cached in memory.
- The library adds nothing to the key. If the output depends on your mod version or a config value, put it in the key.
- `null` (the default) means the generator is not cached.

### Generation context

`GenerationContext` gives the pack id and type, `isModLoaded(modId)`, and `registries()` for codecs.

**Only built-in (static) registries are available**: items, blocks, recipe serializers and so on. Data-driven
registries (biomes, enchantments, ...) and tags are not loaded yet when generators run, so a generator cannot look them
up. It can still reference them by id in the JSON it writes.

DynAssetGen does not read files from other packs, and it does not integrate with known packs.

## Debugging

| System property             | Effect                                                                  |
|-----------------------------|-------------------------------------------------------------------------|
| `-Ddynassetgen.dump=true`   | Writes every generated file to `<gamedir>/.dynassetgen/dump/<pack>/...` |
| `-Ddynassetgen.cache=false` | Turns off the memory and disk cache                                     |

A generator that throws is logged and skipped in production. In a development environment the exception is rethrown,
so the failure is hard to miss.

## Building

```sh
./gradlew build
```

`build` also runs the JUnit tests in `src/test`. They load a small test mod (`dynassetgen_test`) and check pack
loading on an ephemeral server (NeoForge's `EphemeralTestServerProvider`), plus the cache layers.

## Credits and license

Inspired by [DynamicAssetGenerator](https://github.com/lukebemishprojects/DynamicAssetGenerator) by Luke Bemish. This
is an independent rewrite that contains no code from it.

Licensed under the MIT License.
