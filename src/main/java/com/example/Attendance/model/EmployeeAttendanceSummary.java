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

    // Renamed from attendanceMap → years
    private Map<String, YearAttendance> years; // key: year (e.g. "2025")

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class YearAttendance {
        // Removed yearMeta
        private Map<String, MonthAttendance> months; // key: month (e.g. "4" for April)
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthAttendance {
        // Flattened monthMeta → direct fields
        private int totalDays;
        private int presentDays;
        private int approvedLeaveDays;
        private int approvedLopDays;
        private int unapprovedAbsenceDays;

        // Renamed dayMap → days
        private Map<String, DayAttendanceMeta> days; // key: date (e.g. "10")
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayAttendanceMeta {
        private String status; // Present, Absent, Leave, LOP, etc.
    }
}
