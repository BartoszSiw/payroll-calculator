package pl.edashi.payroll.model;

public class Employee {
    private int id;
    private String firstName;
    private String lastName;
    private String jobTitle;
    private double salaryMonth;

    public Employee(int id, String firstName, String lastName, String jobTitle, double salaryMonth) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.jobTitle = jobTitle;
        this.salaryMonth = salaryMonth;
    }

    // Getters
    public int getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getJobTitle() { return jobTitle; }
    public double getSalaryMonth() { return salaryMonth; }
}

