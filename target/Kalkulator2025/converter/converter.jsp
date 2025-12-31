<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title>XML Converter</title>
</head>
<body>

<h2>XML Converter Module</h2>

<form action="../ConverterServlet" method="post" enctype="multipart/form-data">
    <label>Wybierz pliki XML:</label><br>
    <input type="file" name="xmlFile" accept=".xml" multiple><br><br>
    <input type="submit" value="Konwertuj">
</form>

</body>
</html>

