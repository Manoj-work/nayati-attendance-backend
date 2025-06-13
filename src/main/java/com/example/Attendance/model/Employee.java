package com.example.Attendance.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Document(collection = "employees")
public class Employee {
    private String employeeId;
    private String name;
    private String employeeImgUrl;
    private LocalDate joiningDate;
    private List<String> weeklyOffs;
    private List<String> assignTo;
}
