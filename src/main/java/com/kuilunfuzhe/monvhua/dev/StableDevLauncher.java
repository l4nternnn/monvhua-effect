package com.kuilunfuzhe.monvhua.dev;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** JDK-only development bootstrap; never invoked by the published mod entrypoints. */
public final class StableDevLauncher {
    private StableDevLauncher() {}

    public static void main(String[] args) throws Throwable {
        if (System.getProperty("fabric.dli.config") == null) {
            throw new IllegalStateException("Use the Loom development run configuration");
        }
        try (var archives = Archives.open(Path.of("mods"))) {
            System.out.println("[monvhua-dev] Holding " + archives.owned.size()
                    + " mod archives open to protect runtime remapping");
            try {
                Class.forName("net.fabricmc.devlaunchinjector.Main").getMethod("main", String[].class)
                        .invoke(null, (Object) args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    /** Own only file systems created here; never close another component's handle. */
    public static final class Archives implements AutoCloseable {
        private final List<FileSystem> owned = new ArrayList<>();

        public static Archives open(Path directory) throws IOException {
            var result = new Archives();
            if (!Files.isDirectory(directory)) return result;
            try (var paths = Files.list(directory)) {
                for (var path : paths.filter(p -> Files.isRegularFile(p)
                        && !p.getFileName().toString().startsWith(".")
                        && p.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                    // URI overload shares the registry used by Loader/TinyRemapper;
                    // the Path overload would create a separate, ineffective ZipFS.
                    URI uri = URI.create("jar:" + path.toAbsolutePath().normalize().toUri());
                    try {
                        result.owned.add(FileSystems.newFileSystem(uri, Map.of()));
                    } catch (FileSystemAlreadyExistsException ignored) {
                        // Existing owners retain responsibility for their resources.
                    }
                }
                return result;
            } catch (IOException | RuntimeException e) {
                try { result.close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
                throw e;
            }
        }

        @Override public void close() throws IOException {
            IOException failure = null;
            for (int i = owned.size() - 1; i >= 0; i--) {
                try { owned.get(i).close(); }
                catch (IOException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
            }
            owned.clear();
            if (failure != null) throw failure;
        }
    }
}
