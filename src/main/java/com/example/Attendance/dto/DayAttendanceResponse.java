package com.example.Attendance.dto;

import com.example.Attendance.model.DailyAttendance;
import com.example.Attendance.model.EmployeeAttendanceSummary.DayAttendanceMeta;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class DayAttendanceResponse {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Kolkata")
    private DailyAttendance dailyAttendance;
    private DayAttendanceMeta summaryAttendance;
} 