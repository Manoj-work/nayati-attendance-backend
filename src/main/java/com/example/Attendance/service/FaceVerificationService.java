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
import java.util.List;
import java.util.Map;

@Service
public class FaceVerificationService {

    @Value("${PYTHON_FACE_RECOGNITION}")
    private String PYTHON_FACE_RECOGNITION;

    private final RestTemplate restTemplate = new RestTemplate();

    private File convertMultipartFileToFile(MultipartFile file) throws IOException {
        File tempFile = File.createTempFile("uploaded_", ".jpg");
        file.transferTo(tempFile);
        return tempFile;
    }

    public Map<String, Object> registerUser(MultipartFile file, String empId, String name, String imgUrl) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        File tempFile = convertMultipartFileToFile(file);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(tempFile));
        body.add("empId", empId);
        body.add("name", name);
        body.add("imgUrl", imgUrl);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    PYTHON_FACE_RECOGNITION + "register",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Error communicating with face recognition service: " + e.getMessage(), e);
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public Map<String, Object> verifyByEmpId(MultipartFile file, String empId) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Convert MultipartFile to a temporary file
        File tempFile = convertMultipartFileToFile(file);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(tempFile));
        body.add("empId", empId); // ✅ Include empId as plain text in form-data

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    PYTHON_FACE_RECOGNITION + "verify/by-empid", // ✅ Updated endpoint
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Error communicating with face recognition service: " + e.getMessage(), e);
        } finally {
            // Cleanup the temp file
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public Map<String, Object> verifyByEmpIdList(MultipartFile file, List<String> empIds) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        File tempFile = convertMultipartFileToFile(file);
        String empIdListString = String.join(",", empIds); // comma-separated list

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(tempFile));
        body.add("empIds", empIdListString);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    PYTHON_FACE_RECOGNITION + "verify/by-empid-list",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Error communicating with face recognition service: " + e.getMessage(), e);
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public Map<String, Object> verifyAll(MultipartFile file) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        File tempFile = convertMultipartFileToFile(file);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(tempFile));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    PYTHON_FACE_RECOGNITION + "verify/all",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Error communicating with face recognition service: " + e.getMessage(), e);
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

}
