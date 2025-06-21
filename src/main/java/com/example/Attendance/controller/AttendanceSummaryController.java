package com.example.Attendance.controller;

import com.example.Attendance.service.AttendanceSummaryService;
import com.example.Attendance.dto.MonthlyAttendanceSummaryDTO;
import com.example.Attendance.model.Employee;
import com.example.Attendance.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/attendance-summary")
public class AttendanceSummaryController {
    @Autowired
    private AttendanceSummaryService attendanceSummaryService;
    @Autowired
    private EmployeeRepository employeeRepository;

    @GetMapping("/{employeeId}/{year}/{month}")
    public ResponseEntity<MonthlyAttendanceSummaryDTO> getMonthlyAttendanceSummary(
            @PathVariable String employeeId,
            @PathVariable int year,
            @PathVariable int month) {
        MonthlyAttendanceSummaryDTO summary = attendanceSummaryService.getMonthlySummary(employeeId, year, month);
        return ResponseEntity.ok(summary);
    }

    // 1. Manager endpoint
    @GetMapping("/manager/{managerId}/{year}/{month}")
    public ResponseEntity<Map<String, Object>> getManagerTeamMonthlyAttendance(
            @PathVariable String managerId,
            @PathVariable int year,
            @PathVariable int month) {
        Optional<Employee> managerOpt = employeeRepository.findByEmployeeId(managerId);
        if (managerOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Manager not found"));
        }
        Employee manager = managerOpt.get();
        List<String> teamIds = manager.getAssignTo();
        if (teamIds == null || teamIds.isEmpty()) {
            return ResponseEntity.ok(Map.of("managerId", managerId, "teamAttendance", Collections.emptyList()));
        }
        List<Map<String, Object>> teamAttendance = teamIds.stream().map(empId -> {
            try {
                MonthlyAttendanceSummaryDTO summary = attendanceSummaryService.getMonthlySummary(empId, year, month);
                return Map.of("employeeId", empId, "attendance", summary);
            } catch (Exception e) {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("employeeId", empId);
                errorMap.put("error", e.getMessage());
                return errorMap;
            }
        }).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("managerId", managerId, "teamAttendance", teamAttendance));
    }

    // 2. HR endpoint
    @GetMapping("/hr/{companyId}/{year}/{month}")
    public ResponseEntity<Map<String, Object>> getCompanyMonthlyAttendance(
            @PathVariable String companyId,
            @PathVariable int year,
            @PathVariable int month) {
        List<Employee> employees = employeeRepository.findByCompanyId(companyId);
        if (employees == null || employees.isEmpty()) {
            return ResponseEntity.ok(Map.of("companyId", companyId, "hrAttendance", Collections.emptyList()));
        }
        List<Map<String, Object>> attendanceList = employees.stream().map(emp -> {
            try {
                MonthlyAttendanceSummaryDTO summary = attendanceSummaryService.getMonthlySummary(emp.getEmployeeId(), year, month);
                return Map.of("employeeId", emp.getEmployeeId(), "attendance", summary);
            } catch (Exception e) {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("employeeId", emp.getEmployeeId());
                errorMap.put("error", e.getMessage());
                return errorMap;
            }
        }).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("companyId", companyId, "hrAttendance", attendanceList));
    }
} 