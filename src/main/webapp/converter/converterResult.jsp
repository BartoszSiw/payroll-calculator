<%@ page contentType="text/html;charset=UTF-8"%>
<%
    String xml = (String) request.getAttribute("xml");
%>
<html>
<head>
    <title>Wynik konwersji</title>
    <style>
        pre {
            background: #f4f4f4;
            padding: 15px;
            border-radius: 5px;
            white-space: pre-wrap;
            font-family: Consolas, monospace;
        }
    </style>
</head>
<body>
<jsp:include page="/menu.jsp" />
<h2>Wygenerowany XML</h2>
<ul>
<%
    java.util.List<?> list = (java.util.List<?>) request.getAttribute("results");
    if (list != null) {
        for (Object r : list) {
%>
            <li><%= r %></li>
<%
        }
    }
%>
</ul>
</body>
</html>

