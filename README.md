# Payroll Calculator / DMS XML Processing Pipeline

This project implements a modular XML processing pipeline designed for converting
DS documents into Optima‑compatible XML output.  
The architecture is clean, layered, and easy to extend for additional DMS types.

## 🔄 Processing Flow

DS XML  
→ **DmsParserDS**  
→ **DmsParsedDocument**  
→ **Mapper**  
→ **DmsDocumentOut**  
→ **Builder**  
→ **Optima‑compatible XML**
Parser wypełnia DmsParsedDocument, mapper mapuje do DmsDocumentOut, builder czyta TYLKO DmsDocumentOut.

🔥 DLACZEGO TERMIN_PLAT jest pusty?
Bo:

1. Parser → ustawia p.setTerminPlatnosci(...)
2. DmsParsedDocument → trzyma listę payments
3. Mapper → kopiuje payments do DmsDocumentOut
4. DmsDocumentOut → NIE MA pola terminPlatnosci
5. Builder → wywołuje p.getTerminPlatnosci() na obiekcie z DmsDocumentOut

✔️ DmsFieldMapper → mapuje pola pozycji
✔️ DmsToDmsParser → mapuje pola dokumentu
✔️ DocumentOutPositions → generuje pozycje do XML
✔️ Builder → generuje finalny XML

### 1. Input: DS XML
Raw DS document received from external systems.

### 2. DmsParserDS
Parses the incoming XML into a structured internal model.

### 3. DmsParsedDocument
Unified representation of the parsed DS document.  
This layer normalizes data and prepares it for mapping.

### 4. Mapper
Transforms `DmsParsedDocument` into the output model.  
Responsible for:
- field mapping  
- value conversions  
- business logic  
- validation  

### 5. DmsDocumentOut
Clean output model ready for XML generation.

### 6. Builder
Generates final XML using:
- DOM  
- builder pattern  
- XSD‑compliant structure  

### 7. Output: XML for Optima
Final XML document ready for import into Comarch Optima.

### 8. Tomcat11 install
- install and create:
- C:\Tomcat11\bin
- create file setenv.bat and paste line below
- set CATALINA_OPTS=-Dorg.apache.tomcat.util.http.fileupload.fileCountMax=-1
- deploy WAR for prod client no payroll mvn clean package -P converter-client -DskipTests
- deploy WAR for prod client no payroll logs mvn -Pconverter-client -DskipTests package
- deploy WAR for prod mvn clean package -P prod -DskipTests
- deploy default WAR for dev mvn clean package -DskipTests

## 🧱 Project Structure

## 🚀 Development Notes

- Designed for extensibility (DS → DK → KO → KZ → WZ → PZ)
- Builder is fully XSD‑compliant
- Clear separation of concerns
- Easy to plug in new document types

## 🛠 Technologies

- Java  
- JSP / Servlet  
- DOM XML  
- Maven  
- MyEclipse  

## 📌 Git Workflow Notes

If you need to amend the last commit:


