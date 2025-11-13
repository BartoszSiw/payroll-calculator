package pl.edashi.payroll.servlet;

import pl.edashi.payroll.service.PayrollService;
import pl.edashi.payroll.dao.EmployeeDAO;
import pl.edashi.payroll.dao.WorkHoursDAO;
import pl.edashi.payroll.model.PayrollResult;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet("/PayrollServlet")
public class PayrollServlet extends HttpServlet {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private PayrollService payrollService;

    @Override
    public void init() throws ServletException {
        // Initialize DAOs and service once when servlet starts
        EmployeeDAO employeeDAO = new EmployeeDAO();
        WorkHoursDAO workHoursDAO = new WorkHoursDAO();
        payrollService = new PayrollService(employeeDAO, workHoursDAO);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Read employeeId from request
        int employeeId = Integer.parseInt(request.getParameter("employeeIdInput"));

        // Calculate payroll
        PayrollResult result = payrollService.calculatePayroll(employeeId);
        // return html
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        out.println("<h2>Payroll Calculation</h2>");
        out.println("<table>");
        response.getWriter().println("<h2>Payroll Calculation</h2>");
        response.getWriter().println("<table border='1' cellpadding='8' cellspacing='0'>");
        response.getWriter().println("<tr><th>Item</th><th>Value</th></tr>");
        response.getWriter().println("<tr><td>Base Salary</td><td>" + result.getBaseSalary() + "</td></tr>");
        response.getWriter().println("<tr><td>Total Hours</td><td>" + result.getTotalHours() + "</td></tr>");
        response.getWriter().println("<tr><td>Overtime Pay</td><td>" + result.getOvertimePay() + "</td></tr>");
        response.getWriter().println("<tr><td>Gross Salary</td><td>" + result.getGrossSalary() + "</td></tr>");
        response.getWriter().println("<tr><td>Social Contributions</td><td>" + result.getSocialContributions() + "</td></tr>");
        response.getWriter().println("<tr><td>Health Insurance</td><td>" + result.getHealthInsurance() + "</td></tr>");
        response.getWriter().println("<tr><td>Income Tax</td><td>" + result.getIncomeTax() + "</td></tr>");
        response.getWriter().println("<tr><td><strong>Net Salary</strong></td><td><strong>" + result.getNetSalary() + "</strong></td></tr>");
        response.getWriter().println("</table>");

        // Return JSON response
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String json = String.format(
            "{ \"baseSalary\": %.2f, \"totalHours\": %.2f, \"overtimePay\": %.2f, " +
            "\"grossSalary\": %.2f, \"socialContributions\": %.2f, " +
            "\"healthInsurance\": %.2f, \"incomeTax\": %.2f, \"netSalary\": %.2f }",
            result.getBaseSalary(),
            result.getTotalHours(),
            result.getOvertimePay(),
            result.getGrossSalary(),
            result.getSocialContributions(),
            result.getHealthInsurance(),
            result.getIncomeTax(),
            result.getNetSalary()
        );

        response.getWriter().write(json);
    }
}

