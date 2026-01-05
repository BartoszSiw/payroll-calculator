package pl.edashi.dms.model;
import java.util.ArrayList;
import java.util.List;
public class DmsParsedDocument {
    private DocumentMetadata metadata;
    private Contractor contractor;
    private List<DmsPosition> positions = new ArrayList<>();
    private List<DmsPayment> payments = new ArrayList<>();
    private List<String> notes = new ArrayList<>();

    private String vatRate = "";
    private String vatBase = "";
    private String vatAmount = "";
    private String vatZ = "";

    private String fiscalNumber = "";
    private String fiscalDevice = "";
    private String fiscalDate = "";
    private String additionalDescription = "";
    private String invoiceNumber = "";
    private String invoiceShortNumber = "";
    private String documentType = ""; // ? Typ dokumentu czy podtyp ?
    private String typDocAnalizer = "";
    
    // dodatkowe pola pomocnicze
    private String sourceFileName = "";
    private String id = "";
    public DmsParsedDocument() { }
    // --- Settery używane przez parsery ---

    public void setMetadata(DocumentMetadata metadata) {
        this.metadata = metadata;
    }
	public void setDocumentType(String documentType) {
        this.documentType = documentType;	
	}
	public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName != null ? sourceFileName : "";
	}
	public void setId(String genDocId) {
        this.id = id != null ? id : "";
	}
	public void setContractor(Contractor contractor) {
        this.contractor = contractor;		
	}
	public void setAdditionalDescription(String string) {
        this.additionalDescription = additionalDescription != null ? additionalDescription : "";		
	}
	public void setVatRate(String string) {
        this.vatRate = vatRate != null ? vatRate : "";		
	}
	public void setVatBase(String string) {
        this.vatBase = vatBase != null ? vatBase : "";		
	}
	public void setVatAmount(String string) {
        this.vatAmount = vatAmount != null ? vatAmount : "";		
	}
    public void setVatZ(String string) {
        this.vatZ = vatZ != null ? vatZ : "";
    }
	public void setFiscalNumber(String string) {
        this.fiscalNumber = fiscalNumber != null ? fiscalNumber : "";		
	}
	public void setFiscalDevice(String string) {
        this.fiscalDevice = fiscalDevice != null ? fiscalDevice : "";		
	}
	public void setFiscalDate(String string) {
        this.fiscalDate = fiscalDate != null ? fiscalDate : "";		
	}
    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public void setInvoiceShortNumber(String invoiceShortNumber) {
        this.invoiceShortNumber = invoiceShortNumber;
    }
	public void setTypDocAnalizer(String typDocAnalizer) {
		this.typDocAnalizer = typDocAnalizer;
		
	}

    // --- Gettery ---
    public DocumentMetadata getMetadata() {
        return metadata;
    }

    public Contractor getContractor() {
        return contractor;
    }

    public String getVatRate() {
        return vatRate;
    }

    public String getVatBase() {
        return vatBase;
    }

    public String getVatAmount() {
        return vatAmount;
    }

    public String getVatZ() {
        return vatZ;
    }

    public String getFiscalNumber() {
        return fiscalNumber;
    }

    public String getFiscalDevice() {
        return fiscalDevice;
    }

    public String getFiscalDate() {
        return fiscalDate;
    }

    public String getAdditionalDescription() {
        return additionalDescription;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getInvoiceShortNumber() {
        return invoiceShortNumber;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public String getId() {
        return id;
    }
	public String getTypDocAnalizer() {
		return typDocAnalizer;
	}
    

    // --- Utility ---

    @Override
    public String toString() {
        return "DmsParsedDocument{" +
                "id='" + id + '\'' +
                ", sourceFileName='" + sourceFileName + '\'' +
                ", documentType='" + documentType + '\'' +
                ", invoiceNumber='" + invoiceNumber + '\'' +
                ", contractor=" + (contractor != null ? contractor.getName1() : "null") +
                ", payments=" + payments.size() +
                ", positions=" + positions.size() +
                '}';
    }
 // --- Gettery i settery dla list ---
    public List<DmsPosition> getPositions() {
        if (positions == null) positions = new ArrayList<>();
        return positions;
    }

    public void setPositions(List<DmsPosition> positions) {
        this.positions = positions != null ? positions : new ArrayList<>();
    }

    public List<DmsPayment> getPayments() {
        if (payments == null) payments = new ArrayList<>();
        return payments;
    }

    public void setPayments(List<DmsPayment> payments) {
        this.payments = payments != null ? payments : new ArrayList<>();
    }

    public List<String> getNotes() {
        if (notes == null) notes = new ArrayList<>();
        return notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes != null ? notes : new ArrayList<>();
    }
    /*public class DmsPositionDS {
        private String klasyfikacja;
        private String numer;

        public String getKlasyfikacja() { return klasyfikacja; }
        public void setKlasyfikacja(String k) { this.klasyfikacja = k; }

        public String getNumer() { return numer; }
        public void setNumer(String n) { this.numer = n; }
    }*/


}
