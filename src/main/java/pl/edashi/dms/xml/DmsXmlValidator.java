package pl.edashi.dms.xml;

import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;

public class DmsXmlValidator {

    public static void validate(String xml, String xsdPath) throws Exception {

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        try {
            var schema = factory.newSchema(new StreamSource(xsdPath));
            var validator = schema.newValidator();
            validator.validate(new StreamSource(new StringReader(xml)));

        } catch (SAXException e) {
            throw new Exception("XML nie przechodzi walidacji XSD: " + e.getMessage());
        }
    }
}
