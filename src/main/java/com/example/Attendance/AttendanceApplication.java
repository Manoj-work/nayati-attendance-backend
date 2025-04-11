package com.example.Attendance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class AttendanceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AttendanceApplication.class, args);
	}

}
