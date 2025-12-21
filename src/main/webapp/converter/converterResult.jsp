<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="pl.edashi.converter.model.ConversionResult" %>

<%
    ConversionResult result = (ConversionResult) request.getAttribute("result");
%>

<html>
<head>
    <title>Wynik konwersji</title>
</head>
<body>

<h2>Wynik konwersji dokumentu</h2>

<p><strong>ID dokumentu:</strong> <%= result.getMetadata().getGenDocId() %></p>
<p><strong>Status:</strong> <%= result.getStatus() %></p>
<p><strong>Komunikat:</strong> <%= result.getMessage() %></p>

<h3>Wynikowy XML:</h3>
<pre style="background:#f0f0f0; padding:10px; border:1px solid #ccc;">
<%= result.getConvertedXml() == null ? "Brak (konflikt)" : result.getConvertedXml() %>
</pre>

<a href="../converter/converter.jsp">Powrót</a>

</body>
</html>
