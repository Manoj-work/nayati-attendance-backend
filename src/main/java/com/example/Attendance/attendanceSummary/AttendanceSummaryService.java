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

        // 1. Get present dates from daily attendance
        List<LocalDate> presentDates = getPresentDates(employeeId, year, month);

        // 2. Get leave and comp-off dates
        LeaveDatesSummary leaveSummary = getLeaveDates(employeeId, year, month);

        // 3. Get weekly off dates from employee profile
        List<String> weeklyOffs = employee.getWeeklyOffs();
        List<LocalDate> weeklyOffDates = calculateWeeklyOffDates(weeklyOffs, year, month);

        // 4. Calculate all dates in month
        List<LocalDate> allDates = getAllDatesInMonth(year, month);

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

    private List<LocalDate> getPresentDates(String employeeId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        List<DailyAttendance> records = dailyAttendanceRepository.findByEmployeeIdAndDateBetween(employeeId, start.atStartOfDay(), end.atTime(23,59,59));
        return records.stream()
                .filter(d -> d.getLogs() != null && !d.getLogs().isEmpty())
                .map(d -> d.getDate().toLocalDate())
                .collect(Collectors.toList());
    }

    private LeaveDatesSummary getLeaveDates(String employeeId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
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

    private List<LocalDate> calculateWeeklyOffDates(List<String> weeklyOffs, int year, int month) {
        List<LocalDate> result = new ArrayList<>();
        YearMonth ym = YearMonth.of(year, month);
        // Convert weeklyOffs to uppercase for consistent comparison
        Set<String> upperWeeklyOffs = weeklyOffs.stream().map(String::toUpperCase).collect(Collectors.toSet());
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate date = ym.atDay(day);
            DayOfWeek dow = date.getDayOfWeek();
            if (upperWeeklyOffs.contains(dow.name())) {
                result.add(date);
            }
        }
        return result;
    }

    private List<LocalDate> getAllDatesInMonth(int year, int month) {
        List<LocalDate> dates = new ArrayList<>();
        YearMonth ym = YearMonth.of(year, month);
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            dates.add(ym.atDay(day));
        }
        return dates;
    }
} 