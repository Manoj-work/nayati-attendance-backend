package com.example.Attendance.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CheckInOut {
    private String type; // "checkin" or "checkout"
    private LocalDateTime timestamp;
    private String checkinImgUrl; // Only used for checkin type

    // Getters & Setters
}
