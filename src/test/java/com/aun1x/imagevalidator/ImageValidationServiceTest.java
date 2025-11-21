package com.aun1x.imagevalidator;

import com.aun1x.imagevalidator.service.ImageValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ImageValidationServiceTest {

    @Autowired
    private ImageValidationService service;

    @Test
    void testEmptyFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]);
        var result = service.validateImage(file, 2.0, 2.0, 300);
        assertFalse(result.valid);
        assertEquals("File is empty", result.message);
    }

    @Test
    void testInvalidFileType() throws IOException {
        MockMultipartFile file = new MockMultipartFile("image", "test.txt", "text/plain", "hello".getBytes());
        var result = service.validateImage(file, 2.0, 2.0, 300);
        assertFalse(result.valid);
        assertEquals("Invalid file type: must be an image", result.message);
    }

    @Test
    void testInvalidDimensions() throws IOException {
        MockMultipartFile file = new MockMultipartFile("image", "test.jpg", "image/jpeg", "dummy".getBytes());
        var result = service.validateImage(file, 0, 2.0, 300);
        assertFalse(result.valid);
        assertEquals("Invalid physical dimensions: must be positive inches", result.message);
    }

    // Note: For full image validation tests, you would need actual image bytes.
    // This is a basic structure; expand with real image data for comprehensive testing.
}