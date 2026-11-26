package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.dev.StableDevLauncher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class DevArchiveLifetimeTest {
    @TempDir Path directory;
    private URI jar() throws Exception {
        var path = directory.resolve("test with spaces.jar");
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("asset.txt"));
            output.write(new byte[]{1, 2, 3});
            output.closeEntry();
        }
        return URI.create("jar:" + path.toAbsolutePath().toUri());
    }
    @Test void remapperReferencesCannotCloseBootstrapOwnedArchive() throws Exception {
        URI uri = jar();
        FileSystem fs;
        try (var lease = StableDevLauncher.Archives.open(directory)) {
            fs = FileSystems.getFileSystem(uri);
            // Exercise the same external-owner branch used by Loader's shaded TinyRemapper.
            var reference = CompletableFuture.supplyAsync(() -> {
                try { return net.fabricmc.loader.impl.lib.tinyremapper.FileSystemReference.open(uri, false); }
                catch (Exception e) { throw new RuntimeException(e); }
            }).join();
            assertSame(fs, reference.getPath("/").getFileSystem());
            reference.close();
            assertTrue(fs.isOpen());
            assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(fs.getPath("/asset.txt")));
        }
        assertFalse(fs.isOpen());
    }
    @Test void borrowedFileSystemRemainsOpenAndPartialFailuresReleaseOwnedOnes() throws Exception {
        URI uri = jar();
        try (var external = FileSystems.newFileSystem(uri, Map.of())) {
            try (var lease = StableDevLauncher.Archives.open(directory)) { assertTrue(external.isOpen()); }
            assertTrue(external.isOpen());
        }
        Files.writeString(directory.resolve("zz-broken.jar"), "not a zip");
        assertThrows(java.io.IOException.class, () -> StableDevLauncher.Archives.open(directory));
        assertThrows(FileSystemNotFoundException.class, () -> FileSystems.getFileSystem(uri));
    }
}
