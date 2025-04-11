package com.example.Attendance.user;

import com.example.Attendance.service.MinIOService;
import com.example.Attendance.user.User;
import com.example.Attendance.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final MinIOService minIOService;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserByEmployeeId(String employeeId) {
        return userRepository.findByEmployeeId(employeeId);
    }

    public boolean existsByEmployeeId(String employeeId) {
        return userRepository.existsByEmployeeId(employeeId);
    }

    public User saveEmployee(String employeeId, String name, MultipartFile file, LocalDate joiningDate, List<String> weeklyOffs) throws Exception {
        // Upload Image to MinIO
        String photoUrl = minIOService.getPhotoUrl(employeeId, file);

        // Create and Save Employee
        User employee = new User();
        employee.setEmployeeId(employeeId);
        employee.setName(name);
        employee.setPhotoUrl(photoUrl);
        employee.setJoiningDate(joiningDate);
        employee.setWeeklyOffs(weeklyOffs); // <- Add this line to store weekly offs

        return userRepository.save(employee);
    }


    public List<User> getUserByStatus(String status) {
        return userRepository.findByFlag(status);
    }
}
