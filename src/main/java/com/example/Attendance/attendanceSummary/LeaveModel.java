package com.example.Attendance.attendanceSummary;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "Leaves")
public class LeaveModel {
    @Id
    private String id;
    private String leaveId;
    private String employeeId;
    private String companyId;
    private String leaveName; // "Leave" or "Comp-Off"
    private List<LocalDate> leaveDates;
    private String shiftType; // "FULL_DAY", "FIRST_HALF", "SECOND_HALF"
    private String reason;
    private String status; // Only count if "Approved"
    private String remarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Getters and setters
    // (Lombok can be added if desired)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLeaveId() { return leaveId; }
    public void setLeaveId(String leaveId) { this.leaveId = leaveId; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }
    public String getLeaveName() { return leaveName; }
    public void setLeaveName(String leaveName) { this.leaveName = leaveName; }
    public List<LocalDate> getLeaveDates() { return leaveDates; }
    public void setLeaveDates(List<LocalDate> leaveDates) { this.leaveDates = leaveDates; }
    public String getShiftType() { return shiftType; }
    public void setShiftType(String shiftType) { this.shiftType = shiftType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
} 