package pl.edashi.converter.auth;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import pl.edashi.converter.service.ConverterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(LoginServlet.class);

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String username = req.getParameter("username");
        String password = req.getParameter("password");

        String cfgPath = getServletContext() != null ? getServletContext().getInitParameter("configPath") : null;
        ConverterConfig cfg = new ConverterConfig(cfgPath);
        String expectedUser = cfg.getAuthUsername();
        String expectedPass = cfg.getAuthPassword();

        boolean ok = expectedUser.equals(username != null ? username.trim() : "")
                && expectedPass.equals(password != null ? password : "");

        String remote = req.getRemoteAddr();
        String ua = req.getHeader("User-Agent");
        log.info("Login attempt: user='{}' ok={} remote={} ua={}", username, ok, remote, ua);

        if (!ok) {
            HttpSession s = req.getSession(true);
            s.removeAttribute(AuthFilter.SESSION_ATTR_AUTH);
            log.info("Login rejected: sessionId={} attrCleared={}", s.getId(), AuthFilter.SESSION_ATTR_AUTH);
            resp.sendRedirect(req.getContextPath() + "/login.jsp?error=1");
            return;
        }

        HttpSession s = req.getSession(true);
        s.setAttribute(AuthFilter.SESSION_ATTR_AUTH, Boolean.TRUE);
        log.info("Login accepted: sessionId={} attrSet={} redirect={}", s.getId(), AuthFilter.SESSION_ATTR_AUTH, req.getContextPath() + "/index.jsp");
        resp.sendRedirect(req.getContextPath() + "/index.jsp");
    }
}

