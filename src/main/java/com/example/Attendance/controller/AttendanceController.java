package com.example.Attendance.controller;

import com.example.Attendance.dto.BulkAttendanceRequest;
import com.example.Attendance.dto.DayAttendanceResponse;
import com.example.Attendance.model.RegisteredUser;
import com.example.Attendance.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("employee/checkin")
    public ResponseEntity<Map<String, Object>> markAttendanceWithFace(
            @RequestParam String empId,
            @RequestParam MultipartFile file) throws IOException {
        Map<String, Object> result = attendanceService.handleSingleCheckin(file,empId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("manager/checkin")
    public ResponseEntity<Map<String, Object>> teamcheckinwithface(
            @RequestParam String managerId,
            @RequestParam MultipartFile file) throws IOException {
        Map<String, Object> result = attendanceService.handleTeamcheckin(file,managerId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("employee/manual-checkin")
    public ResponseEntity<Map<String,Object>> markManualAttendance(
            @RequestParam String empId,
            @RequestParam MultipartFile file) throws IOException{
        Map<String,Object> result = attendanceService.manualAttendanceMarking(empId,file);
        return ResponseEntity.ok(result);
    }


    @PostMapping("employee/checkout")
    public ResponseEntity<String> checkOut(@RequestParam String employeeId) {
        return ResponseEntity.ok(attendanceService.checkOut(employeeId));
    }

    @GetMapping("employee/daily/{employeeId}/{date}")
    public ResponseEntity<DayAttendanceResponse> getDailyData(
            @PathVariable String employeeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(attendanceService.getDailyData(employeeId, date));
    }

    @GetMapping("employee/monthly/{employeeId}/{year}/{month}")
    public ResponseEntity<Map<String, Object>> getMonthlySummary(
            @PathVariable String employeeId,
            @PathVariable String year,
            @PathVariable String month) {
        return ResponseEntity.ok(attendanceService.getMonthlySummary(employeeId, year, month));
    }

    @PostMapping("manager/mark-bulk")
    public ResponseEntity<String> markBulkAttendance(@RequestBody BulkAttendanceRequest request) {
        attendanceService.markBulkAttendance(request.getEmployeeId(), request.getStatus(), request.getLeaveId(), request.getDates());
        return ResponseEntity.ok("Attendance updated for selected dates.");
    }

    @GetMapping("manager/mark-weekends")
    public ResponseEntity<String> manuallyMarkWeekends() {
        attendanceService.markAllEmployeesWeekendsForCurrentMonth();
        return ResponseEntity.ok("Weekends marked for current month!");
    }

    @GetMapping("manager/mark-weekends/{employeeId}/{year}/{month}")
    public ResponseEntity<String> manuallyMarkWeekends(
            @PathVariable String employeeId,
            @PathVariable int  year,
            @PathVariable int month) {
        attendanceService.markWeekendsForEmployee(employeeId, year, month);
        return ResponseEntity.ok("Weekends marked for current month!");
    }

    @PostMapping("employee/register")
    public ResponseEntity<String> registerEmployee(
            @RequestParam String empId,
            @RequestParam String empName,
            @RequestParam MultipartFile empImage) throws IOException {
        String result = attendanceService.registerEmployee(empId, empName, empImage);
        return ResponseEntity.ok(result);
    }



    @GetMapping("manager/registered-users")
    public ResponseEntity<List<RegisteredUser>> getRegisteredUsers() {
        List<RegisteredUser> users = attendanceService.getRegisteredUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("manager/registered-users/{empId}")
    public ResponseEntity<?> getRegisteredUserByEmpId(@PathVariable String empId) {
        Optional<RegisteredUser> user = attendanceService.getRegisteredUserByEmpId(empId);

        if (user.isPresent()) {
            return ResponseEntity.ok(Map.of(
                    "status", true,
                    "message", "Employee found",
                    "data", user.get()
            ));
        } else {
           return ResponseEntity.ok(Map.of(
                   "status",false,
                   "message","Employee not found"
           ));
        }
    }

    @GetMapping("manager/team-status/{managerId}")
    public ResponseEntity<Map<String, Object>> getTeamCheckInStatus(@PathVariable String managerId) {
        return ResponseEntity.ok(attendanceService.getTeamCheckInStatus(managerId));
    }

}

