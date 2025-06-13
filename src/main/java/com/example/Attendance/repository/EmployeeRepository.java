package com.example.Attendance.repository;

import com.example.Attendance.model.Employee;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EmployeeRepository extends MongoRepository<Employee,String> {
    Optional<Employee> findByEmployeeId(String employeeId);
}
