<%@ page language="java" import="java.util.*" pageEncoding="UTF-8"%>
<%@ page import="pl.edashi.converter.service.DateFilterRegistry" %>
<%@ page import="java.time.LocalDate" %>
<%
    String from = request.getParameter("fromDate");
    String to   = request.getParameter("toDate");

    DateFilterRegistry dfr = DateFilterRegistry.getInstance();

    if (from != null && !from.isBlank()) {
        dfr.setFromDate(LocalDate.parse(from));
    } else {
        dfr.setFromDate(null);
    }

    if (to != null && !to.isBlank()) {
        dfr.setToDate(LocalDate.parse(to));
    } else {
        dfr.setToDate(null);
    }
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
<meta charset="UTF-8">
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
<form action="../ConverterServlet" method="post" enctype="multipart/form-data" class="conv-form" accept-charset="UTF-8">
<div class="two-columns">
<div class="column">
<div class="form-section">
    <label class="field-label"><strong>Filtrowanie po dacie wystawienia</strong></label>

    <div class="date-filter-group">
        <label for="fromDate">Data od:</label>
        <input type="date" id="fromDate" name="fromDate" class="date-input">

        <label for="toDate">Data do:</label>
        <input type="date" id="toDate" name="toDate" class="date-input">
    </div>

    <div class="field-help">
        Brak wybranych dat —> przetworzone zostaną wszystkie daty.
    </div>
</div>
</div>
<div class="column">
    <div class="form-section">
    <label for="xmlFile" class="field-label"><strong>Wybierz pliki XML</strong></label>
    <input type="file" id="xmlFile" name="xmlFile" accept=".xml" multiple class="file-input" aria-describedby="fileHelp">
    <div id="fileHelp" class="field-help">Możesz wybrać wiele plikow.</div>
    <div id="selectedFiles" class="selected-files" aria-live="polite" style="margin-top:8px;font-size:0.95em;color:#333;"></div>
  </div>
</div>
</div>
<c:if test="${not empty fullKey}">
    <div style="margin-top:10px;">
        <label for="allowUpdate">Zezwól na ponowne wczytanie wybranych dokumentów</label>
        <input type="hidden" name="allowUpdate" value="false">
        <input type="checkbox" id="allowUpdate" name="allowUpdate" value="true">
    </div>
    <div style="margin-top:12px;">
        <label for="scheduledTime">Uruchom jak o godzinie</label>
        <input type="time" id="scheduledTime" name="scheduledTime" value="00:00" class="time-input" />
          <label> Wait before manual run (ms): <input type="number" name="waitMillis" min="0" step="100" value="0" /></label>
        <!-- hidden field wysyłany tylko gdy użytkownik kliknie przycisk -->
        <input type="hidden" id="simulateScheduledRun" name="simulateScheduledRun" value="false" />
        <input type="checkbox" id="simulateScheduledRun" name="simulateScheduledRun" value="true" />
        <button type="submit" name="simulateScheduledRun" value="true" class="btn-secondary" style="margin-left:8px;">
            Uruchom jak przy harmonogramie
        </button>
        <div class="field-help" style="margin-top:6px;">
            Kliknij, aby wysłać żądanie do serwera z parametrem symulującym uruchomienie harmonogramu.
        </div>
    </div>
</c:if>
    <div class="form-section">
        <label for="rejestr" class="field-label"><strong>Wybierz rejestry</strong></label>
        <select name="rejestr" id="rejestr" multiple size="8" class="select-control" aria-describedby="rejestrHelp">
            <option value="" selected>Wszystkie</option>
            <%-- <% if (en001) { --%>
                <!-- <option value="001">001</option> -->
            <%-- <% } --%> 
            <option value="001"> 001 </option>
            <option value="002"> 002 </option>
			<option value="003"> 003 </option>
            <option value="040"> 040 </option>
            <option value="041"> 041 </option>
            <option value="070"> 070 </option>
            <option value="100"> 100 </option>
            <option value="101"> 101 </option>
            <option value="102"> 102 </option>
            <option value="110"> 110 </option>
            <option value="120"> 120 </option>
            <option value="121"> 121 </option>
            <option value="130"> 130 </option>
            <option value="132"> 132 </option>
            <option value="141"> 141 </option>
            <option value="143"> 143 </option>
            <option value="191"> 191 </option>
            <option value="200"> 200 </option>
            <option value="240"> 240 </option>
            <option value="290"> 290 </option>
            <option value="310"> 310 </option>
            <option value="320"> 320 </option>
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
            <option value="CC"> CC -> ZAKUP </option>
            <option value="OT"> OT -> ZAKUP </option>
            <option value="EX"> EX -> ZK </option>
            <option value="CU"> CU -> ZK </option>
            <option value="PA"> PA -> Z3 </option>
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
    <button type="submit" id="submitBtn" class="btn-primary">Konwertuj</button>
    <button type="reset" id="resetBtn" class="btn-secondary">Wyczysc</button>
  </div>
</form>
<script src="${pageContext.request.contextPath}main/webapp/script.js"></script>
</body>
</html>