package com.example.Attendance.service;

import com.example.Attendance.dto.DayAttendanceResponse;
import com.example.Attendance.dto.LogEntryDTO;
import com.example.Attendance.exception.CustomException;
import com.example.Attendance.model.*;
import com.example.Attendance.repository.DailyAttendanceRepository;
import com.example.Attendance.repository.EmployeeAttendanceSummaryRepository;
import com.example.Attendance.util.EpochUtil;
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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.*;
import java.util.*;

import com.example.Attendance.dto.EmployeeDetailsDTO;
import org.springframework.web.client.HttpClientErrorException;
import com.example.Attendance.repository.RegisteredUserRepository;

import static java.util.Arrays.stream;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    @Value("${EMPLOYEE_SERVICE_URL}")
    private String EMPLOYEE_SERVICE_BASE_URL;

    @Value("${PYTHON_FACE_RECOGNITION}")
    private String PYTHON_FACE_RECOGNITION;

    private final DailyAttendanceRepository dailyRepo;
    private final EmployeeAttendanceSummaryRepository summaryRepo;
    private final FaceVerificationService faceVerificationService;
    private final MinIOService minIOService;
    private final RegisteredUserRepository registeredUserRepository;
    private final EmployeeService employeeService;

    // Helper method to check if employee is registered
    public boolean isEmployeeRegistered(String empId) {
        return registeredUserRepository.findByEmpId(empId).isPresent();
    }

    public String checkOut(String employeeId) {
        // 1. Check if employee is registered
        if (!isEmployeeRegistered(employeeId)) {
            throw new CustomException("User is not registered", HttpStatus.NOT_FOUND);
        }

        // 2. Get current UTC time and today's start (in IST)
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        long currentEpoch = EpochUtil.currentEpochSeconds();
        long todayEpoch = EpochUtil.toEpochSeconds(LocalDateTime.now(zone).toLocalDate().atStartOfDay(), zone);

        // 3. Find today's attendance record
        DailyAttendance daily = dailyRepo.findByEmployeeIdAndDateEpoch(employeeId, todayEpoch)
                .orElseThrow(() -> new CustomException("No check-in found for today", HttpStatus.NOT_FOUND));

        // 4. Validate last log is a check-in
        List<CheckInOut> logs = daily.getLogs();
        if (logs.isEmpty() || !"checkin".equalsIgnoreCase(logs.get(logs.size() - 1).getType())) {
            throw new CustomException("No check-in found for today", HttpStatus.BAD_REQUEST);
        }

        // 5. Add checkout log with epoch timestamp
        logs.add(new CheckInOut("checkout", currentEpoch, null));

        // 6. Save and return
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
        if (!isEmployeeRegistered(employeeId)) {
            throw new CustomException("User is not registered", HttpStatus.NOT_FOUND);
        }

        DayAttendanceResponse response = new DayAttendanceResponse();

        // 1. Get start of day epoch in IST
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        long dayStartEpoch = EpochUtil.toEpochSeconds(date.atStartOfDay(), zone);

        // 2. Fetch attendance for the specific day
        Optional<DailyAttendance> optionalAttendance = dailyRepo.findByEmployeeIdAndDateEpoch(employeeId, dayStartEpoch);
        if (optionalAttendance.isEmpty()) return response;

        DailyAttendance dailyAttendance = optionalAttendance.get();
        List<CheckInOut> logs = dailyAttendance.getLogs();
        if (logs == null || logs.isEmpty()) return response;

//        response.setDailyAttendance(dailyAttendance);

        // 3. Sort logs by timestampEpoch
        logs.sort(Comparator.comparingLong(CheckInOut::getTimestampEpoch));

        // 4. Prepare for pairing and time tracking
        LocalDateTime firstCheckin = null;
        LocalDateTime latestUnpairedCheckin = null;
        LocalDateTime lastCheckout = null;
        long totalSeconds = 0;

        Deque<Long> checkinStack = new ArrayDeque<>();

        for (CheckInOut log : logs) {
            String type = log.getType().toLowerCase();
            long timestampEpoch = log.getTimestampEpoch();

            switch (type) {
                case "checkin":
                    if (firstCheckin == null) {
                        firstCheckin = EpochUtil.fromEpochSeconds(timestampEpoch, zone);
                    }
                    checkinStack.push(timestampEpoch);
                    break;

                case "checkout":
                    if (!checkinStack.isEmpty()) {
                        long pairedCheckinEpoch = checkinStack.removeLast();
                        totalSeconds += (timestampEpoch - pairedCheckinEpoch);
                        lastCheckout = EpochUtil.fromEpochSeconds(timestampEpoch, zone);
                    }
                    break;

                default:
                    // Optional: log or skip unknown types
                    break;
            }
        }

        // If any unpaired checkins remain, get the latest one
        if (!checkinStack.isEmpty()) {
            latestUnpairedCheckin = EpochUtil.fromEpochSeconds(checkinStack.peekLast(), zone);
        }

        // 5. Set response fields
        response.setFirstCheckin(firstCheckin);
        response.setLatestCheckin(latestUnpairedCheckin);
        response.setLastCheckout(lastCheckout);

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        response.setWorkingHoursTillNow(String.format("%02d:%02d:%02d", hours, minutes, seconds));

        List<LogEntryDTO> readableLogs = logs.stream()
                .map(log -> new LogEntryDTO(
                        log.getType(),
                        EpochUtil.fromEpochSeconds(log.getTimestampEpoch(), zone).toString(),
                        log.getCheckinImgUrl()
                ))
                .collect(Collectors.toList());

//        response.setReadableLogs(readableLogs);

        response.setDailyAttendance(readableLogs);

        return response;
    }


    public Map<String, Object> getMonthlySummary(String employeeId, String year, String month) {
        // Check if employee is registered
        if (!isEmployeeRegistered(employeeId)) {
            throw new CustomException("User is not registered", HttpStatus.NOT_FOUND);
        }

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
        // Check if employee is registered
        if (!isEmployeeRegistered(employeeId)) {
            throw new CustomException("User is not registered", HttpStatus.NOT_FOUND);
        }

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
        // Check if employee is registered
        if (!isEmployeeRegistered(employeeId)) {
            throw new CustomException("User is not registered", HttpStatus.NOT_FOUND);
        }

        // Get employee details from external service
        Optional<Employee> employeeDetails = employeeService.getEmployeeByEmpId(employeeId);
        LocalDate joiningDate = employeeDetails.get().getJoiningDate();
        List<String> weeklyOffs = employeeDetails.get().getWeeklyOffs()
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
        // Check if employee is registered
        if (!isEmployeeRegistered(employeeId)) {
            throw new CustomException("User is not registered", HttpStatus.NOT_FOUND);
        }

        // Get employee details from external service
        Optional<Employee> employeeDetails = employeeService.getEmployeeByEmpId(employeeId);
        List<String> weeklyOffs = employeeDetails.get().getWeeklyOffs();

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
        // 1. Check if employee exists in main system
        if (!employeeService.employeeExists(empId)) {
            return "Employee not found in the system";
        }

        // 2. Check if employee is already registered in attendance system
        Optional<RegisteredUser> existingUser = registeredUserRepository.findByEmpId(empId);
        if (existingUser.isPresent()) {
            return "Employee already registered for attendance";
        }

        // 3. Upload employee image to MinIO
        String imgUrl = minIOService.getPhotoUrl(empId, empImage);

        // 4. Call FaceRecognition service
        Map<String, Object> response = faceVerificationService.registerUser(empImage, empId, empName, imgUrl);

        return response.get("message").toString();
    }

    public Map<String, Object> handleSingleCheckin(MultipartFile file, String empId) throws IOException {
        // 1. Check if employee is registered
        if (!isEmployeeRegistered(empId)) {
            throw new CustomException("User is not registered", HttpStatus.NOT_FOUND);
        }

        byte[] fileBytes = file.getBytes();

        // 2. Call face recognition API
        Map<String, Object> recognitionResult = faceVerificationService.verifyByEmpId(file, empId);

        // 3. Check if employee was found
        if (!"match".equalsIgnoreCase((String) recognitionResult.get("status"))) {
            return Map.of(
                    "status", "not found",
                    "message", "Employee not recognized"
            );
        }

        // 4. Get employee details
        String employeeId = (String) recognitionResult.get("empId");
        Optional<Employee> employeeDetails = employeeService.getEmployeeByEmpId(employeeId);
        String name = employeeDetails.get().getName();

        // 5. Get current time as epoch
        long checkinEpoch = EpochUtil.currentEpochSeconds();

        // 6. Get start of day in UTC (for matching today's attendance)
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        long todayEpoch = EpochUtil.toEpochSeconds(LocalDateTime.now(zone).toLocalDate().atStartOfDay(), zone);

        Optional<DailyAttendance> existingAttendance = dailyRepo.findByEmployeeIdAndDateEpoch(employeeId, todayEpoch);

        if (existingAttendance.isPresent()) {
            List<CheckInOut> logs = existingAttendance.get().getLogs();
            if (!logs.isEmpty()) {
                CheckInOut lastLog = logs.get(logs.size() - 1);
                if ("checkin".equalsIgnoreCase(lastLog.getType())) {
                    return Map.of(
                            "status", "error",
                            "message", "Please check out before checking in again"
                    );
                }
            }
        }

        // 7. Rebuild MultipartFile for MinIO
        MultipartFile newFile = buildMultipartFileFromBytes(file, fileBytes);

        // 8. Upload check-in image
        String checkinImgUrl = minIOService.getCheckinImgUrl(employeeId, newFile);

        // 9. Record daily attendance using epoch
        recordDailyAttendance(employeeId, name, checkinImgUrl, checkinEpoch, todayEpoch);

        // 10. Return success response
        return Map.of(
                "status", "present",
                "employee", name,
                "emp_id", employeeId,
                "message", "Attendance marked successfully"
        );
    }

    public Map<String, Object> handleTeamcheckin(MultipartFile file, String empId) throws IOException {
        // 1. Check if manager is registered
        if (!isEmployeeRegistered(empId)) {
            throw new CustomException("User is not registered", HttpStatus.NOT_FOUND);
        }

        byte[] fileBytes = file.getBytes();

        // 2. Get employee IDs under this manager
        Optional<Employee> employee = employeeService.getEmployeeByEmpId(empId);
        List<String> empIdList = employee.get().getAssignTo();

        // 3. Call face recognition API
        Map<String, Object> recognitionResult = faceVerificationService.verifyByEmpIdList(file, empIdList);

        // 4. Check if employee was found
        if (!"match".equalsIgnoreCase((String) recognitionResult.get("status"))) {
            return Map.of(
                    "status", "not found",
                    "message", "Employee not recognized"
            );
        }

        // 5. Get employee details
        String employeeId = (String) recognitionResult.get("empId");
        Optional<Employee> employeeDetails = employeeService.getEmployeeByEmpId(employeeId);
        String name = employeeDetails.get().getName();

        // 6. Get current epoch time and today’s start (in IST)
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        long checkinEpoch = EpochUtil.currentEpochSeconds();
        long todayEpoch = EpochUtil.toEpochSeconds(LocalDateTime.now(zone).toLocalDate().atStartOfDay(), zone);

        // 7. Check if already checked in and not checked out
        Optional<DailyAttendance> existingAttendance = dailyRepo.findByEmployeeIdAndDateEpoch(employeeId, todayEpoch);

        if (existingAttendance.isPresent()) {
            List<CheckInOut> logs = existingAttendance.get().getLogs();
            if (!logs.isEmpty()) {
                CheckInOut lastLog = logs.get(logs.size() - 1);
                if ("checkin".equalsIgnoreCase(lastLog.getType())) {
                    return Map.of(
                            "status", "error",
                            "message", "Please check out before checking in again"
                    );
                }
            }
        }

        // 8. Rebuild MultipartFile for MinIO
        MultipartFile newFile = buildMultipartFileFromBytes(file, fileBytes);

        // 9. Upload check-in image
        String checkinImgUrl = minIOService.getCheckinImgUrl(employeeId, newFile);

        // 10. Record daily attendance using epoch
        recordDailyAttendance(employeeId, name, checkinImgUrl, checkinEpoch, todayEpoch);

        // 11. Return success response
        return Map.of(
                "status", "present",
                "employee", name,
                "emp_id", employeeId,
                "message", "Attendance marked successfully"
        );
    }


    public Map<String, Object> manualAttendanceMarking(String empId, MultipartFile file) {
        // 1. Check if employee is registered
        if (!isEmployeeRegistered(empId)) {
            throw new CustomException("User is not registered", HttpStatus.NOT_FOUND);
        }

        // 2. Get employee details
        Optional<Employee> employeeDetails = employeeService.getEmployeeByEmpId(empId);
        String empName = employeeDetails.get().getName();

        // 3. Get current epoch timestamp and today's epoch (start of day in IST)
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        long checkinEpoch = EpochUtil.currentEpochSeconds();
        long todayEpoch = EpochUtil.toEpochSeconds(LocalDateTime.now(zone).toLocalDate().atStartOfDay(), zone);

        // 4. Check if already checked in and not checked out
        Optional<DailyAttendance> existingAttendance = dailyRepo.findByEmployeeIdAndDateEpoch(empId, todayEpoch);
        if (existingAttendance.isPresent()) {
            List<CheckInOut> logs = existingAttendance.get().getLogs();
            if (!logs.isEmpty()) {
                CheckInOut lastLog = logs.get(logs.size() - 1);
                if ("checkin".equalsIgnoreCase(lastLog.getType())) {
                    return Map.of(
                            "status", "error",
                            "message", "Please check out before checking in again"
                    );
                }
            }
        }

        // 5. Upload check-in image to MinIO
        String checkinImgUrl = minIOService.getCheckinImgUrl(empId, file);

        // 6. Record daily attendance using epoch
        recordDailyAttendance(empId, empName, checkinImgUrl, checkinEpoch, todayEpoch);

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
            @Override public void transferTo(File dest) { throw new UnsupportedOperationException(); }
        };
    }

    private void recordDailyAttendance(String employeeId, String name, String checkinImgUrl, long checkinEpoch, long todayEpoch) {
        // ✅ Mark backdated absents on first-time check-in
        markPastDaysAbsentIfFirstCheckin(employeeId);

        DailyAttendance daily = dailyRepo.findByEmployeeIdAndDateEpoch(employeeId, todayEpoch)
                .orElse(new DailyAttendance(null, employeeId, todayEpoch, new ArrayList<>()));

        // Create a new CheckInOut log with epoch
        CheckInOut checkInLog = new CheckInOut("checkin", checkinEpoch, checkinImgUrl);
        daily.getLogs().add(checkInLog);

        // Save the updated attendance
        dailyRepo.save(daily);

        // Convert todayEpoch to LocalDate (in IST) for summary update
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        LocalDate todayDate = EpochUtil.fromEpochSecondsToDate(todayEpoch, zone);

        updateSummaryOnCheckIn(employeeId, todayDate);
    }


    public List<RegisteredUser> getRegisteredUsers() {
        return registeredUserRepository.findAll();
    }

    public Optional<RegisteredUser> getRegisteredUserByEmpId(String empId) {
        return registeredUserRepository.findByEmpId(empId);
    }

    public Map<String, Object> getTeamCheckInStatus(String managerId) {
        // 1. Get manager's team members
        Optional<Employee> manager = employeeService.getEmployeeByEmpId(managerId);
        if (manager.isEmpty()) {
            throw new CustomException("Manager not found", HttpStatus.NOT_FOUND);
        }

        List<String> teamMembers = manager.get().getAssignTo();

        // 2. Get today's epoch start (Asia/Kolkata)
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        long todayEpoch = EpochUtil.toEpochSeconds(LocalDateTime.now(zone).toLocalDate().atStartOfDay(), zone);

        // 3. Batch fetch attendance records for today
        List<DailyAttendance> teamAttendance = dailyRepo.findByEmployeeIdInAndDateEpoch(teamMembers, todayEpoch);

        // 4. Map attendance by employee ID
        Map<String, DailyAttendance> attendanceMap = teamAttendance.stream()
                .collect(Collectors.toMap(DailyAttendance::getEmployeeId, attendance -> attendance));

        // 5. Fetch employee details
        List<Employee> teamDetails = employeeService.getEmployeesByEmpIds(teamMembers);
        Map<String, Employee> employeeMap = teamDetails.stream()
                .collect(Collectors.toMap(Employee::getEmployeeId, employee -> employee));

        // 6. Prepare response
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> teamStatus = new ArrayList<>();

        for (String empId : teamMembers) {
            Map<String, Object> memberStatus = new HashMap<>();
            memberStatus.put("empId", empId);

            // Get employee details
            Employee employee = employeeMap.get(empId);
            if (employee == null) continue;
            memberStatus.put("name", employee.getName());

            // Get attendance logs
            DailyAttendance attendance = attendanceMap.get(empId);
            if (attendance != null && attendance.getLogs() != null && !attendance.getLogs().isEmpty()) {
                CheckInOut lastLog = attendance.getLogs().get(attendance.getLogs().size() - 1);
                memberStatus.put("status", lastLog.getType().equalsIgnoreCase("checkin") ? "checked_in" : "checked_out");

                // Convert timestampEpoch to readable LocalDateTime in IST
                LocalDateTime lastActionTime = EpochUtil.fromEpochSeconds(lastLog.getTimestampEpoch(), zone);
                memberStatus.put("lastActionTime", lastActionTime.toString());
            } else {
                memberStatus.put("status", "not_checked_in");
            }

            teamStatus.add(memberStatus);
        }

        response.put("teamStatus", teamStatus);
        response.put("totalMembers", teamMembers.size());
        response.put("checkedInCount", teamStatus.stream()
                .filter(status -> "checked_in".equals(status.get("status")))
                .count());

        return response;
    }

    public Map<String, Object> getRegisteredTeamMembers(String managerId) {
        // Get manager's team members
        Optional<Employee> manager = employeeService.getEmployeeByEmpId(managerId);
        if (manager.isEmpty()) {
            throw new CustomException("Manager not found", HttpStatus.NOT_FOUND);
        }
        List<String> teamMembers = manager.get().getAssignTo();
        
        // Get all registered users
        List<RegisteredUser> allRegisteredUsers = registeredUserRepository.findAll();
        Set<String> registeredEmpIds = allRegisteredUsers.stream()
                .map(RegisteredUser::getEmpId)
                .collect(Collectors.toSet());
        
        // Filter team members who are registered for attendance
        List<String> registeredTeamMembers = teamMembers.stream()
                .filter(registeredEmpIds::contains)
                .collect(Collectors.toList());
        
        return Map.of(
                "managerId", managerId,
                "totalTeamMembers", teamMembers.size(),
                "registeredTeamMembers", registeredTeamMembers.size(),
                "registeredEmpIds", registeredTeamMembers
        );
    }

}





