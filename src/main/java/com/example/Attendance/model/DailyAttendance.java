package com.example.Attendance.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
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
    private LocalDateTime date;
    private List<CheckInOut> logs;
}
