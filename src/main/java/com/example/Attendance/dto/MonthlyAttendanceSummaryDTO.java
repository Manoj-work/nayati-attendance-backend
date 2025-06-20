package com.example.Attendance.dto;

import java.time.LocalDate;
import java.util.List;

public class MonthlyAttendanceSummaryDTO {
    private List<LocalDate> presentDates;
    private List<LocalDate> fullLeaveDates;
    private List<LocalDate> halfDayLeaveDates;
    private List<LocalDate> fullCompoffDates;
    private List<LocalDate> halfCompoffDates;
    private List<LocalDate> weeklyOffDates;
    private List<LocalDate> absentDates;

    // Getters and setters
    public List<LocalDate> getPresentDates() { return presentDates; }
    public void setPresentDates(List<LocalDate> presentDates) { this.presentDates = presentDates; }
    public List<LocalDate> getFullLeaveDates() { return fullLeaveDates; }
    public void setFullLeaveDates(List<LocalDate> fullLeaveDates) { this.fullLeaveDates = fullLeaveDates; }
    public List<LocalDate> getHalfDayLeaveDates() { return halfDayLeaveDates; }
    public void setHalfDayLeaveDates(List<LocalDate> halfDayLeaveDates) { this.halfDayLeaveDates = halfDayLeaveDates; }
    public List<LocalDate> getFullCompoffDates() { return fullCompoffDates; }
    public void setFullCompoffDates(List<LocalDate> fullCompoffDates) { this.fullCompoffDates = fullCompoffDates; }
    public List<LocalDate> getHalfCompoffDates() { return halfCompoffDates; }
    public void setHalfCompoffDates(List<LocalDate> halfCompoffDates) { this.halfCompoffDates = halfCompoffDates; }
    public List<LocalDate> getWeeklyOffDates() { return weeklyOffDates; }
    public void setWeeklyOffDates(List<LocalDate> weeklyOffDates) { this.weeklyOffDates = weeklyOffDates; }
    public List<LocalDate> getAbsentDates() { return absentDates; }
    public void setAbsentDates(List<LocalDate> absentDates) { this.absentDates = absentDates; }
} 