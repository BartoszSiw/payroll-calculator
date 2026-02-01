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
<form action="../ConverterServlet" method="post" enctype="multipart/form-data" class="conv-form">

    <div class="form-section">
        <label for="rejestr">Wybierz rejestry:</label>
        <select name="rejestr" id="rejestr" multiple size="8" class="select-control">
            <option value="">Wszystkie</option>
            <option value="001">001</option>
            <option value="040">040</option>
            <option value="041">041</option>
            <option value="070">Rejestr 070</option>
            <option value="100">Rejestr 100</option>
            <option value="101">Rejestr 101</option>
            <option value="102">Rejestr 102</option>
            <option value="110">Rejestr 110</option>
            <option value="120">Rejestr 120</option>
            <option value="121">Rejestr 121</option>
            <option value="141">Rejestr 141</option>
            <option value="143">Rejestr 143</option>
            <option value="191">Rejestr 191</option>
            <option value="200">Rejestr 200</option>
            <option value="240">Rejestr 240</option>
            <option value="290">Rejestr 290</option>
            <option value="310">Rejestr 310</option>
            <option value="330">Rejestr 330</option>
            <option value="400">Rejestr 400</option>
            <option value="CC">Rejestr ZAKUP</option>
            <option value="EX">ZK</option>
            <option value="PA">Z4</option>
            <option value="CD">Z1</option>
            <option value="CP">Z1</option>
            <option value="CR">Z1</option>
        </select>
    </div>
      <div class="form-section">
    <label>Wybierz oddzial:</label>
    <div class="radio-group">
      <label class="radio-item"><input type="radio" name="oddzial" value="01" checked> Oddzial 01</label>
      <label class="radio-item"><input type="radio" name="oddzial" value="02"> Oddzial 02</label>
    </div>
  </div>
    <label>Wybierz pliki XML:</label><br>
    <input type="file" name="xmlFile" accept=".xml" multiple class="file-input"><br><br>
    <input type="submit" value="Konwertuj" class="btn-primary">
</form>
</body>
</html>