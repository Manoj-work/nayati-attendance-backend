package com.example.Attendance.exception;
import org.springframework.http.HttpStatus;

public class CustomException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String message;

    public CustomException(String message, HttpStatus httpStatus) {
        super(message);
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
