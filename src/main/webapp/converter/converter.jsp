<%@ page language="java" import="java.util.*" pageEncoding="ISO-8859-1"%>
<%-- <%
    String enabledStr = (String) request.getAttribute("enabledStr");
    if (enabledStr == null) enabledStr = "";
     boolean en001 = enabledStr.contains("001");
    boolean enCD  = enabledStr.contains("CD");
    boolean enCP  = enabledStr.contains("CP");
    boolean enCR  = enabledStr.contains("CR");
%> --%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
    <meta http-equiv="pragma" content="no-cache">
	<meta http-equiv="cache-control" content="no-cache">
	<meta http-equiv="expires" content="0">    
	<meta http-equiv="keywords" content="keyword1,keyword2,keyword3">
	<meta http-equiv="description" content="This is my page">
    <title>XML Converter</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/styles.css?v=1">
</head>
<body>
<h2>Konwerter XML AUTO TIM</h2>
<jsp:include page="/menu.jsp" />
<form action="../ConverterServlet" method="post" enctype="multipart/form-data" class="conv-form">
    <div class="form-section">
        <label for="rejestr" class="field-label"><strong>Wybierz rejestry</strong></label>
        <select name="rejestr" id="rejestr" multiple size="8" class="select-control" aria-describedby="rejestrHelp">
            <option value="">Wszystkie</option>
            <%-- <% if (en001) { --%>
                <!-- <option value="001">001</option> -->
            <%-- <% } --%> 
            <option value="001"> 001 </option>
            <option value="040"> 040 </option>
            <option value="041"> 041 </option>
            <option value="070"> 070 </option>
            <option value="100"> 100 </option>
            <option value="101"> 101 </option>
            <option value="102"> 102 </option>
            <option value="110"> 110 </option>
            <option value="120"> 120 </option>
            <option value="121"> 121 </option>
            <option value="141"> 141 </option>
            <option value="143"> 143 </option>
            <option value="191"> 191 </option>
            <option value="200"> 200 </option>
            <option value="240"> 240 </option>
            <option value="290"> 290 </option>
            <option value="310"> 310 </option>
            <option value="330"> 330 </option>
            <option value="400"> 400 </option>
                        <!-- Z1: pokaz tylko jesli ktorys z CD/CP/CR jest enabled -->
            <%-- <% if (enCD || enCP || enCR) { %>
               <option value="CD">Z1 (CD)</option>
                <option value="CP">Z1 (CP)</option>
                <option value="CR">Z1 (CR)</option>
            <% } else { %>
               <option value="Z1" disabled title="Z1 tymczasowo wylaczone">Z1 (wylaczone)</option>
            <% } %>  --%>
            <option value="CC"> ZAKUP </option>
            <option value="EX"> EX -> ZK </option>
            <option value="PA"> PA -> Z4 </option>
            <option value="CD"> CD -> Z1 </option>
            <option value="CP"> CP -> Z1 </option>
            <option value="CR"> CR -> Z1 </option>
        </select>
        <div id="rejestrHelp" class="field-help">Przytrzymaj Ctrl (Cmd) aby wybrac wiele rejestrow.</div>
    </div>
    <div class="form-section">
    <label class="field-label"><strong>Wybierz oddzial</strong></label>
    <div class="radio-group" role="radiogroup" aria-label="Oddzial">
      <label class="radio-item"><input type="radio" name="oddzial" value="01" checked> Oddzial 01</label>
      <label class="radio-item"><input type="radio" name="oddzial" value="02"> Oddzial 02</label>
    </div>
  </div>
    <div class="form-section">
    <label for="xmlFile" class="field-label"><strong>Wybierz pliki XML</strong></label>
    <input type="file" id="xmlFile" name="xmlFile" accept=".xml" multiple class="file-input" aria-describedby="fileHelp">
    <div id="fileHelp" class="field-help">Mozesz wybrac wiele plikow. Maksymalny rozmiar zalezy od ustawien serwera.</div>
    <div id="selectedFiles" class="selected-files" aria-live="polite" style="margin-top:8px;font-size:0.95em;color:#333;"></div>
  </div>
  <div class="form-section">
    <button type="submit" id="submitBtn" class="btn-primary">Konwertuj</button>
    <button type="reset" id="resetBtn" class="btn-secondary">Wyczysc</button>
  </div>
</form>
<script src="${pageContext.request.contextPath}main/webapp/script.js"></script>
</body>
</html>