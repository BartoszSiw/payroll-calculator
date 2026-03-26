package pl.edashi.dms.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public interface XmlSectionBuilder {

	void build(Document docXml, Element root);
	default void setIdKsiegOddzial(String id) { /* opcjonalnie implementacja domyślna */ }
}
