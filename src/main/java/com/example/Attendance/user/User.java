package com.example.Attendance.user;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.List;

@Document(collection = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class User {
    @Id
    private String id;
    private String employeeId;
    private String name;
    private String photoUrl;
    private String flag = "Absent";
    private LocalDate joiningDate;
    private List<String> weeklyOffs;

}
