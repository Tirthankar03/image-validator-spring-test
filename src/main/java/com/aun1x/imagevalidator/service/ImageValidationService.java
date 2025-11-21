package com.aun1x.imagevalidator.service;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Service for validating uploaded images on resolution (target inches + DPI) and blurriness (Laplacian variance).
 * Returns a structured ValidationResult for flexible error handling.
 */
@Service
@Slf4j
public class ImageValidationService {

    @Value("${app.image.default-target-dpi}")
    private int defaultTargetDpi;

    @Value("${app.image.min-pct}")
    private double minPct;

    @Value("${app.image.max-blur-variance}")
    private double maxBlurVariance;

    // Static initialization for OpenCV (load native library once on startup)
    static {
        // Ensure OpenCV is loaded; in production, handle platform-specific natives
        nu.pattern.OpenCV.loadShared();  // From opencv dep; or System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        log.info("OpenCV library loaded successfully");
    }

    /**
     * Inner DTO for validation results: Includes status, metrics, and suggestions for frontend.
     */
    public static class ValidationResult {
        public boolean valid = true;
        public String message = "";
        public double effectiveDpi = -1.0;
        public double widthPct = -1.0;
        public double heightPct = -1.0;
        public String suggestion = "";
    }

    /**
     * Validates the image against target physical size and DPI.
     * @param file Uploaded MultipartFile (image).
     * @param xInches Target width in inches (>0 required).
     * @param yInches Target height in inches (>0 required).
     * @param targetDpi Optional target DPI (defaults to app.image.default-target-dpi).
     * @return ValidationResult with details.
     * @throws IOException If file read fails.
     */
    public ValidationResult validateImage(MultipartFile file, double xInches, double yInches, Integer targetDpi) throws IOException {
        log.debug("Starting image validation for file: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());
        ValidationResult result = new ValidationResult();

        // Basic input validations
        if (file.isEmpty()) {
            result.valid = false;
            result.message = "File is empty";
            log.warn("Validation failed: file is empty");
            return result;
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            result.valid = false;
            result.message = "Invalid file type: must be an image";
            log.warn("Validation failed: invalid content type {}", contentType);
            return result;
        }
        if (xInches <= 0 || yInches <= 0) {
            result.valid = false;
            result.message = "Invalid physical dimensions: must be positive inches";
            log.warn("Validation failed: invalid dimensions x={}, y={}", xInches, yInches);
            return result;
        }

        // Utility: Extract pixel dimensions using ImageIO (efficient for metadata)
        byte[] bytes = file.getBytes();
        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));
        if (bufferedImage == null) {
            result.valid = false;
            result.message = "Invalid image format";
            log.warn("Validation failed: cannot read image format");
            return result;
        }
        int widthPx = bufferedImage.getWidth();
        int heightPx = bufferedImage.getHeight();
        if (widthPx == 0 || heightPx == 0) {
            result.valid = false;
            result.message = "Image has zero dimensions";
            log.warn("Validation failed: zero dimensions");
            return result;
        }

        // Use provided or default DPI
        int dpi = (targetDpi != null && targetDpi > 0) ? targetDpi : defaultTargetDpi;

        // Compute required pixels for target size/DPI
        double reqWidthPx = xInches * dpi;
        double reqHeightPx = yInches * dpi;

        // Compute percentages against required
        result.widthPct = (widthPx / reqWidthPx) * 100;
        result.heightPct = (heightPx / reqHeightPx) * 100;

        // Resolution check: Fail if below min percentage
        boolean resFail = (result.widthPct < minPct || result.heightPct < minPct);
        if (resFail) {
            result.valid = false;
            result.message = String.format("Insufficient resolution for %.1fx%.1f inches @ %d DPI: %dx%d px (%.1f%% x %.1f%% of required %.0fx%.0f px).",
                    xInches, yInches, dpi, widthPx, heightPx, result.widthPct, result.heightPct, reqWidthPx, reqHeightPx);
            double minReqWidth = reqWidthPx * (minPct / 100);
            double minReqHeight = reqHeightPx * (minPct / 100);
            result.suggestion = String.format("Use ≥%.0fx%.0f px image (e.g., scan/capture at ≥%.0f DPI).",
                    minReqWidth, minReqHeight, dpi * (minPct / 100));
            log.warn("Validation failed: insufficient resolution");
            return result;  // Early exit on res fail
        }

        // Compute effective DPI (min of x/y for conservative estimate)
        result.effectiveDpi = Math.min(widthPx / xInches, heightPx / yInches);
        double effectivePct = (result.effectiveDpi / dpi) * 100;
        if (effectivePct < minPct) {
            result.valid = false;
            result.message = String.format("Low effective DPI: %.1f (%.1f%% of target %d).", result.effectiveDpi, effectivePct, dpi);
            result.suggestion += " Increase capture resolution.";
            log.warn("Validation failed: low effective DPI");
            return result;
        }

        // Optional: Aspect ratio check (tolerance 20% of target AR)
        double targetAR = xInches / yInches;
        double actualAR = (double) widthPx / heightPx;
        double arTolerance = 0.20;
        if (Math.abs(actualAR - targetAR) > arTolerance * targetAR) {
            result.valid = false;
            result.message += " Aspect ratio mismatch.";
            result.suggestion += String.format(" Expected ~%.2f (from %.1fx%.1f inches).", targetAR, xInches, yInches);
            log.warn("Validation failed: aspect ratio mismatch");
            return result;
        }

        // Blurriness check using Laplacian variance
        double variance = computeLaplacianVariance(bytes);
        if (variance < maxBlurVariance) {
            result.valid = false;
            // Heuristic blur %: Normalize variance to 0-100% (tune *2 based on max observed sharp variance)
            double blurPercentage = Math.max(0, (1 - (variance / (maxBlurVariance * 2))) * 100);
            result.message += String.format(" Image too blurry (estimated %.1f%% blur, variance=%.2f < threshold=%.2f).",
                    blurPercentage, variance, maxBlurVariance);
            result.suggestion += " Ensure steady capture with good lighting; avoid motion blur.";
            log.warn("Validation failed: image too blurry, variance={}", variance);
        }

        // Success message
        if (result.valid) {
            result.message = String.format("Valid for %.1fx%.1f inches @ %d DPI (effective %.1f DPI, %d x %d px).",
                    xInches, yInches, dpi, result.effectiveDpi, widthPx, heightPx);
            result.suggestion = "Ready for processing/printing.";
            log.info("Validation passed for file: {}", file.getOriginalFilename());
        }

        return result;
    }

    /**
     * Computes Laplacian variance for blurriness: High variance = sharp edges (not blurry).
     * @param imageBytes Raw image bytes.
     * @return Variance (e.g., >100 = sharp).
     */
    private double computeLaplacianVariance(byte[] imageBytes) {
        // Load as grayscale Mat (OpenCV matrix)
        Mat matGray = Imgcodecs.imdecode(new org.opencv.core.MatOfByte(imageBytes), Imgcodecs.IMREAD_GRAYSCALE);
        if (matGray.empty()) {
            throw new IllegalArgumentException("Failed to load image for blur processing");
        }

        // Apply Laplacian filter (edge detector)
        Mat destination = new Mat();
        Imgproc.Laplacian(matGray, destination, CvType.CV_64F);

        // Compute std dev, then variance = std^2
        org.opencv.core.MatOfDouble mean = new org.opencv.core.MatOfDouble();
        org.opencv.core.MatOfDouble std = new org.opencv.core.MatOfDouble();
        Core.meanStdDev(destination, mean, std);
        double variance = Math.pow(std.get(0, 0)[0], 2);

        // Cleanup (prevents memory leaks)
        matGray.release();
        destination.release();
        mean.release();
        std.release();

        return variance;
    }
}