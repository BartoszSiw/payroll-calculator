package pl.edashi.converter.model;

import java.util.List;

public class DsParsedDocument {

    public DocumentMetadata metadata;

    public Contractor contractor;
    public List<DsPosition> positions;
    public List<DsPayment> payments;
    public List<String> notes;

    public String vatRate;
    public String vatBase;
    public String vatAmount;

    public String fiscalNumber;
    public String fiscalDevice;
    public String fiscalDate;
    public String additionalDescription;

}
