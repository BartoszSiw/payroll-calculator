package pl.edashi.common.logging;
	import jakarta.servlet.*;
	import jakarta.servlet.annotation.WebFilter;
	import jakarta.servlet.http.HttpServletRequest;
	import org.slf4j.MDC;

	import java.io.IOException;
	import java.util.UUID;

	@WebFilter("/*")
	public class MdcFilter implements Filter {

	    @Override
	    public void init(FilterConfig filterConfig) { /* opcjonalnie */ }

	    @Override
	    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
	            throws IOException, ServletException {
	        String module = "converter"; // możesz ustawić dynamicznie na podstawie requestu
	        String requestId = UUID.randomUUID().toString();

	        MDC.put("module", module);
	        MDC.put("requestId", requestId);
	        try {
	            // opcjonalnie: expose requestId do response header
	            if (request instanceof HttpServletRequest) {
	                // ((HttpServletResponse) response).setHeader("X-Request-Id", requestId);
	            }
	            chain.doFilter(request, response);
	        } finally {
	            MDC.remove("module");
	            MDC.remove("requestId");
	        }
	    }

	    @Override
	    public void destroy() { /* opcjonalnie */ }
	}

