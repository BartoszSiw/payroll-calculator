<%@ page contentType="text/html; charset=UTF-8" %>
<%
  String module = request.getParameter("module");
%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta http-equiv="pragma" content="no-cache">
  <meta http-equiv="cache-control" content="no-cache">
  <meta http-equiv="expires" content="0">
  <title>PayACon — Licencja</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/styles.css?v=1">
  <style>
    .wrap { max-width: 720px; margin: 28px auto; text-align:left; }
    .card { background:#f8f9fa; padding:18px; border-radius:10px; box-shadow:0 2px 8px rgba(0,0,0,0.06); }
    .title { margin:0 0 10px 0; color:#2c3e50; }
    .warn { background:#fff3cd; border:1px solid #ffe69c; color:#664d03; padding:12px; border-radius:8px; }
    .mono { font-family: Consolas, monospace; }
  </style>
</head>
<body>
  <h2>PayACon</h2>
  <div class="wrap">
    <div class="card">
      <h3 class="title">Brak dostępu — problem z licencją</h3>
      <div class="warn">
        <% if (module != null && !module.isBlank()) { %>
          Moduł <strong class="mono"><%=module%></strong> nie jest włączony w licencji albo licencja jest nieważna.
        <% } else { %>
          Licencja jest nieważna lub nie została poprawnie wgrana na serwer.
        <% } %>
      </div>
      <div style="margin-top:12px;" class="field-help">
        Skontaktuj się z administratorem / dostawcą systemu i wgraj poprawne pliki licencji.
      </div>
    </div>
  </div>
</body>
</html>

