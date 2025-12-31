<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
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
<h2>Wygenerowany XML</h2>
<ul>
<c:forEach var="r" items="${results}">
    <li>${r}</li>
</c:forEach>
</ul>
<a href="converter.jsp">Powrót</a>
</body>
</html>

