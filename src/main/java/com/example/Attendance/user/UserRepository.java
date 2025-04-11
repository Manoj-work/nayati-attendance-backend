package com.example.Attendance.user;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    boolean existsByEmployeeId(String employeeId);
    Optional<User> findByEmployeeId(String employeeId);
    List<User> findByFlag(String status);
}
