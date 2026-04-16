package pl.edashi.dms.model;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class DmsParsedDocument {
    private DocumentMetadata metadata;
    private Contractor contractor;
    private List<DmsPosition> positions = new ArrayList<>();
    private List<DmsPayment> payments = new ArrayList<>();
    private List<DmsRapKasa> rapKasa = new ArrayList<>();
    private List<String> notes = new ArrayList<>();
    private List<DmsVatEntry> vatEntries = new ArrayList<>();
    public List<DmsVatEntry> getVatEntries() { return vatEntries; }
    public void addVatEntry(DmsVatEntry e) { vatEntries.add(e); }
    
    private Map<String, String> vatRates = new HashMap<>();
    public Map<String, String> getVatRates() { return vatRates; }
    public void setVatRates(Map<String, String> map) { this.vatRates = map; }
    
	public List<DmsNetBruEntry> netBruEntries = new ArrayList<>();
    public List<DmsNetBruEntry> getNetBruEntries() { return netBruEntries; }
    public void addNetBruEntry(DmsNetBruEntry e) { netBruEntries.add(e); }
    private Map<String, String> netBruRates = new HashMap<>();
    public Map<String, String> getNetBruRates() { return netBruRates; }
    public void setNetBruRates(Map<String, String> map) { this.netBruRates = map; }
    private MappingTarget mappingTarget = MappingTarget.PURCHASES;

    private String vatRate = "";
    private String vatBase = "";
    private String vatAmount = "";
    private String vatZ = "";
    private boolean advance = false;
    private String advanceNet = "";
    private String advanceVat = "";
    private String kwotaRk = "";
    private String nrKsef = "";
    private String fiscalNumber = "";
    private String reportNumber = "";
    private String reportNumberPos = "";
    private String fiscalDevice = "";
    private String fiscalDate = "";
    private String reportDate = "";
    private String dataOtwarcia = "";
    private String dataZamkniecia = "";
    private String nrRKB = "";
    private String nrRep = "";
    private String lp = "";
    private String additionalDescription = "";
    private String invoiceNumber = "";
    private String invoiceShortNumber = "";
    private String documentType = ""; // ? Typ dokumentu czy podtyp ?
    private String documentWewne = "";
    private String typDocAnalizer = "";
    private String daneRejestr = "";
    private String oddzial = "";
    private String opis1 = "";
    private String kierunek = "";; // "przychód" albo "rozchód" albo "korekta"
	public String korekta = "";
	public String korektaNumer = "";
    private String dokumentFiskalny = "";
    private String dokumentDetaliczny = "";
    private String terminPlatnosci = "";
    private String kasa = "";
    private String dzial = "";
    private String transId = "";
    private String dowodNumber = "";
    private String dataWystawienia = "";
    private String nrDokumentu = "";
    private String nrIdPlat = "";
    private String fullKey = "";
    private String docKey = "";
    private String hash = "";
    private String podmiot = "";
    private String statusVat = "";
    public String odliczenia ="";
    public String expKrajowy = "";
    public String dodatkowyOpis ="";
    public String nrBank = "";
    //private String vin = "";

    // dodatkowe pola pomocnicze
    private String sourceFileName = "";
    private String id = "";
    private boolean hasVatDocument = false;
    private String uwzglProp = "";
    public DmsParsedDocument() { }
    // --- Settery używane przez parsery ---
    public void setAdvance(boolean advance) { this.advance = advance; }
    public void setNrKsef(String nrKsef) {this.nrKsef = nrKsef; }
    public void setMetadata(DocumentMetadata metadata) {
        this.metadata = metadata;
    }
	public void setDocumentType(String documentType) {
        this.documentType = documentType;	
	}
	public void setDocumentWewne(String documentWewne) {
        this.documentWewne = documentWewne;	
	}
	public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName != null ? sourceFileName : "";
	}
	public void setId(String genDocId) {
        this.id = genDocId != null ? genDocId : "";
	}

	public void setContractor(Contractor contractor) {
        this.contractor = contractor;		
	}
	public void setAdditionalDescription(String additionalDescription) {
        this.additionalDescription = additionalDescription != null ? additionalDescription : "";		
	}
	public void setVatRate(String vatRate) {
        this.vatRate = vatRate != null ? vatRate : "";		
	}
	public void setVatBase(String vatBase) {
        this.vatBase = vatBase != null ? vatBase : "";		
	}
	public void setVatAmount(String vatAmount) {
        this.vatAmount = vatAmount != null ? vatAmount : "";		
	}
    public void setVatZ(String vatZ) {
        this.vatZ = vatZ != null ? vatZ : "";}
    
	public void setAdvanceNet(String advanceNet) {
		this.advanceNet = advanceNet != null ? advanceNet : "";}
	public void setAdvanceVat(String advanceVat) {
		this.advanceVat = advanceVat != null ? advanceVat : "";}
	public void setFiscalNumber(String fiscalNumber) {
        this.fiscalNumber = fiscalNumber != null ? fiscalNumber : "";		
	}
	public void setFiscalDevice(String fiscalDevice) {
        this.fiscalDevice = fiscalDevice != null ? fiscalDevice : "";		
	}
	public void setFiscalDate(String fiscalDate) {
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
	public void setUwzglProp(String uwzglProp) {
        this.uwzglProp = uwzglProp;	
	}
	public void setReportDate(String reportDate) {this.reportDate = reportDate != null ? reportDate : "";}
    public void setReportNumber(String reportNumber) {this.reportNumber = reportNumber; }
    public void setNrRKB(String nrRKB) {this.nrRKB = nrRKB; }
    public void setNrRep(String nrRep) {this.nrRep = nrRep; }
    public void setLp(String lp) {this.lp = lp; }
    public void setReportNumberPos(String reportNumberPos) {this.reportNumberPos = reportNumberPos; }
    public void setDaneRejestr(String daneRejestr) { this.daneRejestr = daneRejestr == null ? "" : daneRejestr.trim(); }
    public void setOddzial(String oddzial) { this.oddzial = oddzial == null ? "" : oddzial.trim(); }
    public void setOpis1(String opis1) { this.opis1 = opis1 == null ? "" : opis1.trim(); }
    public void setHasVatDocument(boolean hasVatDocument) { this.hasVatDocument = hasVatDocument; }
    public void setKierunek(String kierunek) { this.kierunek = kierunek; }
	public void setDokumentFiskalny(String dokumentFiskalny) { this.dokumentFiskalny = dokumentFiskalny ;} 
	public void setDokumentDetaliczny (String dokumentDetaliczny ) { this.dokumentDetaliczny  = dokumentDetaliczny  ;}
	public void setKorekta(String korekta) {this.korekta  = korekta ;}
	public void setKorektaNumer(String korektaNumer) {this.korektaNumer = korektaNumer;	}
	public void setTerminPlatnosci(String terminPlatnosci) { this.terminPlatnosci = terminPlatnosci;}
	public void setKasa(String kasa) { this.kasa = kasa;}
	public void setKwotaRk(String kwotaRk) { this.kwotaRk = kwotaRk;}
	public void setDzial(String dzial) { this.dzial = dzial;}
	public void setTransId(String transId) { this.transId = transId;}
    public void setDataOtwarcia(String dataOtwarcia) { this.dataOtwarcia = safe(dataOtwarcia); }
    public void setDataZamkniecia(String dataZamkniecia) { this.dataZamkniecia = safe(dataZamkniecia); }
	public void setDowodNumber(String dowodNumber) {this.dowodNumber = safe(dowodNumber); }
	public void setDataWystawienia(String dataWystawienia) {this.dataWystawienia = safe(dataWystawienia); }
	public void setNrDokumentu(String nrDokumentu) { this.nrDokumentu = safe(nrDokumentu); }
	public void setNrIdPlat(String nrIdPlat) {	 this.nrIdPlat = safe(nrIdPlat); }
	public void setFullKey(String fullKey) {this.fullKey = safe(fullKey);	}
	public void setDocKey(String docKey) {this.docKey = safe(docKey);	}
	public void setHash(String hash) {this.hash = safe(hash);	}
	public void setPodmiot(String podmiot) {this.podmiot = safe(podmiot);	}
	public void setStatusVat(String statusVat) { this.statusVat = safe(statusVat); }
	public void setOdliczenia(String odliczenia) { this.odliczenia = safe(odliczenia); }
	public void setExpKrajowy(String expKrajowy) { this.expKrajowy = safe(expKrajowy); }
	public void setDodatkowyOpis(String dodatkowyOpis) {this.dodatkowyOpis = safe(dodatkowyOpis); }
	public void setNrBank(String nrBank) {this.nrBank = safe(nrBank);}
	public void setMappingTarget(MappingTarget mappingTarget) { this.mappingTarget = mappingTarget; }
	
	//public void setVin(String vin) {this.vin  = vin ;}
    // --- Gettery ---
    public String getNrKsef() {
        return nrKsef;
    }
    public DocumentMetadata getMetadata() { return metadata; }

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

    public String getAdvanceNet() {
        return advanceNet;
    }
    
    public String getAdvanceVat() {
        return advanceVat;
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
    
    public String getDocumentWewne() {
        return documentWewne;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getInvoiceShortNumber() {
        return invoiceShortNumber;
    }
    
    public String getReportNumber() {
        return reportNumber;
    }
    
    public String getNrRKB() {
        return nrRKB;
    }
    
    public String getNrRep() {
        return nrRep;
    }
    
    public String getLp() {
        return lp;
    }
    
    public String getReportNumberPos() {
        return reportNumberPos;
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
    public String getReportDate() {return reportDate; }
    public String getDaneRejestr() { return daneRejestr; }
    public String getOddzial() { return oddzial; }
    public String getOpis1() { return opis1; }
    public boolean isHasVatDocument() { return hasVatDocument; }
    public boolean isAdvance() { return advance; }
    public String getUwzglProp() { return uwzglProp; }
    public String getKierunek() { return kierunek; }
    public String getDokumentFiskalny() {return dokumentFiskalny;}
    public String getDokumentDetaliczny() {return dokumentDetaliczny;}
    public String getKorekta() {return korekta;}
    public String getKorektaNumer() {return korektaNumer;}
    public String getTerminPlatnosci() {return terminPlatnosci;}
    public String getKasa() {return kasa;}
    public String getKwotaRk() {return kwotaRk;}
    public String getDzial() {return dzial;}
	public String getTransId() {return transId;}
    public String getDataOtwarcia() { return dataOtwarcia; }  
    public String getDataZamkniecia() { return dataZamkniecia; }
    public String getDowodNumber() { return dowodNumber;}
    public String getDataWystawienia() { return dataWystawienia;}
    public String getNrDokumentu() { return nrDokumentu;}
    public String getNrIdPlat() { return nrIdPlat;}
    public String getFullKey() { return fullKey;}
    public String getDocKey() { return docKey;}
    public String getHash() { return hash;}
    public String getPodmiot() { return podmiot;}
    public String getStatusVat() {return statusVat;}
    public String getOdliczenia() { return odliczenia; }
    public String getExpKrajowy() { return expKrajowy; }
    public String getDodatkowyOpis() {return dodatkowyOpis;}
    public String getNrBank() {return nrBank;}
    public MappingTarget getMappingTarget() { return mappingTarget; }
	    //public String getVin() {return vin;}
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
                ", kierunek='" + kierunek + '\'' +
                '}';
    }
 // --- Gettery i settery dla list ---
    public List<DmsPosition> getPositions() {
        if (positions == null) {
            positions = new ArrayList<>();
        }
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
    public List<DmsRapKasa> getRapKasa() {
        if (rapKasa == null) {
            rapKasa = new ArrayList<>();
        }
        return rapKasa;
    }

    public void setRapKasa(List<DmsRapKasa> rapKasa) {
        this.rapKasa = rapKasa != null ? rapKasa : new ArrayList<>();
    }
    public static class DmsVatEntry {
        public String stawka;
        public String podstawa;
        public String vat;
        public String statusVat;
        public BigDecimal remainingPodstawa;
        public BigDecimal podstawaParsed; // parsed original podstawa from extractVat
        public boolean consumed = false;  // mark when fully used

    
    public void initRemaining() {
        this.remainingPodstawa = parseAmount(this.podstawa);
        if (this.remainingPodstawa == null) this.remainingPodstawa = BigDecimal.ZERO;
    }
    }
    public static class DmsNetBruEntry{
    	public String base;
        public String netto;
        public String brutto;
    }
    // --- Helper ---
    private static String safe(String s) {
        return s == null ? "" : s;
    }
    private static BigDecimal parseAmount(String s) {
        if (s == null) return null;
        try {
            String cleaned = s.trim().replace(",", ".").replaceAll("\\s+", "");
            if (cleaned.isEmpty()) return null;
            return new BigDecimal(cleaned);
        } catch (Exception ex) {
            return null;
        }
    }
 // new field
    private final List<DmsPosition> pendingExtras = new ArrayList<>();

    // helper
    public void addPendingExtra(DmsPosition p) {
        if (p == null) return;
        this.pendingExtras.add(p);
    }

    public List<DmsPosition> drainPendingExtras() {
        List<DmsPosition> copy = new ArrayList<>(this.pendingExtras);
        this.pendingExtras.clear();
        return copy;
    }


}
