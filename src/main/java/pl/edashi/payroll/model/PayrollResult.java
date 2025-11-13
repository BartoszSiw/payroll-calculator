package pl.edashi.payroll.model;
public class PayrollResult {
    private final double baseSalary;
    private final double totalHours;
    private final double overtimePay;
    private final double grossSalary;
    private final double socialContributions;
    private final double healthInsurance;
    private final double incomeTax;
    private final double netSalary;

    public PayrollResult(double baseSalary, double totalHours, double overtimePay,
                         double grossSalary, double socialContributions,
                         double healthInsurance, double incomeTax, double netSalary) {
        this.baseSalary = baseSalary;
        this.totalHours = totalHours;
        this.overtimePay = overtimePay;
        this.grossSalary = grossSalary;
        this.socialContributions = socialContributions;
        this.healthInsurance = healthInsurance;
        this.incomeTax = incomeTax;
        this.netSalary = netSalary;
    }

    // Getters
    public double getBaseSalary() { return baseSalary; }
    public double getTotalHours() { return totalHours; }
    public double getOvertimePay() { return overtimePay; }
    public double getGrossSalary() { return grossSalary; }
    public double getSocialContributions() { return socialContributions; }
    public double getHealthInsurance() { return healthInsurance; }
    public double getIncomeTax() { return incomeTax; }
    public double getNetSalary() { return netSalary; }
}

