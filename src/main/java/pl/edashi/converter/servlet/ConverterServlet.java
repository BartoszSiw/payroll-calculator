package pl.edashi.converter.servlet;

import pl.edashi.converter.service.ConverterService;
import pl.edashi.converter.model.ConversionResult;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet("/ConverterServlet")
public class ConverterServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private ConverterService converterService;

    @Override
    public void init() throws ServletException {
        converterService = new ConverterService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String type = request.getParameter("type"); // e.g. "currency" or "length"
        double input = Double.parseDouble(request.getParameter("input"));
        double factor = Double.parseDouble(request.getParameter("factor")); // optional

        ConversionResult result;
        if ("currency".equalsIgnoreCase(type)) {
            result = converterService.convertCurrency(input, factor);
        } else if ("length".equalsIgnoreCase(type)) {
            result = converterService.convertLength(input);
        } else {
            throw new ServletException("Unsupported conversion type: " + type);
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String json = String.format(
            "{ \"input\": %.2f, \"factor\": %.4f, \"output\": %.2f, \"type\": \"%s\" }",
            result.getInput(), result.getFactor(), result.getOutput(), result.getType()
        );
        response.getWriter().write(json);
    }
}

