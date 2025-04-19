package com.example.Attendance.dto;

import com.example.Attendance.model.DailyAttendance;
import com.example.Attendance.model.EmployeeAttendanceSummary.DayAttendanceMeta;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class DayAttendanceResponse {
    private DailyAttendance dailyAttendance;
    private DayAttendanceMeta summaryAttendance;
} 