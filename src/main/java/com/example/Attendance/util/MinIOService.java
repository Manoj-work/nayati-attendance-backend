package com.example.Attendance.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MinIOService {

    @Value("${minio.serviceUrl}")
    private String minioServiceUrl;

    @Value("${minio.attendanceBucketName}")
    private String attendanceBucketName;

    @Value("${minio.checkinBucketName}")
    private String checkinBucketName;



    private final RestTemplate restTemplate = new RestTemplate();

    public String generateUUID() {
        return restTemplate.getForObject(minioServiceUrl + "/generate-uuid", String.class);
    }

    public String uploadFile(String bucketName, String employeeId, MultipartFile file) {
        String uniqueId = generateUUID(); // 🔥 Fetch UUID from MinIO Service
        String filePath = employeeId + "/" + uniqueId + "_" + file.getOriginalFilename(); // 🔥 Construct path

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("bucketName", bucketName);
        body.add("filePath", filePath);
        body.add("file", file.getResource());

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(minioServiceUrl + "/upload", requestEntity, String.class);

        return response.getBody(); // MinIO file URL
    }

    public String getPhotoUrl(String employeeId, MultipartFile file) {
        return uploadFile(attendanceBucketName, employeeId, file);
    }

    public String getCheckinImgUrl(String employeeId, MultipartFile file) {
        return uploadFile(checkinBucketName, employeeId, file);
    }

}
