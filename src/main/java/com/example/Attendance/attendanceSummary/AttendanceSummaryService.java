package com.example.Attendance.attendanceSummary;

import com.example.Attendance.model.DailyAttendance;
import com.example.Attendance.model.Employee;
import com.example.Attendance.repository.DailyAttendanceRepository;
import com.example.Attendance.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceSummaryService {
    @Autowired
    private DailyAttendanceRepository dailyAttendanceRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private LeaveRepository leaveRepository;

    public MonthlyAttendanceSummaryDTO getMonthlySummary(String employeeId, int year, int month) {
        // 0. Check if employee exists before any calculation
        Optional<Employee> empOpt = employeeRepository.findByEmployeeId(employeeId);
        if (empOpt.isEmpty()) {
            throw new com.example.Attendance.exception.CustomException("Employee not found", HttpStatus.NOT_FOUND);
        }
        Employee employee = empOpt.get();

        // Start date is always the first of the month
        java.time.YearMonth ym = java.time.YearMonth.of(year, month);
        java.time.LocalDate start = ym.atDay(1);
        // End date is the lesser of today or the last day of the month
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate end = ym.atEndOfMonth();
        if (today.isBefore(end)) {
            end = today;
        }

        // 1. Get present dates from daily attendance
        List<LocalDate> presentDates = getPresentDates(employeeId, start, end);

        // 2. Get leave and comp-off dates
        LeaveDatesSummary leaveSummary = getLeaveDates(employeeId, start, end, year, month);

        // 3. Get weekly off dates from employee profile
        List<String> weeklyOffs = employee.getWeeklyOffs();
        List<LocalDate> weeklyOffDates = calculateWeeklyOffDates(weeklyOffs, start, end);

        // 4. Calculate all dates in month up to today or end of month
        List<LocalDate> allDates = getAllDatesInRange(start, end);

        // 5. Calculate absent dates
        Set<LocalDate> excluded = new HashSet<>();
        excluded.addAll(presentDates);
        excluded.addAll(leaveSummary.fullLeaveDates);
        excluded.addAll(leaveSummary.halfDayLeaveDates);
        excluded.addAll(leaveSummary.fullCompoffDates);
        excluded.addAll(leaveSummary.halfCompoffDates);
        excluded.addAll(weeklyOffDates);

        List<LocalDate> absentDates = allDates.stream()
                .filter(date -> !excluded.contains(date))
                .collect(Collectors.toList());

        // 6. Build summary object
        MonthlyAttendanceSummaryDTO dto = new MonthlyAttendanceSummaryDTO();
        dto.setPresentDates(presentDates);
        dto.setFullLeaveDates(leaveSummary.fullLeaveDates);
        dto.setHalfDayLeaveDates(leaveSummary.halfDayLeaveDates);
        dto.setFullCompoffDates(leaveSummary.fullCompoffDates);
        dto.setHalfCompoffDates(leaveSummary.halfCompoffDates);
        dto.setWeeklyOffDates(weeklyOffDates);
        dto.setAbsentDates(absentDates);
        return dto;
    }

    // Helper DTO for leave summary
    private static class LeaveDatesSummary {
        List<LocalDate> fullLeaveDates = new ArrayList<>();
        List<LocalDate> halfDayLeaveDates = new ArrayList<>();
        List<LocalDate> fullCompoffDates = new ArrayList<>();
        List<LocalDate> halfCompoffDates = new ArrayList<>();
    }

    private List<LocalDate> getPresentDates(String employeeId, java.time.LocalDate start, java.time.LocalDate end) {
        List<DailyAttendance> records = dailyAttendanceRepository.findByEmployeeIdAndDateBetween(employeeId, start.atStartOfDay(), end.atTime(23,59,59));
        return records.stream()
                .filter(d -> d.getLogs() != null && !d.getLogs().isEmpty())
                .map(d -> d.getDate().toLocalDate())
                .collect(Collectors.toList());
    }

    private LeaveDatesSummary getLeaveDates(String employeeId, java.time.LocalDate start, java.time.LocalDate end, int year, int month) {
        List<LeaveModel> leaves = leaveRepository.findByEmployeeIdAndStatusAndLeaveDatesBetween(employeeId, "Approved", start, end);
        LeaveDatesSummary summary = new LeaveDatesSummary();
        for (LeaveModel leave : leaves) {
            for (LocalDate date : leave.getLeaveDates()) {
                if (date.getYear() == year && date.getMonthValue() == month) {
                    if ("Leave".equalsIgnoreCase(leave.getLeaveName())) {
                        if ("FULL_DAY".equalsIgnoreCase(leave.getShiftType())) summary.fullLeaveDates.add(date);
                        else summary.halfDayLeaveDates.add(date);
                    } else if ("Comp-Off".equalsIgnoreCase(leave.getLeaveName()) || "Comp Off".equalsIgnoreCase(leave.getLeaveName())) {
                        if ("FULL_DAY".equalsIgnoreCase(leave.getShiftType())) summary.fullCompoffDates.add(date);
                        else summary.halfCompoffDates.add(date);
                    }
                }
            }
        }
        return summary;
    }

    private List<LocalDate> calculateWeeklyOffDates(List<String> weeklyOffs, java.time.LocalDate start, java.time.LocalDate end) {
        List<LocalDate> result = new ArrayList<>();
        java.time.YearMonth ym = java.time.YearMonth.of(start.getYear(), start.getMonth());
        // Convert weeklyOffs to uppercase for consistent comparison
        Set<String> upperWeeklyOffs = weeklyOffs.stream().map(String::toUpperCase).collect(Collectors.toSet());
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate date = ym.atDay(day);
            DayOfWeek dow = date.getDayOfWeek();
            if (upperWeeklyOffs.contains(dow.name()) && date.isAfter(start) && date.isBefore(end)) {
                result.add(date);
            }
        }
        return result;
    }

    private List<LocalDate> getAllDatesInRange(java.time.LocalDate start, java.time.LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        java.time.YearMonth ym = java.time.YearMonth.of(start.getYear(), start.getMonth());
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate date = ym.atDay(day);
            if (date.isAfter(start) && date.isBefore(end)) {
                dates.add(date);
            }
        }
        return dates;
    }
} 