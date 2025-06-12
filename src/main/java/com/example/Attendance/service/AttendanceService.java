package com.example.Attendance.service;

import com.example.Attendance.dto.DayAttendanceResponse;
import com.example.Attendance.exception.CustomException;
import com.example.Attendance.model.CheckInOut;
import com.example.Attendance.model.DailyAttendance;
import com.example.Attendance.model.EmployeeAttendanceSummary;
import com.example.Attendance.repository.DailyAttendanceRepository;
import com.example.Attendance.repository.EmployeeAttendanceSummaryRepository;
import com.example.Attendance.util.MinIOService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

import com.example.Attendance.dto.EmployeeDetailsDTO;
import org.springframework.web.client.HttpClientErrorException;
import com.example.Attendance.model.RegisteredUser;
import com.example.Attendance.repository.RegisteredUserRepository;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    @Value("${EMPLOYEE_SERVICE_URL}")
    private String EMPLOYEE_SERVICE_BASE_URL;

    @Value("${PYTHON_FACE_RECOGNITION}")
    private String PYTHON_FACE_RECOGNITION;



    private final DailyAttendanceRepository dailyRepo;
    private final EmployeeAttendanceSummaryRepository summaryRepo;
    private final EmployeeDetailsService employeeDetailsService;
    private final FaceVerificationService faceVerificationService;
    private final MinIOService minIOService;
    private final RegisteredUserRepository registeredUserRepository;

    public String handleFaceCheckin(String employeeId, MultipartFile file, LocalDateTime checkinTime) throws IOException {
        // 1. Get employee details from external service
        EmployeeDetailsDTO employeeDetails = employeeDetailsService.getEmployeeDetails(employeeId);
        String storedImageUrl = employeeDetails.getEmployeeImgUrl();
        String name = employeeDetails.getName();

        // 6. Use current time if not provided
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


    public DayAttendanceResponse getDailyData(String employeeId, LocalDate date) {
        LocalDateTime dateTime = date.atStartOfDay();
        DayAttendanceResponse response = new DayAttendanceResponse();
        
        // First check daily attendance
        Optional<DailyAttendance> dailyAttendance = dailyRepo.findByEmployeeIdAndDate(employeeId, dateTime);
        if (dailyAttendance.isPresent()) {
            response.setDailyAttendance(dailyAttendance.get());
            return response;
        }

        // If no daily attendance, check summary
        EmployeeAttendanceSummary summary = summaryRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new CustomException("No attendance found for this date", HttpStatus.NOT_FOUND));

        String year = String.valueOf(date.getYear());
        String month = String.valueOf(date.getMonthValue());
        String day = String.valueOf(date.getDayOfMonth());

        // Get the day's attendance from summary
        EmployeeAttendanceSummary.DayAttendanceMeta dayMeta = summary.getYears()
                .getOrDefault(year, new EmployeeAttendanceSummary.YearAttendance())
                .getMonths()
                .getOrDefault(month, new EmployeeAttendanceSummary.MonthAttendance())
                .getDays()
                .get(day);

        if (dayMeta == null) {
            throw new CustomException("No data found for this day", HttpStatus.NOT_FOUND);
        }

        response.setSummaryAttendance(dayMeta);
        return response;
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

    public void markBulkAttendance(String employeeId, String status, String leaveId, List<String> dateStrings) {
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
            EmployeeAttendanceSummary.DayAttendanceMeta dayMeta = new EmployeeAttendanceSummary.DayAttendanceMeta(status, null);
            
            // Set leaveId if provided, regardless of status
            if (leaveId != null && !leaveId.isEmpty()) {
                dayMeta.setLeaveId(leaveId);
            }
            
            days.put(day, dayMeta);
        }

        summaryRepo.save(summary);
    }


    // mark previous days as absent
    public void markPastDaysAbsentIfFirstCheckin(String employeeId) {
        // Get employee details from external service
        EmployeeDetailsDTO employeeDetails = employeeDetailsService.getEmployeeDetails(employeeId);
        LocalDate joiningDate = employeeDetails.getJoiningDate();
        List<String> weeklyOffs = employeeDetails.getWeeklyOffs()
                .stream()
                .map(String::toUpperCase)
                .toList();


        boolean hasPreviousAttendance = dailyRepo.existsByEmployeeId(employeeId);
        if (hasPreviousAttendance) return;

        EmployeeAttendanceSummary summary = summaryRepo.findByEmployeeId(employeeId)
                .orElse(new EmployeeAttendanceSummary("summary_" + employeeId, employeeId, new HashMap<>()));

        LocalDate today = LocalDate.now();
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
        // Get employee details from external service
        EmployeeDetailsDTO employeeDetails = employeeDetailsService.getEmployeeDetails(employeeId);
        List<String> weeklyOffs = employeeDetails.getWeeklyOffs();

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
        List<RegisteredUser> allUsers = registeredUserRepository.findAll();

        YearMonth yearMonth = YearMonth.now();
        int year = yearMonth.getYear();
        int month = yearMonth.getMonthValue();

        for (RegisteredUser user : allUsers) {
            markWeekendsForEmployee(user.getEmpId(), year, month);
        }
    }



    @Scheduled(cron = "0 0 0 1 * *") // Every 1st of the month at midnight
    public void autoMarkWeekends() {
        System.out.println("Running scheduled weekend marker...");
        markAllEmployeesWeekendsForCurrentMonth();
    }

    public String registerEmployee(String empId, String empName, MultipartFile empImage) throws IOException {
        // 1. Check if employee exists in the main system
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    EMPLOYEE_SERVICE_BASE_URL + empId + "/attendance-details",
                Map.class
            );
            
            // If we get here, employee exists in main system
            Map<String, Object> employeeDetails = response.getBody();
            
            // 2. Check if employee is already registered for attendance
            Optional<RegisteredUser> existingUser = registeredUserRepository.findByEmpId(empId);
            if (existingUser.isPresent()) {
                return "Employee already registered for attendance";
            }

            // 3. Upload image to MinIO in registration bucket
            String imgUrl = minIOService.getPhotoUrl(empId, empImage);

            // 4. Call registration API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", empImage.getResource());
            body.add("name", empName);
            body.add("empId", empId);
            body.add("imgUrl", imgUrl);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            ResponseEntity<String> registrationResponse = restTemplate.postForEntity(
                    PYTHON_FACE_RECOGNITION+"register",
                requestEntity,
                String.class
            );

            return registrationResponse.getBody();

        } catch (HttpClientErrorException.NotFound e) {
            return "Employee not found in the system";
        } catch (Exception e) {
            throw new RuntimeException("Error during employee registration: " + e.getMessage());
        }
    }

    public Map<String, Object> handleFaceRecognitionAttendance(MultipartFile file) throws IOException {
        byte[] fileBytes = file.getBytes();

        // 1. Call face recognition API
        Map<String, Object> recognitionResult = faceVerificationService.recognizeFace(file);
        
        // 2. Check if employee was found
        if (!"present".equalsIgnoreCase((String) recognitionResult.get("status"))) {
            return Map.of(
                "status", "not found",
                "message", "Employee not recognized"
            );
        }

        // 3. Get employee details
        String employeeId = (String) recognitionResult.get("empId");
        EmployeeDetailsDTO employeeDetails = employeeDetailsService.getEmployeeDetails(employeeId);
        String name = employeeDetails.getName();

        // 4. Check if already checked in and not checked out
        LocalDateTime checkinTime = LocalDateTime.now();
        LocalDateTime today = checkinTime.withHour(0).withMinute(0).withSecond(0).withNano(0);
        Optional<DailyAttendance> existingAttendance = dailyRepo.findByEmployeeIdAndDate(employeeId, today);

        if (existingAttendance.isPresent()) {
            List<CheckInOut> logs = existingAttendance.get().getLogs();
            if (!logs.isEmpty()) {
                CheckInOut lastLog = logs.get(logs.size() - 1);
                if (lastLog.getType().equals("checkin")) {
                    return Map.of(
                        "status", "error",
                        "message", "Please check out before checking in again"
                    );
                }
            }
        }

        // 4. Rebuild MultipartFile for MinIO
        MultipartFile newFile = buildMultipartFileFromBytes(file, fileBytes);

        // 5. Upload check-in image
        String checkinImgUrl = minIOService.getCheckinImgUrl(employeeId, newFile);

        // 6. Record daily attendance
        recordDailyAttendance(employeeId, name, checkinImgUrl, checkinTime);

        // 7. Return success response
        return Map.of(
            "status", "present",
            "employee", name,
            "emp_id", employeeId,
            "message", "Attendance marked successfully"
        );
    }

    public Map<String, Object> manualAttendanceMarking(String empId){

        EmployeeDetailsDTO employeeDetails = employeeDetailsService.getEmployeeDetails(empId);
        String empName = employeeDetails.getName();

        LocalDateTime checkinTime = LocalDateTime.now();
        LocalDateTime today = checkinTime.withHour(0).withMinute(0).withSecond(0).withNano(0);
        Optional<DailyAttendance> existingAttendance = dailyRepo.findByEmployeeIdAndDate(empId, today);

        if (existingAttendance.isPresent()) {
            List<CheckInOut> logs = existingAttendance.get().getLogs();
            if (!logs.isEmpty()) {
                CheckInOut lastLog = logs.get(logs.size() - 1);
                if (lastLog.getType().equals("checkin")) {
                    return Map.of(
                            "status", "error",
                            "message", "Please check out before checking in again"
                    );
                }
            }
        }

//        String checkinImgUrl = minIOService.getCheckinImgUrl(employeeId, newFile);

        // 6. Record daily attendance
        recordDailyAttendance(empId,empName, "markedManually", checkinTime);

        // 7. Return success response
        return Map.of(
                "status", "present",
                "employee", empName,
                "emp_id", empId,
                "message", "Attendance marked successfully"
        );
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

    public List<RegisteredUser> getRegisteredUsers() {
        return registeredUserRepository.findAll();
    }

    public Optional<RegisteredUser> getRegisteredUserByEmpId(String empId) {
        return registeredUserRepository.findByEmpId(empId);
    }

}





