package com.example.Attendance.repository;

import com.example.Attendance.model.DailyAttendance;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyAttendanceRepository extends MongoRepository<DailyAttendance, String> {

    boolean existsByEmployeeId(String employeeId);



    Optional<DailyAttendance> findByEmployeeIdAndDateEpoch(String employeeId, long todayEpoch);

    List<DailyAttendance> findByEmployeeIdInAndDateEpoch(List<String> teamMembers, long todayEpoch);

    List<DailyAttendance> findByEmployeeIdAndDateEpochBetween(String employeeId, long startEpoch, long endEpoch);
}
