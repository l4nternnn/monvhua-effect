package com.kuilunfuzhe.monvhua.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.kuilunfuzhe.monvhua.features.paint.PaintPaperStore;
import com.kuilunfuzhe.monvhua.item.paint.PaintPaperItem;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PaintGraffitiCommand {
    private static final int TILE_SIZE = 16;
    private static final int PAPER_TILE_SIZE = 25;
    private static final int PAPER_PIXEL_SIZE = PAPER_TILE_SIZE * TILE_SIZE;
    private static final int MAX_IMPORTED_PAPERS = 25 * 25;

    private PaintGraffitiCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("monvhua-graffiti")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("folder")
                        .executes(context -> showFolder(context.getSource())))
                .then(CommandManager.literal("import")
                        .then(CommandManager.argument("file", StringArgumentType.greedyString())
                                .executes(context -> importImage(context.getSource(), StringArgumentType.getString(context, "file"), 1.0D))))
                .then(CommandManager.literal("import_scaled")
                        .then(CommandManager.argument("scale", DoubleArgumentType.doubleArg(0.05D, 8.0D))
                                .then(CommandManager.argument("file", StringArgumentType.greedyString())
                                        .executes(context -> importImage(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "file"),
                                                DoubleArgumentType.getDouble(context, "scale")))))));
    }

    private static int showFolder(ServerCommandSource source) {
        Path folder = graffitiFolder(source);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            source.sendError(Text.literal("Failed to create graffiti folder: " + e.getMessage()));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Graffiti folder: " + folder), false);
        return 1;
    }

    private static int importImage(ServerCommandSource source, String filename, double scale) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }

        Path folder = graffitiFolder(source);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            source.sendError(Text.literal("Failed to create graffiti folder: " + e.getMessage()));
            return 0;
        }
        Path imagePath = folder.resolve(filename).normalize();
        if (!imagePath.startsWith(folder)) {
            source.sendError(Text.literal("Image path must stay inside the graffiti folder."));
            return 0;
        }
        if (!Files.isRegularFile(imagePath)) {
            source.sendError(Text.literal("Image not found: " + imagePath));
            return 0;
        }

        BufferedImage image;
        try {
            image = ImageIO.read(imagePath.toFile());
        } catch (IOException e) {
            source.sendError(Text.literal("Failed to read image: " + e.getMessage()));
            return 0;
        }
        if (image == null) {
            source.sendError(Text.literal("Unsupported image file: " + imagePath.getFileName()));
            return 0;
        }
        image = scaledImage(image, scale);

        int paperColumns = Math.max(1, (image.getWidth() + PAPER_PIXEL_SIZE - 1) / PAPER_PIXEL_SIZE);
        int paperRows = Math.max(1, (image.getHeight() + PAPER_PIXEL_SIZE - 1) / PAPER_PIXEL_SIZE);
        if (paperColumns * paperRows > MAX_IMPORTED_PAPERS) {
            source.sendError(Text.literal("Scaled image is too large: " + paperColumns + "x" + paperRows + " papers, max 25x25."));
            return 0;
        }

        ServerWorld world = source.getWorld();
        String imageName = baseName(imagePath.getFileName().toString());
        int created = 0;
        for (int paperY = 0; paperY < paperRows; paperY++) {
            for (int paperX = 0; paperX < paperColumns; paperX++) {
                int pixelStartX = paperX * PAPER_PIXEL_SIZE;
                int pixelStartY = paperY * PAPER_PIXEL_SIZE;
                int remainingWidth = Math.max(1, image.getWidth() - pixelStartX);
                int remainingHeight = Math.max(1, image.getHeight() - pixelStartY);
                int tileColumns = Math.min(PAPER_TILE_SIZE, Math.max(1, (remainingWidth + TILE_SIZE - 1) / TILE_SIZE));
                int tileRows = Math.min(PAPER_TILE_SIZE, Math.max(1, (remainingHeight + TILE_SIZE - 1) / TILE_SIZE));
                int paperSize = Math.max(tileColumns, tileRows);
                List<PaintPaperStore.Cell> cells = paperCells(image, paperX, paperY, tileColumns, tileRows);
                ItemStack paper = PaintPaperItem.createSavedPaper(
                        world,
                        imageName + "(" + (paperX + 1) + "," + (paperY + 1) + ")",
                        paperSize,
                        cells
                );
                if (!player.getInventory().insertStack(paper)) {
                    player.dropItem(paper, false);
                }
                created++;
            }
        }

        String message = "Imported " + imagePath.getFileName() + " at " + formatScale(scale) + "x as " + created + " paint papers from " + paperColumns + "x" + paperRows + " paper pages.";
        source.sendFeedback(() -> Text.literal(message), true);
        return created;
    }

    private static BufferedImage scaledImage(BufferedImage source, double scale) {
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        if (width == source.getWidth() && height == source.getHeight()) {
            return source;
        }
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return scaled;
    }

    private static Path graffitiFolder(ServerCommandSource source) {
        return source.getServer().getSavePath(WorldSavePath.ROOT).resolve("graffiti").normalize();
    }

    private static List<PaintPaperStore.Cell> paperCells(BufferedImage image, int paperX, int paperY, int tileColumns, int tileRows) {
        List<PaintPaperStore.Cell> cells = new ArrayList<>();
        int tileStartX = paperX * PAPER_TILE_SIZE;
        int tileStartY = paperY * PAPER_TILE_SIZE;
        for (int y = 0; y < tileRows; y++) {
            for (int x = 0; x < tileColumns; x++) {
                int[] pixels = tilePixels(image, tileStartX + x, tileStartY + y);
                if (hasPixels(pixels)) {
                    cells.add(new PaintPaperStore.Cell(x, y, pixels));
                }
            }
        }
        return cells;
    }

    private static int[] tilePixels(BufferedImage image, int tileX, int tileY) {
        int[] pixels = new int[TILE_SIZE * TILE_SIZE];
        int startX = tileX * TILE_SIZE;
        int startY = tileY * TILE_SIZE;
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                int imageX = startX + x;
                int imageY = startY + y;
                if (imageX >= image.getWidth() || imageY >= image.getHeight()) {
                    continue;
                }
                int argb = image.getRGB(imageX, imageY);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                pixels[y * TILE_SIZE + x] = argb;
            }
        }
        return pixels;
    }

    private static boolean hasPixels(int[] pixels) {
        for (int pixel : pixels) {
            if (pixel != 0) {
                return true;
            }
        }
        return false;
    }

    private static String baseName(String filename) {
        int dot = filename.lastIndexOf('.');
        String name = dot <= 0 ? filename : filename.substring(0, dot);
        return name.isBlank() ? "image" : name;
    }

    private static String formatScale(double scale) {
        return String.format(java.util.Locale.ROOT, "%.2f", scale);
    }
}
