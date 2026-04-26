<%@ page contentType="text/html; charset=UTF-8" %>
<%
    String error = request.getParameter("error");
    boolean showError = "1".equals(error);
%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta http-equiv="pragma" content="no-cache">
  <meta http-equiv="cache-control" content="no-cache">
  <meta http-equiv="expires" content="0">
  <title>PayACon — Logowanie</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/styles.css?v=1">
  <style>
    .login-wrap { max-width: 520px; margin: 28px auto; }
    .login-card { background:#f8f9fa; padding:18px; border-radius:10px; box-shadow:0 2px 8px rgba(0,0,0,0.06); text-align:left; }
    .login-title { margin:0 0 12px 0; color:#2c3e50; }
    .login-row { display:flex; flex-direction:column; gap:6px; margin-bottom:14px; }
    .login-input { padding:10px; border:1px solid #cfd8dc; border-radius:6px; font-size:15px; }
    .login-actions { display:flex; gap:10px; align-items:center; }
    .login-btn { padding:12px 16px; border:none; border-radius:8px; cursor:pointer; font-weight:600; }
    .login-primary { color:#fff; background: linear-gradient(180deg,#2b7cff,#1a5fe0); box-shadow:0 6px 18px rgba(26,95,224,0.18); }
    .login-error { background:#ffecec; border:1px solid #f5c2c2; color:#8a1f1f; padding:10px 12px; border-radius:8px; margin:10px 0 14px; }
  </style>
</head>
<body>
  <h2>PayACon</h2>
  <div class="login-wrap">
    <div class="login-card">
      <h3 class="login-title">Logowanie</h3>
      <% if (showError) { %>
        <div class="login-error">Nieprawidłowa nazwa użytkownika lub hasło.</div>
      <% } %>
      <form action="${pageContext.request.contextPath}/LoginServlet" method="post" accept-charset="UTF-8">
        <div class="login-row">
          <label class="field-label" for="username">Użytkownik</label>
          <input class="login-input" id="username" name="username" type="text" autocomplete="username" required>
        </div>
        <div class="login-row">
          <label class="field-label" for="password">Hasło</label>
          <input class="login-input" id="password" name="password" type="password" autocomplete="current-password" required>
        </div>
        <div class="login-actions">
          <button class="login-btn login-primary" type="submit">Zaloguj</button>
        </div>
      </form>
    </div>
  </div>
</body>
</html>

