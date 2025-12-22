package pl.edashi.dms.model;

public class DocumentMetadata {
    private final String genDocId;   // gen_doc_id
    private final String id;         // id="DS"/"KO"/"DK"
    private final String trans;      // trans="..."
    private final String sourceFile; // nazwa pliku źródłowego

    private final String date;          // data wystawienia
    private final String dateSale;      // data sprzedaży
    private final String dateApproval;  // data zatwierdzenia
    
    public DocumentMetadata(String genDocId, String id, String trans, String sourceFile, String date, String dateSale, String dateApproval) {
        this.genDocId = genDocId;
        this.id = id;
        this.trans = trans;
        this.sourceFile = sourceFile;
        this.date = date;
        this.dateSale = dateSale;
        this.dateApproval = dateApproval;
    }

    public String getGenDocId() { return genDocId; }
    public String getId() { return id; }
    public String getTrans() { return trans; }
    public String getSourceFile() { return sourceFile; }
    public String getDate() { return date; }
    public String getDateSale() { return dateSale; }
    public String getDateApproval() { return dateApproval; }
}
