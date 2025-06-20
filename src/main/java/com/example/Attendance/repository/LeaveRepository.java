package com.example.Attendance.repository;

import com.example.Attendance.model.LeaveModel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveRepository extends MongoRepository<LeaveModel, String> {
    List<LeaveModel> findByEmployeeIdAndStatusAndLeaveDatesBetween(
        String employeeId, String status, java.time.LocalDate start, java.time.LocalDate end);
} 