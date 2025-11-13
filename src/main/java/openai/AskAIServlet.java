package openai;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import org.json.JSONObject;
@WebServlet("/AskAIServlet")
public class AskAIServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            // Read JSON input from frontend
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JSONObject input = new JSONObject(sb.toString());
            String prompt = input.getString("prompt");

            // Dummy AI response (pretend we analyzed the prompt)
            String answer = "🤖 Dummy AI says: You asked '" + prompt + "'. "
                          + "In the future, this will be answered by a real AI service.";

            // Send JSON back to frontend
            response.setContentType("application/json");
            response.getWriter().write(new JSONObject().put("answer", answer).toString());

        } catch (Exception e) {
            e.printStackTrace();
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Dummy AI failed\"}");
        }
    }
}

