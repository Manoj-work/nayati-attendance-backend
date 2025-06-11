package com.example.Attendance.service;

import io.minio.*;
import io.minio.errors.MinioException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@Service
public class FaceVerificationService {
    @Value("${PYTHON_API_URL}")
    private String PYTHON_API_URL;

    @Value("${minio.url}")
    private String minioUrl;

    @Value("${minio.accessKey}")
    private String accessKey;

    @Value("${minio.secretKey}")
    private String secretkey;

    @Value("${PYTHON_FACE_RECOGNITION}")
    private String PYTHON_FACE_RECOGNITION;



    private final RestTemplate restTemplate = new RestTemplate();

    private  MinioClient minioClient;

    @PostConstruct
    public void initMinioClient() {
        this.minioClient = MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(accessKey,secretkey )
                .build();
    }

    public String verifyFace(String employeeId, MultipartFile uploadedFile, String storedImageUrl) throws IOException {

        // Extract filename from storedImageUrl
        String fileName = storedImageUrl.substring(storedImageUrl.lastIndexOf("/") + 1);

        // Remove base URL (http://192.168.0.200:9000/) to get the full MinIO object path
        String objectPath = storedImageUrl.replace("http://192.168.0.200:9000/attendance", "");


        //  Download stored image from MinIO
        File storedImageFile = downloadImageFromMinIO(objectPath);

        if (storedImageFile == null) {
            throw new RuntimeException("Failed to download stored image from MinIO for employee: " + employeeId);
        }

        // Convert uploaded image to a temporary file
        File uploadedImageFile = convertMultipartFileToFile(uploadedFile);

        // Prepare the request to send images to Python service
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image1", new FileSystemResource(uploadedImageFile));
        body.add("image2", new FileSystemResource(storedImageFile));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            // Send request to Python API
            ResponseEntity<Map> response = restTemplate.exchange(PYTHON_API_URL, HttpMethod.POST, requestEntity, Map.class);

            // 🔹 Extract verification result
            Map<String, Object> responseBody = response.getBody();
            boolean verified = (boolean) responseBody.get("verified");

            return verified ? "Present" : "Absent"; //Return only "Present" or "Absent"
        } catch (Exception e) {
            throw new RuntimeException("Error communicating with Python service: " + e.getMessage());
        } finally {
            // Clean up temp files
            uploadedImageFile.delete();
            storedImageFile.delete();
        }
    }

    private File downloadImageFromMinIO(String objectPath) {
        try {
            File tempFile = File.createTempFile("minio_", ".jpg");


            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket("attendance")
                            .object(objectPath)  // Use the full object path here
                            .build())) {
                Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            return tempFile;
        } catch (MinioException e) {
            System.err.println("MinIO Exception: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("General Error Downloading Image: " + e.getMessage());
            return null;
        }
    }

    private File convertMultipartFileToFile(MultipartFile file) throws IOException {
        File tempFile = File.createTempFile("uploaded_", ".jpg");
        file.transferTo(tempFile);
        return tempFile;
    }

    public Map<String, Object> recognizeFace(MultipartFile file) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(convertMultipartFileToFile(file)));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    PYTHON_FACE_RECOGNITION+"upload/",
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Error communicating with face recognition service: " + e.getMessage());
        }
    }
}
