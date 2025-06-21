package com.example.Attendance.service;

import com.example.Attendance.dto.MonthlyAttendanceSummaryDTO;
import com.example.Attendance.model.DailyAttendance;
import com.example.Attendance.model.Employee;
import com.example.Attendance.model.LeaveModel;
import com.example.Attendance.repository.DailyAttendanceRepository;
import com.example.Attendance.repository.EmployeeRepository;
import com.example.Attendance.repository.LeaveRepository;
import com.example.Attendance.util.EpochUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceSummaryService {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kolkata");

    @Autowired
    private DailyAttendanceRepository dailyAttendanceRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private LeaveRepository leaveRepository;

    public MonthlyAttendanceSummaryDTO getMonthlySummary(String employeeId, int year, int month) {
        // 0. Check if employee exists
        Optional<Employee> empOpt = employeeRepository.findByEmployeeId(employeeId);
        if (empOpt.isEmpty()) {
            throw new com.example.Attendance.exception.CustomException("Employee not found", HttpStatus.NOT_FOUND);
        }

        Employee employee = empOpt.get();
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate today = LocalDate.now();
        LocalDate end = LocalDate.of(year, month, start.lengthOfMonth());
        if (today.isBefore(end)) {
            end = today;
        }

        // 1. Get present dates
        List<LocalDate> presentDates = getPresentDates(employeeId, start, end);

        // 2. Get leave and comp-off dates
        LeaveDatesSummary leaveSummary = getLeaveDates(employeeId, start, end, year, month);

        // 3. Weekly offs
        List<String> weeklyOffs = employee.getWeeklyOffs();
        List<LocalDate> weeklyOffDates = calculateWeeklyOffDates(weeklyOffs, start, end);

        // 4. All days in range
        List<LocalDate> allDates = getAllDatesInRange(start, end);

        // 5. Absent dates = all - present - leave - weekly offs
        Set<LocalDate> excluded = new HashSet<>();
        excluded.addAll(presentDates);
        excluded.addAll(leaveSummary.fullLeaveDates);
        excluded.addAll(leaveSummary.halfDayLeaveDates);
        excluded.addAll(leaveSummary.fullCompoffDates);
        excluded.addAll(leaveSummary.halfCompoffDates);
        excluded.addAll(weeklyOffDates);

        List<LocalDate> absentDates = allDates.stream()
                .filter(d -> !excluded.contains(d))
                .collect(Collectors.toList());

        // 6. Build and return DTO
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

    private List<LocalDate> getPresentDates(String employeeId, LocalDate start, LocalDate end) {
        long startEpoch = EpochUtil.toEpochSeconds(start, ZONE_ID);
        long endEpoch = EpochUtil.toEpochSeconds(end.plusDays(1), ZONE_ID) - 1;

        List<DailyAttendance> records = dailyAttendanceRepository
                .findByEmployeeIdAndDateEpochBetween(employeeId, startEpoch, endEpoch);

        return records.stream()
                .filter(d -> d.getLogs() != null && !d.getLogs().isEmpty())
                .map(d -> EpochUtil.fromEpochSecondsToDate(d.getDateEpoch(), ZONE_ID))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private LeaveDatesSummary getLeaveDates(String employeeId, LocalDate start, LocalDate end, int year, int month) {
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

    private List<LocalDate> calculateWeeklyOffDates(List<String> weeklyOffs, LocalDate start, LocalDate end) {
        List<LocalDate> result = new ArrayList<>();
        Set<String> upperWeeklyOffs = weeklyOffs.stream().map(String::toUpperCase).collect(Collectors.toSet());
        LocalDate date = start;
        while (!date.isAfter(end)) {
            if (upperWeeklyOffs.contains(date.getDayOfWeek().name())) {
                result.add(date);
            }
            date = date.plusDays(1);
        }
        return result;
    }

    private List<LocalDate> getAllDatesInRange(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate date = start;
        while (!date.isAfter(end)) {
            dates.add(date);
            date = date.plusDays(1);
        }
        return dates;
    }

    private static class LeaveDatesSummary {
        List<LocalDate> fullLeaveDates = new ArrayList<>();
        List<LocalDate> halfDayLeaveDates = new ArrayList<>();
        List<LocalDate> fullCompoffDates = new ArrayList<>();
        List<LocalDate> halfCompoffDates = new ArrayList<>();
    }
}
