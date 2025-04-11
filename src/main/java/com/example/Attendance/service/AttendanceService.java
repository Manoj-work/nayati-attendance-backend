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

        // 6. Use current time if not provided
        if (checkinTime == null) {
            checkinTime = LocalDateTime.now();
        }

        // 7. Record daily attendance
        recordDailyAttendance(employeeId, name, checkinImgUrl, checkinTime);

        return "Present";
    }


    public String checkOut(String employeeId) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        DailyAttendance daily = dailyRepo.findByEmployeeIdAndDate(employeeId, today)
                .orElseThrow(() -> new CustomException("No check-in found for today",HttpStatus.NOT_FOUND));

        List<CheckInOut> times = daily.getCheckInOutTimes();
        CheckInOut last = times.get(times.size() - 1);
        if (last.getCheckOut() != null) throw new CustomException("Already checked out",HttpStatus.BAD_REQUEST);

        last.setCheckOut(now);

        // Update working hours
        Duration duration = Duration.between(last.getCheckIn(), now);
        double hours = duration.toMinutes() / 60.0;
        daily.setTotalWorkingHours(daily.getTotalWorkingHours() + hours);

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
        EmployeeAttendanceSummary.MonthAttendance monthData = months.computeIfAbsent(month, m -> new EmployeeAttendanceSummary.MonthAttendance(0, 0, 0, 0, 0, new HashMap<>()));

        Map<String, EmployeeAttendanceSummary.DayAttendanceMeta> days = monthData.getDays();
        days.put(day, new EmployeeAttendanceSummary.DayAttendanceMeta("Present"));

        // Update month counters (optional logic – you can refine as needed)
        monthData.setTotalDays(monthData.getTotalDays() + 1);
        monthData.setPresentDays(monthData.getPresentDays() + 1);

        summaryRepo.save(summary);
    }


    public DailyAttendance getDailyData(String employeeId, LocalDate date) {
        return dailyRepo.findByEmployeeIdAndDate(employeeId, date)
                .orElseThrow(() -> new CustomException("No attendance found for this date", HttpStatus.NOT_FOUND));
    }

    public Map<String, Object> getMonthlySummary(String employeeId, String year, String month) {
        EmployeeAttendanceSummary summary = summaryRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new CustomException("No summary found", HttpStatus.NOT_FOUND));

        EmployeeAttendanceSummary.YearAttendance yearData = summary.getYears()
                .getOrDefault(year, new EmployeeAttendanceSummary.YearAttendance());
                
        EmployeeAttendanceSummary.MonthAttendance monthData = yearData.getMonths()
                .getOrDefault(month, new EmployeeAttendanceSummary.MonthAttendance());

        return Map.of("summary", monthData);
    }

    // mark status as provided
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
            EmployeeAttendanceSummary.MonthAttendance monthData = months.computeIfAbsent(month, m -> new EmployeeAttendanceSummary.MonthAttendance(0, 0, 0, 0, 0, new HashMap<>()));

            Map<String, EmployeeAttendanceSummary.DayAttendanceMeta> days = monthData.getDays();
            days.put(day, new EmployeeAttendanceSummary.DayAttendanceMeta(status));

            // Update month counters
            monthData.setTotalDays(monthData.getTotalDays() + 1);
            switch (status) {
                case "Present" -> monthData.setPresentDays(monthData.getPresentDays() + 1);
                case "Leave" -> monthData.setApprovedLeaveDays(monthData.getApprovedLeaveDays() + 1);
                case "LOP" -> monthData.setApprovedLopDays(monthData.getApprovedLopDays() + 1);
                case "Absent" -> monthData.setUnapprovedAbsenceDays(monthData.getUnapprovedAbsenceDays() + 1);
            }
        }

        summaryRepo.save(summary);
    }


    // mark previous days as absent
    public void markPastDaysAbsentIfFirstCheckin(String employeeId) {
        User user = userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        LocalDate joiningDate = user.getJoiningDate();
        LocalDate today = LocalDate.now();
        List<String> weeklyOffs = user.getWeeklyOffs(); // e.g. ["SUNDAY", "SATURDAY"]

        boolean hasPreviousAttendance = dailyRepo.existsByEmployeeId(employeeId);
        if (hasPreviousAttendance) return;

        EmployeeAttendanceSummary summary = summaryRepo.findByEmployeeId(employeeId)
                .orElse(new EmployeeAttendanceSummary("summary_" + employeeId, employeeId, new HashMap<>()));

        for (LocalDate date = joiningDate; date.isBefore(today); date = date.plusDays(1)) {
            String dayName = date.getDayOfWeek().toString(); // e.g. "MONDAY"
            String year = String.valueOf(date.getYear());
            String month = String.valueOf(date.getMonthValue());
            String day = String.valueOf(date.getDayOfMonth());

            Map<String, EmployeeAttendanceSummary.YearAttendance> years = summary.getYears();
            EmployeeAttendanceSummary.YearAttendance yearData = years.computeIfAbsent(year, y -> new EmployeeAttendanceSummary.YearAttendance(new HashMap<>()));

            Map<String, EmployeeAttendanceSummary.MonthAttendance> months = yearData.getMonths();
            EmployeeAttendanceSummary.MonthAttendance monthData = months.computeIfAbsent(month, m -> new EmployeeAttendanceSummary.MonthAttendance(0, 0, 0, 0, 0, new HashMap<>()));

            Map<String, EmployeeAttendanceSummary.DayAttendanceMeta> days = monthData.getDays();

            if (weeklyOffs.contains(dayName)) {
                days.put(day, new EmployeeAttendanceSummary.DayAttendanceMeta("Weekly Off"));
                // No change in absence count or working day count
            } else {
                days.put(day, new EmployeeAttendanceSummary.DayAttendanceMeta("Absent"));
                monthData.setTotalDays(monthData.getTotalDays() + 1);
                monthData.setUnapprovedAbsenceDays(monthData.getUnapprovedAbsenceDays() + 1);
            }
        }

        summaryRepo.save(summary);
    }



    // Mark weekends for the user for a month
    public void markWeekendsForEmployee(String employeeId, int year, int month) {
        User user = userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        List<String> weeklyOffs = user.getWeeklyOffs(); // e.g. ["SUNDAY", "SATURDAY"]
        EmployeeAttendanceSummary summary = summaryRepo.findByEmployeeId(employeeId)
                .orElse(new EmployeeAttendanceSummary("summary_" + employeeId, employeeId, new HashMap<>()));

        YearMonth yearMonth = YearMonth.of(year, month);
        int daysInMonth = yearMonth.lengthOfMonth();

        String yearStr = String.valueOf(year);
        String monthStr = String.valueOf(month);

        EmployeeAttendanceSummary.YearAttendance yearAttendance =
                summary.getYears().computeIfAbsent(yearStr, y -> new EmployeeAttendanceSummary.YearAttendance(new HashMap<>()));

        EmployeeAttendanceSummary.MonthAttendance monthAttendance =
                yearAttendance.getMonths().computeIfAbsent(monthStr, m -> new EmployeeAttendanceSummary.MonthAttendance(0, 0, 0, 0, 0, new HashMap<>()));

        Map<String, EmployeeAttendanceSummary.DayAttendanceMeta> daysMap = monthAttendance.getDays();

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = LocalDate.of(year, month, day);
            String dayOfWeek = date.getDayOfWeek().toString(); // e.g. "SUNDAY"

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
        LocalDate today = checkinTime.toLocalDate();

        // ✅ Mark backdated absents on first-time check-in
        markPastDaysAbsentIfFirstCheckin(employeeId);

        DailyAttendance daily = dailyRepo.findByEmployeeIdAndDate(employeeId, today)
                .orElse(new DailyAttendance(null, employeeId, today, new ArrayList<>(), 0));

        daily.getCheckInOutTimes().add(new CheckInOut(checkinTime, null, checkinImgUrl));
        dailyRepo.save(daily);

        updateSummaryOnCheckIn(employeeId, today);
    }

}





