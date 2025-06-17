package com.example.Attendance.repository;

import com.example.Attendance.model.DailyAttendance;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyAttendanceRepository extends MongoRepository<DailyAttendance, String> {
    Optional<DailyAttendance> findByEmployeeIdAndDate(String employeeId, LocalDateTime date);

    boolean existsByEmployeeId(String employeeId);

    List<DailyAttendance> findByEmployeeIdAndDateBetween(String employeeId, LocalDateTime startDate, LocalDateTime endDate);

    List<DailyAttendance> findByEmployeeIdInAndDate(List<String> employeeIds, LocalDateTime date);
}
