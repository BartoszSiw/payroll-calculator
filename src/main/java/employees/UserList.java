package employees;
import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/UserList")
public class UserList extends HttpServlet {
    /**
	 * 
	 */

	private static final long serialVersionUID = 1L;

	protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
		System.out.println("Servlet is being accessed.");
		log("Servlet method called!");
        response.setContentType("text/html");
        System.out.println("Request method: " + request.getMethod());

        PrintWriter out = response.getWriter();
        String name = request.getParameter("name");
        out.println("<html><body>");
        out.println("<h2>Hello, " + name + "!</h2>");
        out.println("</body></html>");
    }
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
	        throws ServletException, IOException {
		System.out.println("My Request method: " + request.getMethod());

	    response.getWriter().println("Handling POST request");
	}

}

