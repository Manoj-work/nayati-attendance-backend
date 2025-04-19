package com.example.Attendance.service;


import com.example.Attendance.exception.CustomException;
import com.example.Attendance.model.CheckInOut;
import com.example.Attendance.model.DailyAttendance;
import com.example.Attendance.model.EmployeeAttendanceSummary;
import com.example.Attendance.repository.DailyAttendanceRepository;
import com.example.Attendance.repository.EmployeeAttendanceSummaryRepository;
import com.example.Attendance.user.User;
import com.example.Attendance.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final DailyAttendanceRepository dailyRepo;
    private final EmployeeAttendanceSummaryRepository summaryRepo;
    private final UserRepository userRepository;
    private final FaceVerificationService faceVerificationService;
    private final MinIOService minIOService;

    public String handleFaceCheckin(String employeeId, MultipartFile file, LocalDateTime checkinTime) throws IOException {
        // 1. Find user
        User user = userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        // Use current time if not provided
        if (checkinTime == null) {
            checkinTime = LocalDateTime.now();
        }

        // Check if user has already checked in and not checked out
        LocalDateTime today = checkinTime.withHour(0).withMinute(0).withSecond(0).withNano(0);
        Optional<DailyAttendance> existingAttendance = dailyRepo.findByEmployeeIdAndDate(employeeId, today);
        
        if (existingAttendance.isPresent()) {
            List<CheckInOut> logs = existingAttendance.get().getLogs();
            if (!logs.isEmpty()) {
                CheckInOut lastLog = logs.get(logs.size() - 1);
                if (lastLog.getType().equals("checkin")) {
                    throw new CustomException("Please check out before checking in again", HttpStatus.BAD_REQUEST);
                }
            }
        }

        String storedImageUrl = user.getPhotoUrl();
        String name = user.getName();

        // 2. Reuse file bytes for MinIO & Python
        byte[] fileBytes = file.getBytes();

        // 3. Call Python service to verify
        String verificationResult = faceVerificationService.verifyFace(employeeId, file, storedImageUrl);

        if (!"Present".equalsIgnoreCase(verificationResult)) {
            return "Absent";
        }

        // 4. Rebuild MultipartFile for MinIO
        MultipartFile newFile = buildMultipartFileFromBytes(file, fileBytes);

        // 5. Upload check-in image
        String checkinImgUrl = minIOService.getCheckinImgUrl(employeeId, newFile);

        // 7. Record daily attendance
        recordDailyAttendance(employeeId, name, checkinImgUrl, checkinTime);

        return "Present";
    }


    public String checkOut(String employeeId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime today = now.withHour(0).withMinute(0).withSecond(0).withNano(0);

        DailyAttendance daily = dailyRepo.findByEmployeeIdAndDate(employeeId, today)
                .orElseThrow(() -> new CustomException("No check-in found for today", HttpStatus.NOT_FOUND));

        List<CheckInOut> logs = daily.getLogs();
        if (logs.isEmpty() || !logs.get(logs.size() - 1).getType().equals("checkin")) {
            throw new CustomException("No check-in found for today", HttpStatus.BAD_REQUEST);
        }

        logs.add(new CheckInOut("checkout", now, null));
        dailyRepo.save(daily);
        return "Check-out recorded!";
    }

    private void updateSummaryOnCheckIn(String employeeId, LocalDate date) {
        String year = String.valueOf(date.getYear());
        String month = String.valueOf(date.getMonthValue());
        String day = String.valueOf(date.getDayOfMonth());

        EmployeeAttendanceSummary summary = summaryRepo.findByEmployeeId(employeeId)
                .orElse(new EmployeeAttendanceSummary("summary_" + employeeId, employeeId, new HashMap<>()));

        // Handle years → months → days
        Map<String, EmployeeAttendanceSummary.YearAttendance> years = summary.getYears();
        EmployeeAttendanceSummary.YearAttendance yearData = years.computeIfAbsent(year, y -> new EmployeeAttendanceSummary.YearAttendance(new HashMap<>()));

        Map<String, EmployeeAttendanceSummary.MonthAttendance> months = yearData.getMonths();
        EmployeeAttendanceSummary.MonthAttendance monthData = months.computeIfAbsent(month, m -> new EmployeeAttendanceSummary.MonthAttendance(new HashMap<>()));

        Map<String, EmployeeAttendanceSummary.DayAttendanceMeta> days = monthData.getDays();
        days.put(day, new EmployeeAttendanceSummary.DayAttendanceMeta("Present"));

        summaryRepo.save(summary);
    }


    public DailyAttendance getDailyData(String employeeId, LocalDate date) {
        LocalDateTime dateTime = date.atStartOfDay();
        return dailyRepo.findByEmployeeIdAndDate(employeeId, dateTime)
                .orElseThrow(() -> new CustomException("No attendance found for this date", HttpStatus.NOT_FOUND));
    }

    public Map<String, Object> getMonthlySummary(String employeeId, String year, String month) {
        EmployeeAttendanceSummary summary = summaryRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new CustomException("No summary found", HttpStatus.NOT_FOUND));

        EmployeeAttendanceSummary.YearAttendance yearData = summary.getYears()
                .getOrDefault(year, new EmployeeAttendanceSummary.YearAttendance());
                
        EmployeeAttendanceSummary.MonthAttendance monthData = yearData.getMonths()
                .getOrDefault(month, new EmployeeAttendanceSummary.MonthAttendance());

        Map<String, EmployeeAttendanceSummary.DayAttendanceMeta> days = monthData.getDays();
        
        // Calculate summary from daily records
        int presentDays = 0;
        int approvedLeaveDays = 0;
        int approvedLopDays = 0;
        int unapprovedAbsenceDays = 0;
        int weeklyOffDays = 0;

        for (EmployeeAttendanceSummary.DayAttendanceMeta dayMeta : days.values()) {
            String status = dayMeta.getStatus();
            switch (status) {
                case "Present" -> presentDays++;
                case "Leave" -> approvedLeaveDays++;
                case "LOP" -> approvedLopDays++;
                case "Absent" -> unapprovedAbsenceDays++;
                case "Weekly Off" -> weeklyOffDays++;
            }
        }

        Map<String, Object> summaryMap = new LinkedHashMap<>();

        summaryMap.put("presentDays", presentDays);
        summaryMap.put("approvedLeaveDays", approvedLeaveDays);
        summaryMap.put("approvedLopDays", approvedLopDays);
        summaryMap.put("unapprovedAbsenceDays", unapprovedAbsenceDays);
        summaryMap.put("weeklyOffDays", weeklyOffDays);
        summaryMap.put("days", days);

        return Map.of("summary", summaryMap);
    }

    public void markBulkAttendance(String employeeId, String status, List<String> dateStrings) {
        EmployeeAttendanceSummary summary = summaryRepo.findByEmployeeId(employeeId)
                .orElse(new EmployeeAttendanceSummary("summary_" + employeeId, employeeId, new HashMap<>()));

        for (String dateStr : dateStrings) {
            LocalDate date = LocalDate.parse(dateStr);
            String year = String.valueOf(date.getYear());
            String month = String.valueOf(date.getMonthValue());
            String day = String.valueOf(date.getDayOfMonth());

            Map<String, EmployeeAttendanceSummary.YearAttendance> years = summary.getYears();
            EmployeeAttendanceSummary.YearAttendance yearData = years.computeIfAbsent(year, y -> new EmployeeAttendanceSummary.YearAttendance(new HashMap<>()));

            Map<String, EmployeeAttendanceSummary.MonthAttendance> months = yearData.getMonths();
            EmployeeAttendanceSummary.MonthAttendance monthData = months.computeIfAbsent(month, m -> new EmployeeAttendanceSummary.MonthAttendance(new HashMap<>()));

            Map<String, EmployeeAttendanceSummary.DayAttendanceMeta> days = monthData.getDays();
            days.put(day, new EmployeeAttendanceSummary.DayAttendanceMeta(status));
        }

        summaryRepo.save(summary);
    }


    // mark previous days as absent
    public void markPastDaysAbsentIfFirstCheckin(String employeeId) {
        User user = userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        LocalDate joiningDate = user.getJoiningDate();
        LocalDate today = LocalDate.now();
        List<String> weeklyOffs = user.getWeeklyOffs();

        boolean hasPreviousAttendance = dailyRepo.existsByEmployeeId(employeeId);
        if (hasPreviousAttendance) return;

        EmployeeAttendanceSummary summary = summaryRepo.findByEmployeeId(employeeId)
                .orElse(new EmployeeAttendanceSummary("summary_" + employeeId, employeeId, new HashMap<>()));

        for (LocalDate date = joiningDate; date.isBefore(today); date = date.plusDays(1)) {
            String dayName = date.getDayOfWeek().toString();
            String year = String.valueOf(date.getYear());
            String month = String.valueOf(date.getMonthValue());
            String day = String.valueOf(date.getDayOfMonth());

            Map<String, EmployeeAttendanceSummary.YearAttendance> years = summary.getYears();
            EmployeeAttendanceSummary.YearAttendance yearData = years.computeIfAbsent(year, y -> new EmployeeAttendanceSummary.YearAttendance(new HashMap<>()));

            Map<String, EmployeeAttendanceSummary.MonthAttendance> months = yearData.getMonths();
            EmployeeAttendanceSummary.MonthAttendance monthData = months.computeIfAbsent(month, m -> new EmployeeAttendanceSummary.MonthAttendance(new HashMap<>()));

            Map<String, EmployeeAttendanceSummary.DayAttendanceMeta> days = monthData.getDays();

            if (weeklyOffs.contains(dayName)) {
                days.put(day, new EmployeeAttendanceSummary.DayAttendanceMeta("Weekly Off"));
            } else {
                days.put(day, new EmployeeAttendanceSummary.DayAttendanceMeta("Absent"));
            }
        }

        summaryRepo.save(summary);
    }



    // Mark weekends for the user for a month
    public void markWeekendsForEmployee(String employeeId, int year, int month) {
        User user = userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        List<String> weeklyOffs = user.getWeeklyOffs();
        EmployeeAttendanceSummary summary = summaryRepo.findByEmployeeId(employeeId)
                .orElse(new EmployeeAttendanceSummary("summary_" + employeeId, employeeId, new HashMap<>()));

        YearMonth yearMonth = YearMonth.of(year, month);
        int daysInMonth = yearMonth.lengthOfMonth();

        String yearStr = String.valueOf(year);
        String monthStr = String.valueOf(month);

        EmployeeAttendanceSummary.YearAttendance yearAttendance =
                summary.getYears().computeIfAbsent(yearStr, y -> new EmployeeAttendanceSummary.YearAttendance(new HashMap<>()));

        EmployeeAttendanceSummary.MonthAttendance monthAttendance =
                yearAttendance.getMonths().computeIfAbsent(monthStr, m -> new EmployeeAttendanceSummary.MonthAttendance(new HashMap<>()));

        Map<String, EmployeeAttendanceSummary.DayAttendanceMeta> daysMap = monthAttendance.getDays();

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = LocalDate.of(year, month, day);
            String dayOfWeek = date.getDayOfWeek().toString();

            if (weeklyOffs.contains(dayOfWeek)) {
                String dayKey = String.valueOf(day);
                daysMap.putIfAbsent(dayKey, new EmployeeAttendanceSummary.DayAttendanceMeta("Weekly Off"));
            }
        }

        summaryRepo.save(summary);
    }

    public void markAllEmployeesWeekendsForCurrentMonth() {
        List<User> allUsers = userRepository.findAll();

        YearMonth yearMonth = YearMonth.now();
        int year = yearMonth.getYear();
        int month = yearMonth.getMonthValue();

        for (User user : allUsers) {
            markWeekendsForEmployee(user.getEmployeeId(), year, month);
        }
    }



    @Scheduled(cron = "0 0 0 1 * *") // Every 1st of the month at midnight
    public void autoMarkWeekends() {
        System.out.println("Running scheduled weekend marker...");
        markAllEmployeesWeekendsForCurrentMonth();
    }









    // Helper Methods
    private MultipartFile buildMultipartFileFromBytes(MultipartFile original, byte[] bytes) {
        return new MultipartFile() {
            @Override public String getName() { return original.getName(); }
            @Override public String getOriginalFilename() { return original.getOriginalFilename(); }
            @Override public String getContentType() { return original.getContentType(); }
            @Override public boolean isEmpty() { return bytes.length == 0; }
            @Override public long getSize() { return bytes.length; }
            @Override public byte[] getBytes() { return bytes; }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
            @Override public void transferTo(java.io.File dest) { throw new UnsupportedOperationException(); }
        };
    }

    private void recordDailyAttendance(String employeeId, String name, String checkinImgUrl, LocalDateTime checkinTime) {
        LocalDateTime today = checkinTime.withHour(0).withMinute(0).withSecond(0).withNano(0);

        // ✅ Mark backdated absents on first-time check-in
        markPastDaysAbsentIfFirstCheckin(employeeId);

        DailyAttendance daily = dailyRepo.findByEmployeeIdAndDate(employeeId, today)
                .orElse(new DailyAttendance(null, employeeId, today, new ArrayList<>()));

        daily.getLogs().add(new CheckInOut("checkin", checkinTime, checkinImgUrl));
        dailyRepo.save(daily);

        updateSummaryOnCheckIn(employeeId, today.toLocalDate());
    }

}





