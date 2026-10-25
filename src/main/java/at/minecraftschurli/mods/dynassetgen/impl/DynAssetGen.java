package at.minecraftschurli.mods.dynassetgen.impl;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

/**
 * Internal constants of DynAssetGen. DynAssetGen is a game library, not a mod: it has no entrypoint. Packs are created
 * with {@link at.minecraftschurli.mods.dynassetgen.api.DynamicPacks}.
 */
@ApiStatus.Internal
public final class DynAssetGen {
    /** The library id, used for the {@code dynassetgen/} pack id prefix and the cache directories. */
    public static final String ID = "dynassetgen";
    /** The library logger. */
    public static final Logger LOGGER = LogUtils.getLogger();

    private DynAssetGen() {}
}
