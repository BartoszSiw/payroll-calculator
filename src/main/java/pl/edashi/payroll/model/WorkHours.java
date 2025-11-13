package pl.edashi.payroll.model;

public class WorkHours {
    private int employeeId;
    private double totalHours;

    public WorkHours(int employeeId, double totalHours) {
        this.employeeId = employeeId;
        this.totalHours = totalHours;
    }

    public int getEmployeeId() { return employeeId; }
    public double getTotalHours() { return totalHours; }
}

