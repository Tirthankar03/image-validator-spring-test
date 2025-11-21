package com.tvscs.imagevalidator.controller;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tvscs.imagevalidator.domain.dto.ValidationResponse;
import com.tvscs.imagevalidator.service.ImageValidationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;

/**
 * REST Controller for the /api/validate endpoint.
 * Handles multipart uploads and returns JSON with validation results.
 */
@RestController
@Validated
@Tag(name = "Image Validation", description = "API for validating image resolution and blurriness")
public class ImageValidationController {

    private static final Logger log = LoggerFactory.getLogger(ImageValidationController.class);

    private final ImageValidationService validationService;

    public ImageValidationController(ImageValidationService validationService) {
        this.validationService = validationService;
    }

    /**
     * POST /api/validate
     * Required: image (file), x_inches (double), y_inches (double)
     * Optional: target_dpi (int, defaults to 300)
     */
    @PostMapping(value = "/api/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Validate image resolution and blurriness",
               description = "Uploads an image and validates it against target physical dimensions and DPI requirements.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Validation successful",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed or invalid input",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationResponse.class)))
    })
    public ResponseEntity<ValidationResponse> validateImage(
            @Parameter(description = "Image file to validate", required = true)
            @RequestParam("image") MultipartFile file,
            @Parameter(description = "Target width in inches", required = true)
            @RequestParam("x_inches") @DecimalMin(value = "0.1", message = "x_inches must be greater than 0") double xInches,
            @Parameter(description = "Target height in inches", required = true)
            @RequestParam("y_inches") @DecimalMin(value = "0.1", message = "y_inches must be greater than 0") double yInches,
            @Parameter(description = "Target DPI (optional, defaults to 300)")
            @RequestParam(value = "target_dpi", required = false) Integer targetDpi) {
        try {
            log.info("Received validation request for file: {}, size: {}", file.getOriginalFilename(), file.getSize());
            ImageValidationService.ValidationResult result = validationService.validateImage(file, xInches, yInches, targetDpi);

            ValidationResponse response = new ValidationResponse(
                result.valid ? "valid" : "invalid",
                result.message,
                result.suggestion,
                result.effectiveDpi,
                result.widthPct,
                result.heightPct
            );

            HttpStatus status = result.valid ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(response);
        } catch (IOException e) {
            log.error("IO error during image validation", e);
            ValidationResponse errorResponse = new ValidationResponse(
                "error",
                "Failed to process image: " + e.getMessage(),
                null,
                null,
                null,
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } catch (Exception e) {
            log.error("Unexpected error during image validation", e);
            ValidationResponse errorResponse = new ValidationResponse(
                "error",
                "Unexpected error: " + e.getMessage(),
                null,
                null,
                null,
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}