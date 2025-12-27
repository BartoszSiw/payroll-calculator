package pl.edashi.dms.model;
import java.util.List;
public class DmsParsedDocument {
	public DocumentMetadata metadata;

    public Contractor contractor;
    public List<DmsPosition> positions;
    public List<DmsPayment> payments;
    public List<String> notes;

    public String vatRate;
    public String vatBase;
    public String vatAmount;

    public String fiscalNumber;
    public String fiscalDevice;
    public String fiscalDate;
    public String additionalDescription;
	public String documentType;		  // Pr, Fa
    public String invoiceNumber;      // 1/100/01/01102/2025
    public String invoiceShortNumber; // 1/100/01/01102/2025

}
