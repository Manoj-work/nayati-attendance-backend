package com.example.Attendance.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
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
    @JsonIgnore
    private String id; // e.g. "summary_emp002"
    private String employeeId;

    private Map<String, YearAttendance> years; // key: year (e.g. "2025")

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @AllArgsConstructor
    public static class YearAttendance {
        private Map<String, MonthAttendance> months; // key: month (e.g. "4" for April)
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @AllArgsConstructor
    public static class MonthAttendance {
        private Map<String, DayAttendanceMeta> days; // key: date (e.g. "10")
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DayAttendanceMeta {
        private String status; // Present, Absent, Leave, LOP, Weekly Off
        private String leaveId; // ID of the leave if status is "Leave"

        public DayAttendanceMeta(String status) {
            this.status=status;
        }
    }
}
