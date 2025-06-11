package com.example.Attendance.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "Registered-Users")
public class RegisteredUser {
    @Id
    private String id;
    private String empId;
    private String name;
    private String imgUrl;
    private String registeredAt;
} 