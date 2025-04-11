package com.example.Attendance.dto;

import lombok.Data;
import java.util.List;

@Data
public class BulkAttendanceRequest {
    private String employeeId;
    private String status; // Leave, LOP, Absent
    private List<String> dates; // yyyy-MM-dd
}
