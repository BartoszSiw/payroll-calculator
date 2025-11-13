package pl.edashi.payroll.service;
import pl.edashi.payroll.dao.EmployeeDAO;
import pl.edashi.payroll.dao.WorkHoursDAO;
import pl.edashi.payroll.model.Employee;
import pl.edashi.payroll.model.WorkHours;
import pl.edashi.payroll.model.PayrollResult;

public class PayrollService {

    private final EmployeeDAO employeeDAO;
    private final WorkHoursDAO workHoursDAO;

    // Inject DAOs (could be via constructor or dependency injection framework)
    public PayrollService(EmployeeDAO employeeDAO, WorkHoursDAO workHoursDAO) {
        this.employeeDAO = employeeDAO;
        this.workHoursDAO = workHoursDAO;
    }

    public PayrollResult calculatePayroll(int employeeId) {
        // 1. Load employee data
        Employee employee = employeeDAO.findById(employeeId);
        WorkHours workHours = workHoursDAO.findByEmployeeId(employeeId);

        if (employee == null) {
            throw new IllegalArgumentException("Employee not found: " + employeeId);
        }

        double baseSalary = employee.getSalaryMonth();
        double totalHours = workHours != null ? workHours.getTotalHours() : 0;

        // 2. Overtime calculation
        final int BASE_HOURS = 160;
        double overtimeRate = baseSalary / BASE_HOURS * 1.5;
        double overtimePay = totalHours > BASE_HOURS ? (totalHours - BASE_HOURS) * overtimeRate : 0;
        double grossSalary = baseSalary + overtimePay;

        // 3. Contributions (simplified starter version)
        double pension = grossSalary * 0.0976;
        double disability = grossSalary * 0.015;
        double sickness = grossSalary * 0.0245;
        double socialContributions = pension + disability + sickness;

        // 4. Taxable base
        double taxableBase = grossSalary - socialContributions;

        // 5. Health insurance
        double healthInsurance = taxableBase * 0.09;

        // 6. Income tax (simplified flat 12%)
        double incomeTax = taxableBase * 0.12;

        // 7. Net salary
        double netSalary = grossSalary - socialContributions - healthInsurance - incomeTax;

        // 8. Return result object
        return new PayrollResult(baseSalary, totalHours, overtimePay, grossSalary,
                                 socialContributions, healthInsurance, incomeTax, netSalary);
    }
}

