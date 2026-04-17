package com.omnipost.service;

import com.omnipost.exception.VideoProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Service responsible for generating video thumbnails.
 * Attempts to use FFmpeg (if available on the system PATH) to extract
 * the first frame; falls back to a branded placeholder image.
 */
@Service
public class ThumbnailService {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailService.class);
    private static final int THUMBNAIL_WIDTH = 1280;
    private static final int THUMBNAIL_HEIGHT = 720;

    private final Path thumbnailStoragePath;
    private final Path videoStoragePath;

    public ThumbnailService(@Qualifier("thumbnailStoragePath") Path thumbnailStoragePath,
                            @Qualifier("videoStoragePath") Path videoStoragePath) {
        this.thumbnailStoragePath = thumbnailStoragePath;
        this.videoStoragePath = videoStoragePath;
    }

    /**
     * Generates a thumbnail for the provided video file.
     *
     * @param videoPath the path to the uploaded video file
     * @param title     the AI-generated title used in the placeholder thumbnail
     * @return the absolute path to the generated thumbnail image
     */
    public String generateThumbnail(Path videoPath, String title) {
        // Validate the video path is within the configured upload directory
        Path normalizedVideoPath = videoPath.normalize().toAbsolutePath();
        if (!normalizedVideoPath.startsWith(videoStoragePath.toAbsolutePath())) {
            throw new VideoProcessingException("Access denied: video path is outside the allowed storage directory.");
        }

        String thumbnailFileName = UUID.randomUUID() + "_thumbnail.jpg";
        Path thumbnailPath = thumbnailStoragePath.resolve(thumbnailFileName);

        // Try FFmpeg first (if installed)
        if (tryFFmpegExtraction(normalizedVideoPath, thumbnailPath)) {
            log.info("Thumbnail extracted via FFmpeg: {}", thumbnailPath);
            return thumbnailPath.toAbsolutePath().toString();
        }

        // Fall back to generating a branded placeholder
        log.info("FFmpeg not available or extraction failed. Generating placeholder thumbnail.");
        generatePlaceholderThumbnail(thumbnailPath.toFile(), title);
        return thumbnailPath.toAbsolutePath().toString();
    }

    private boolean tryFFmpegExtraction(Path videoPath, Path outputPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", videoPath.toString(),
                    "-ss", "00:00:01",
                    "-vframes", "1",
                    "-vf", "scale=" + THUMBNAIL_WIDTH + ":" + THUMBNAIL_HEIGHT + ":force_original_aspect_ratio=decrease",
                    outputPath.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0 && outputPath.toFile().exists();
        } catch (IOException | InterruptedException e) {
            log.debug("FFmpeg not available: {}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private void generatePlaceholderThumbnail(File outputFile, String title) {
        try {
            BufferedImage image = new BufferedImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();

            // Enable anti-aliasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Gradient background
            GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(15, 15, 30),
                    THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, new Color(30, 60, 120));
            g2d.setPaint(gradient);
            g2d.fillRect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);

            // Decorative accent bar at top
            g2d.setColor(new Color(255, 80, 0));
            g2d.fillRect(0, 0, THUMBNAIL_WIDTH, 8);

            // OmniPost-AI branding
            g2d.setColor(new Color(255, 80, 0));
            g2d.setFont(new Font("Arial", Font.BOLD, 48));
            drawCenteredString(g2d, "OmniPost-AI", THUMBNAIL_WIDTH, 120);

            // Title text
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 60));
            String displayTitle = title != null && !title.isBlank() ? title : "My Video";
            if (displayTitle.length() > 50) {
                displayTitle = displayTitle.substring(0, 47) + "...";
            }
            drawCenteredString(g2d, displayTitle, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT / 2);

            // Play button icon
            drawPlayButton(g2d, THUMBNAIL_WIDTH / 2, THUMBNAIL_HEIGHT / 2 + 120);

            // Accent bar at bottom
            g2d.setColor(new Color(255, 80, 0));
            g2d.fillRect(0, THUMBNAIL_HEIGHT - 8, THUMBNAIL_WIDTH, 8);

            g2d.dispose();

            ImageIO.write(image, "jpg", outputFile);
        } catch (IOException e) {
            throw new VideoProcessingException("Failed to generate placeholder thumbnail", e);
        }
    }

    private void drawCenteredString(Graphics2D g2d, String text, int width, int y) {
        FontMetrics metrics = g2d.getFontMetrics();
        int x = (width - metrics.stringWidth(text)) / 2;
        g2d.drawString(text, x, y);
    }

    private void drawPlayButton(Graphics2D g2d, int cx, int cy) {
        int radius = 60;
        // Circle background
        g2d.setColor(new Color(255, 80, 0, 200));
        g2d.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);

        // Triangle (play icon)
        g2d.setColor(Color.WHITE);
        int[] xPoints = {cx - 20, cx - 20, cx + 40};
        int[] yPoints = {cy - 30, cy + 30, cy};
        g2d.fillPolygon(xPoints, yPoints, 3);
    }
}
