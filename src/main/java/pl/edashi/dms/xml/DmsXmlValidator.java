package pl.edashi.dms.xml;
import java.io.StringReader;
import java.io.FileNotFoundException;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

public class DmsXmlValidator {

    public static void validate(String xml, String xsdPath) throws Exception {

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        URL xsdUrl = DmsXmlValidator.class.getClassLoader().getResource(xsdPath);
        if (xsdUrl == null) {
            throw new FileNotFoundException("Nie znaleziono XSD: " + xsdPath);
        }

        Schema schema = factory.newSchema(xsdUrl);
        Validator validator = schema.newValidator();

        try {
            validator.validate(new StreamSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new Exception("XML nie przechodzi walidacji XSD: " + e.getMessage(), e);
        }
    }
}

