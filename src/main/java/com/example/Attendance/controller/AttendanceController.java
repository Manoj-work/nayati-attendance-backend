package com.example.Attendance.controller;

import com.example.Attendance.dto.BulkAttendanceRequest;
import com.example.Attendance.model.DailyAttendance;
import com.example.Attendance.model.EmployeeAttendanceSummary;
import com.example.Attendance.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/checkin")
    public ResponseEntity<String> checkinUser(@RequestParam String employeeId,
                                              @RequestParam MultipartFile file,
                                              @RequestParam(required = false) LocalDateTime checkinTime) throws IOException {
        String result = attendanceService.handleFaceCheckin(employeeId, file, checkinTime);
        return ResponseEntity.ok(result);
    }



    @PostMapping("/checkout")
    public ResponseEntity<String> checkOut(@RequestParam String employeeId) {
        return ResponseEntity.ok(attendanceService.checkOut(employeeId));
    }

    @GetMapping("/daily/{employeeId}/{date}")
    public ResponseEntity<DailyAttendance> getDailyData(
            @PathVariable String employeeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(attendanceService.getDailyData(employeeId, date));
    }

    @GetMapping("/monthly/{employeeId}/{year}/{month}")
    public ResponseEntity<Map<String, Object>> getMonthlySummary(
            @PathVariable String employeeId,
            @PathVariable String year,
            @PathVariable String month) {
        return ResponseEntity.ok(attendanceService.getMonthlySummary(employeeId, year, month));
    }


    /*
        {
            "employeeId": "emp002",
            "status": "Leave",
            "dates": [
            "2025-04-05",
            "2025-04-06",
            "2025-04-07"
            ]
        }

     */

    @PostMapping("/mark-bulk")
    public ResponseEntity<String> markBulkAttendance(@RequestBody BulkAttendanceRequest request) {
        attendanceService.markBulkAttendance(request.getEmployeeId(), request.getStatus(), request.getDates());
        return ResponseEntity.ok("Attendance updated for selected dates.");
    }


    @GetMapping("/mark-weekends")
    public ResponseEntity<String> manuallyMarkWeekends() {
        attendanceService.markAllEmployeesWeekendsForCurrentMonth();
        return ResponseEntity.ok("Weekends marked for current month!");
    }

    @GetMapping("/mark-weekends/{employeeId}/{year}/{month}")
    public ResponseEntity<String> manuallyMarkWeekends(
            @PathVariable String employeeId,
            @PathVariable int  year,
            @PathVariable int month) {
        attendanceService.markWeekendsForEmployee(employeeId, year, month);
        return ResponseEntity.ok("Weekends marked for current month!");
    }



}

