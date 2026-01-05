<%@ page language="java" import="java.util.*" pageEncoding="ISO-8859-1"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
    <meta http-equiv="pragma" content="no-cache">
	<meta http-equiv="cache-control" content="no-cache">
	<meta http-equiv="expires" content="0">    
	<meta http-equiv="keywords" content="keyword1,keyword2,keyword3">
	<meta http-equiv="description" content="This is my page">
    <title>XML Converter</title>
	<link rel="stylesheet" type="text/css" href="styles.css">
</head>
<body>
<jsp:include page="/menu.jsp" />
<h2>XML Converter Module</h2>
<form action="../ConverterServlet" method="post" enctype="multipart/form-data">
    <label>Wybierz pliki XML:</label><br>
    <input type="file" name="xmlFile" accept=".xml" multiple><br><br>
    <input type="submit" value="Konwertuj">
</form>
</body>
</html>