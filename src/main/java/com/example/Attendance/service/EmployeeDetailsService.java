package com.example.Attendance.service;

import com.example.Attendance.dto.EmployeeDetailsDTO;
import com.example.Attendance.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class EmployeeDetailsService {
    private RestTemplate restTemplate = new RestTemplate();
    private static final String EMPLOYEE_SERVICE_BASE_URL = "http://192.168.0.200:8080/employee";

    public EmployeeDetailsDTO getEmployeeDetails(String employeeId) {
        try {
            String url = String.format("%s/%s/attendance-details", EMPLOYEE_SERVICE_BASE_URL, employeeId);
            return restTemplate.getForObject(url, EmployeeDetailsDTO.class);
        } catch (Exception e) {
            throw new CustomException("Failed to fetch employee details: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
} 