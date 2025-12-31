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


