package com.example.Attendance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class EmployeeDetailsDTO {
    private String name;
    private String employeeImgUrl;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate joiningDate;
    private List<String> weeklyOffs;
} 