package com.st6.wc.employee.repo;

import com.st6.wc.employee.Employee;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bare Spring Data repository for {@link Employee} (task 1.5). Finder queries land in 1.6. */
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {}
