# Image Validation API

[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.0-green)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-Central-red)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A production-ready Spring Boot REST API for validating uploaded images, focusing on **resolution quality** (based on target physical size and DPI) and **blurriness detection**. Ideal for document uploads like passports, Aadhaar cards, or ID proofs, where sharpness and sufficient resolution prevent poor-quality submissions.

This API ensures images are suitable for printing or digital processing by checking if they meet configurable thresholds, providing detailed feedback for frontend error handling (e.g., "Rescan at higher DPI").

## Features

- **Resolution Validation**: Computes required pixels from target inches and DPI (default: 300 DPI). Tolerates undersizing via percentage threshold (e.g., ≥80% of required).
- **Blurriness Check**: Uses OpenCV's Laplacian variance to detect blur (e.g., motion shake). Estimates blur percentage for user-friendly messages.
- **Aspect Ratio Validation**: Optional check against target proportions (20% tolerance).
- **Rich Responses**: JSON with status, metrics (effective DPI, percentages), and suggestions (e.g., "Use ≥600x600 px").
- **Flexible Inputs**: Multipart form-data with optional target DPI.
- **Configurable**: Thresholds via `application.properties`.
- **Error Handling**: Graceful failures for invalid files, dimensions, or formats with global exception handling.
- **Performance**: Fast (ms per image); handles up to 5MB files (configurable).
- **API Documentation**: Integrated Swagger/OpenAPI UI for easy testing and documentation.
- **Health Monitoring**: Spring Boot Actuator for health checks and metrics.
- **Validation**: Input validation with Bean Validation annotations.
- **Logging**: Comprehensive logging for debugging and monitoring.
- **Docker Support**: Ready-to-deploy with Dockerfile and docker-compose.

## Prerequisites

- Java 17+ (tested on 21).
- Maven 3.6+ (or use included Maven Wrapper).
- OpenCV Java bindings (via Maven; natives auto-loaded).
- For development: IDE like IntelliJ/Eclipse.

**OpenCV Note**: On startup, it loads shared libraries. If issues (e.g., on Windows), ensure `opencv_java490.dll` (or platform equiv.) is in PATH/JAVA_OPTS.

## Installation & Setup

1. **Clone/Extract Project**:
    ```
    git clone <repo-url>  # Or download ZIP
    cd image-validator
    ```

2. **Build**:
    ```
    ./mvnw clean install
    ```

3. **Run**:
    ```
    ./mvnw spring-boot:run
    ```
    - App starts on `http://localhost:8080`.
    - Health check: `GET http://localhost:8080/actuator/health`.
    - API Docs: `GET http://localhost:8080/swagger-ui.html`.

4. **Docker**:
    ```bash
    docker-compose up --build
    ```
    - App runs on `http://localhost:8080`.

## Configuration

Edit `src/main/resources/application.properties`:
```properties
# Core Thresholds
app.image.default-target-dpi=300      # Default DPI for calculations (high for sharp docs)
app.image.min-pct=80                  # Min % of required pixels (80% tolerance)
app.image.max-blur-variance=100       # Blur threshold (variance < this = too blurry; tune with samples)

# Upload Limits
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=5MB

# Server
server.port=8080

# Logging
logging.level.com.aun1x.imagevalidator=DEBUG
logging.level.org.opencv=INFO

# Actuator endpoints
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=when-authorized

# Swagger UI
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
```

**Tuning Tips**:
- **Resolution**: Test with sample images; set `min-pct=85` for stricter IDs.
- **Blur**: Compute variance on sharp/blurry samples (e.g., via separate OpenCV script). Sharp: 150-400; Blurry: <100.

## API Endpoints

### `POST /api/validate`

**Description**: Upload an image and validate against target physical dimensions (inches) and DPI. Supports JPEG/PNG (extendable).

**Request**:
- **Content-Type**: `multipart/form-data`
- **Parameters**:
  | Name          | Type    | Required | Description |
  |---------------|---------|----------|-------------|
  | `image`      | File    | Yes     | Image file (e.g., passport.jpg). |
  | `x_inches`   | double  | Yes     | Target width in inches (e.g., 2.0 for passport). Must be > 0. |
  | `y_inches`   | double  | Yes     | Target height in inches (e.g., 2.0). Must be > 0. |
  | `target_dpi` | int     | No      | Target DPI (default: 300). |

**Example cURL**:
```bash
curl -X POST \
  -F "image=@/path/to/passport.jpg" \
  -F "x_inches=2.0" \
  -F "y_inches=2.0" \
  -F "target_dpi=300" \
  http://localhost:8080/api/validate
```

**Example JS (Fetch)**:
```javascript
const formData = new FormData();
formData.append('image', fileInput.files[0]);
formData.append('x_inches', '2.0');
formData.append('y_inches', '2.0');
formData.append('target_dpi', '300');

fetch('/api/validate', {
  method: 'POST',
  body: formData
})
.then(res => res.json())
.then(data => {
  if (data.status === 'valid') {
    console.log('Upload OK:', data.message);
  } else {
    alert(data.suggestion);  // e.g., "Rescan at ≥240 DPI"
  }
});
```

**Response** (JSON):
- **Success (200 OK)**:
  ```json
  {
    "status": "valid",
    "message": "Valid for 2.0x2.0 inches @ 300 DPI (effective 300.0 DPI, 600 x 600 px).",
    "suggestion": "Ready for processing/printing.",
    "effectiveDpi": 300.0,
    "widthPct": 100.0,
    "heightPct": 100.0
  }
  ```
- **Failure (400 Bad Request)**:
  ```json
  {
    "status": "invalid",
    "message": "Insufficient resolution for 2.0x2.0 inches @ 300 DPI: 450x450 px (75.0% x 75.0% of required 600x600 px). Image too blurry (estimated 60.0% blur, variance=40.00 < threshold=100.00).",
    "suggestion": "Use ≥480x480 px image (e.g., scan/capture at ≥240 DPI). Ensure steady capture with good lighting; avoid motion blur.",
    "effectiveDpi": 225.0,
    "widthPct": 75.0,
    "heightPct": 75.0
  }
  ```
- **Error (500 Internal Server Error)**:
  ```json
  {
    "status": "error",
    "message": "Failed to process image: ..."
  }
  ```

**Validation Logic**:
1. **Basics**: File type (image/*), non-empty, positive inches.
2. **Resolution**: Req px = inches * DPI. Fail if uploaded px < req * (min-pct/100).
3. **Effective DPI**: min(width/inches, height/inches). Fail if < target * (min-pct/100).
4. **Aspect Ratio**: Fail if |actual AR - target AR| > 20% (AR = width/height).
5. **Blur**: Laplacian variance < threshold → Fail with % estimate.

## Testing

### Unit Tests
Run tests with:
```bash
./mvnw test
```

Example test in `src/test/java/com/aun1x/imagevalidator/ImageValidationServiceTest.java`:
```java
@SpringBootTest
class ImageValidationServiceTest {

    @Autowired
    private ImageValidationService service;

    @Test
    void testValidImage() throws IOException {
        // Create a mock MultipartFile with valid image bytes
        MockMultipartFile file = new MockMultipartFile("image", "test.jpg", "image/jpeg", validImageBytes);
        var result = service.validateImage(file, 2.0, 2.0, 300);
        assertTrue(result.valid);
    }
}
```

### Integration Tests
- Use Postman/Newman or the cURL examples.
- Sample Images: Create via GIMP (e.g., 600x600 sharp JPEG for pass; 450x450 blurry for fail).
- Coverage: Test all cases from the test cases table.

**Test Cases Summary**:
| Scenario | Target | Image | Expected |
|----------|--------|-------|----------|
| Passport Exact | 2x2@300 | 600x600 Sharp | Valid |
| Undersized | 2x2@300 | 450x450 Sharp | Invalid (Res) |
| Blurry | 2x2@300 | 600x600 Blurry | Invalid (Blur) |
| AR Mismatch | 2x2@300 | 600x800 Sharp | Invalid (AR) |
| Invalid File | Any | .txt | Invalid (Type) |

## Deployment

- **Docker**: Use provided `Dockerfile` and `docker-compose.yml`.
- **Heroku/AWS**: Use `Procfile`: `web: java -jar target/*.jar`.
- **Scaling**: Stateless; add Redis for caching if high traffic.
- **Monitoring**: Spring Boot Actuator + Prometheus/Grafana.

## Limitations & Troubleshooting

- **OpenCV Issues**: "UnsatisfiedLinkError"? Set `java.library.path` to natives dir. Use `nu.pattern.OpenCV.loadShared()` (as in code).
- **Large Images**: >5MB? Increase limits; watch memory (BufferedImage loads full raster).
- **Formats**: JPEG/PNG primary; add TIFF via ImageIO plugins.
- **Blur Accuracy**: Global metric—fine for docs, but test per use case. No ROI support yet.
- **No Upscaling**: API validates only; client-side resize via Canvas if needed.
- **Logs**: Enable DEBUG on `org.opencv` for blur traces.

**Common Errors**:
- "Invalid format": Corrupt file—retry upload.
- High variance on noisy images: Raise threshold.

## Contributing

Fork, PR with tests. Issues? Open on GitHub.

## License

MIT License. See [LICENSE](LICENSE) for details.

---

*Built with ❤️ for robust document uploads. Questions? [Contact](mailto:dev@example.com).*