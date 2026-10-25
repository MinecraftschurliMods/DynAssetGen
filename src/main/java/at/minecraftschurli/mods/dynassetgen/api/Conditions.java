package at.minecraftschurli.mods.dynassetgen.api;

import java.util.function.BooleanSupplier;
import net.neoforged.fml.ModList;

/// Common conditions for generators and packs. Conditions are evaluated when they are used, not when they are created. 
public final class Conditions {
    private Conditions() {}

    /// Returns a condition that holds when all given mods are loaded.
    ///
    /// @param modIds the mod ids
    /// @return the condition
    public static BooleanSupplier modsLoaded(String... modIds) {
        String[] ids = modIds.clone();
        return () -> {
            for (String id : ids) {
                if (!ModList.get().isLoaded(id)) return false;
            }
            return true;
        };
    }

    /// Returns a condition that holds when at least one of the given mods is loaded.
    ///
    /// @param modIds the mod ids
    /// @return the condition
    public static BooleanSupplier anyModLoaded(String... modIds) {
        String[] ids = modIds.clone();
        return () -> {
            for (String id : ids) {
                if (ModList.get().isLoaded(id)) return true;
            }
            return false;
        };
    }

    /// Negates a condition.
    ///
    /// @param condition the condition to negate
    /// @return the negated condition
    public static BooleanSupplier not(BooleanSupplier condition) {
        return () -> !condition.getAsBoolean();
    }
}
