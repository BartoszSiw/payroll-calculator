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
        //return html render jsp

        // put result in request scope
        request.setAttribute("payrollResult", result);

        // forward to JSP
        request.getRequestDispatcher("payrollResult.jsp").forward(request, response);

    }
}

