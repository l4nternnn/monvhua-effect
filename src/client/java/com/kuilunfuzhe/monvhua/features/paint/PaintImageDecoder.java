package com.kuilunfuzhe.monvhua.features.paint;

import net.minecraft.client.texture.NativeImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Decodes imported paint images through one format-independent path. */
public final class PaintImageDecoder {
    private PaintImageDecoder() {
    }

    public static NativeImage read(Path path) throws IOException {
        try (InputStream stream = Files.newInputStream(path)) {
            return read(stream);
        }
    }

    public static NativeImage read(InputStream stream) throws IOException {
        BufferedImage buffered = ImageIO.read(stream);
        if (buffered == null) {
            throw new IOException("Unsupported image format");
        }
        NativeImage image = new NativeImage(buffered.getWidth(), buffered.getHeight(), false);
        for (int y = 0; y < buffered.getHeight(); y++) {
            for (int x = 0; x < buffered.getWidth(); x++) {
                image.setColorArgb(x, y, buffered.getRGB(x, y));
            }
        }
        return image;
    }
}
