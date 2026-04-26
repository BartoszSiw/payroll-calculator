package pl.edashi.converter.auth;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import pl.edashi.converter.license.LicenseStatus;
import pl.edashi.converter.license.LicenseVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthFilter implements Filter {

    public static final String SESSION_ATTR_AUTH = "payacon.authenticated";
    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    private static final Set<String> PUBLIC_PATH_PREFIXES = Set.of(
            "/login.jsp",
            "/license.jsp",
            "/LoginServlet",
            "/LogoutServlet",
            "/styles.css",
            "/script.js"
    );

    @Override
    public void init(FilterConfig filterConfig) {
        // no-op
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest req) || !(response instanceof HttpServletResponse resp)) {
            chain.doFilter(request, response);
            return;
        }

        String ctx = req.getContextPath() == null ? "" : req.getContextPath();
        String uri = req.getRequestURI() == null ? "" : req.getRequestURI();
        String path = uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;

        // Allow static and login endpoints
        for (String prefix : PUBLIC_PATH_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "?")) {
                chain.doFilter(request, response);
                return;
            }
        }
        // Also allow resources under /converter/ and /payroll/ that are public? (No: these must be protected)

        // License gate (blocks app even if user knows credentials)
        LicenseStatus lic = null;
        try {
            Object o = req.getServletContext().getAttribute(LicenseVerifier.CTX_ATTR_LICENSE);
            if (o instanceof LicenseStatus ls) lic = ls;
        } catch (Throwable ignored) {}
        if (lic == null || !lic.valid) {
            log.warn("AuthFilter: license invalid -> redirect. path={} remote={} msg={}",
                    path, req.getRemoteAddr(), lic == null ? "<null>" : lic.message);
            resp.sendRedirect(ctx + "/license.jsp");
            return;
        }

        // Module gate by URL prefix
        if (path.startsWith("/payroll/") && !lic.moduleEnabled("payroll", false)) {
            log.warn("AuthFilter: payroll disabled by license -> redirect. path={} remote={}", path, req.getRemoteAddr());
            resp.sendRedirect(ctx + "/license.jsp?module=payroll");
            return;
        }
        if (path.startsWith("/converter/") && !lic.moduleEnabled("converter", true)) {
            log.warn("AuthFilter: converter disabled by license -> redirect. path={} remote={}", path, req.getRemoteAddr());
            resp.sendRedirect(ctx + "/license.jsp?module=converter");
            return;
        }

        HttpSession session = req.getSession(false);
        boolean authed = session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_ATTR_AUTH));
        if (authed) {
            log.debug("AuthFilter: allowed. path={} sessionId={}", path, session.getId());
            chain.doFilter(request, response);
            return;
        }

        log.info("AuthFilter: not authenticated -> redirect to login. path={} remote={} sessionPresent={} sessionId={}",
                path, req.getRemoteAddr(), session != null, session != null ? session.getId() : "<none>");
        String target = ctx + "/login.jsp";
        resp.sendRedirect(target);
    }

    @Override
    public void destroy() {
        // no-op
    }
}

