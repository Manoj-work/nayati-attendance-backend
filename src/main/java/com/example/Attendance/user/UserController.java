package com.example.Attendance.user;

import com.example.Attendance.user.User;
import com.example.Attendance.user.UserRepository;
import com.example.Attendance.service.AttendanceService;
import com.example.Attendance.service.FaceVerificationService;
import com.example.Attendance.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequiredArgsConstructor
public class UserController {
    private final UserRepository userRepository;
    private final FaceVerificationService faceVerificationService;
    private final UserService userService;
    private final AttendanceService attendanceService;


    // 1. Fetch all users
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // get user with employeeId
    @GetMapping("/{employeeId}")
    public ResponseEntity<?> getUser(@PathVariable String employeeId){
        return ResponseEntity.ok(userService.getUserByEmployeeId(employeeId));
    }

    // 2. Store user with image
    @PostMapping("/register")
    public ResponseEntity<?> registerEmployee(
            @RequestParam String employeeId,
            @RequestParam String name,
            @RequestParam MultipartFile file,
            @RequestParam LocalDate joiningDate,
            @RequestParam List<String> weeklyOffs // Accept weekly offs like ["SUNDAY", "SATURDAY"]
    ) {
        try {
            // Check if employee already exists
            if (userService.existsByEmployeeId(employeeId)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Employee ID already exists! Please use a unique ID."));
            }

            // Save Employee Details and Image
            User savedEmployee = userService.saveEmployee(employeeId, name, file, joiningDate, weeklyOffs);

            return ResponseEntity.ok(savedEmployee);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process the uploaded file."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred. Please try again later."));
        }
    }


    //3.Find user by status
    @GetMapping("/status/{status}")
    public ResponseEntity<List<User>> getUserByStatus(@PathVariable String status){
        return ResponseEntity.ok(userService.getUserByStatus(status));
    }


}
