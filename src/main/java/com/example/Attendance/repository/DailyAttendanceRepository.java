package com.example.Attendance.repository;

import com.example.Attendance.model.DailyAttendance;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyAttendanceRepository extends MongoRepository<DailyAttendance, String> {
    Optional<DailyAttendance> findByEmployeeIdAndDate(String employeeId, LocalDate date);

    boolean existsByEmployeeId(String employeeId);

    List<DailyAttendance> findByEmployeeIdAndDateBetween(String employeeId, LocalDate startDate, LocalDate endDate);
}
