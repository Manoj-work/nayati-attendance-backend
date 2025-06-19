package com.example.Attendance.repository;

import com.example.Attendance.model.Employee;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends MongoRepository<Employee, String> {
    Optional<Employee> findByEmployeeId(String employeeId);
    List<Employee> findByEmployeeIdIn(List<String> employeeIds);
    List<Employee> findByCompanyId(String companyId);
}
