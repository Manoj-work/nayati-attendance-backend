package com.example.Attendance.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.List;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document("daily_attendance")
public class DailyAttendance {
    @Id
    private String id;
    private String employeeId;
    private LocalDate date;
    private List<CheckInOut> checkInOutTimes;
    private double totalWorkingHours;

    // Getters & Setters
}
