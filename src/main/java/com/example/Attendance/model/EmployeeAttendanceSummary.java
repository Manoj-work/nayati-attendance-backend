package com.example.Attendance.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Document(collection = "employee_attendance_summary")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeAttendanceSummary {

    @Id
    private String id; // e.g. "summary_emp002"
    private String employeeId;

    private Map<String, YearAttendance> years; // key: year (e.g. "2025")

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class YearAttendance {
        private Map<String, MonthAttendance> months; // key: month (e.g. "4" for April)
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthAttendance {
        private Map<String, DayAttendanceMeta> days; // key: date (e.g. "10")
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayAttendanceMeta {
        private String status; // Present, Absent, Leave, LOP, Weekly Off
    }
}
