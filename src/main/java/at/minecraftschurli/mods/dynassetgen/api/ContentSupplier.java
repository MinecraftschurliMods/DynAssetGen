package at.minecraftschurli.mods.dynassetgen.api;

import java.io.IOException;

/// Produces the bytes of one file. Called only when the file is actually read, or never if a cached copy exists. 
@FunctionalInterface
public interface ContentSupplier {
    /// Returns the file content.
    ///
    /// @return the content bytes
    /// @throws IOException if the content cannot be produced
    byte[] get() throws IOException;
}
