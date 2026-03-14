package pl.edashi.dms.model;

public class Rozliczenie {
    private String idZrodla;
    private String numerLewegoDokumentu;
    private String rowIdLewego;
    private String numerPrawegoDokumentu;
    private String rowIdPrawego;
    private String dataDokumentu; // yyyy-MM-dd
    private String typLewegoDokumentu;
    private String typPrawegoDokumentu;
    private String kwotaRk;
    private String opis;
    private String kontrahentNip;
    private String kontrahentNazwa;
    private String waluta;
    // dodatkowe pola pomocnicze do powiązań
    private String sourceReportNumber;
    private String sourcePositionLp;

    public Rozliczenie() {
        //this.idZrodla = UUID.randomUUID().toString();
    }

	public String getIdZrodla() {
		return idZrodla;
	}

	public void setIdZrodla(String idZrodla) {
		this.idZrodla = idZrodla;
	}

	public String getNumerLewegoDokumentu() {
		return numerLewegoDokumentu;
	}

	public void setNumerLewegoDokumentu(String numerLewegoDokumentu) {
		this.numerLewegoDokumentu = numerLewegoDokumentu;
	}

	public String getRowIdLewego() {
		return rowIdLewego;
	}

	public void setRowIdLewego(String rowIdLewego) {
		this.rowIdLewego = rowIdLewego;
	}

	public String getNumerPrawegoDokumentu() {
		return numerPrawegoDokumentu;
	}

	public void setNumerPrawegoDokumentu(String numerPrawegoDokumentu) {
		this.numerPrawegoDokumentu = numerPrawegoDokumentu;
	}

	public String getRowIdPrawego() {
		return rowIdPrawego;
	}

	public void setRowIdPrawego(String rowIdPrawego) {
		this.rowIdPrawego = rowIdPrawego;
	}

	public String getDataDokumentu() {
		return dataDokumentu;
	}

	public void setDataDokumentu(String dataDokumentu) {
		this.dataDokumentu = dataDokumentu;
	}

	public String getTypLewegoDokumentu() {
		return typLewegoDokumentu;
	}

	public void setTypLewegoDokumentu(String typLewegoDokumentu) {
		this.typLewegoDokumentu = typLewegoDokumentu;
	}

	public String getTypPrawegoDokumentu() {
		return typPrawegoDokumentu;
	}

	public void setTypPrawegoDokumentu(String typPrawegoDokumentu) {
		this.typPrawegoDokumentu = typPrawegoDokumentu;
	}

	public String getKwotaRk() {
		return kwotaRk;
	}

	public void setKwotaRk(String kwotaRk) {
		this.kwotaRk = kwotaRk;
	}

	public String getOpis() {
		return opis;
	}

	public void setOpis(String opis) {
		this.opis = opis;
	}

	public String getKontrahentNip() {
		return kontrahentNip;
	}

	public void setKontrahentNip(String kontrahentNip) {
		this.kontrahentNip = kontrahentNip;
	}

	public String getKontrahentNazwa() {
		return kontrahentNazwa;
	}

	public void setKontrahentNazwa(String kontrahentNazwa) {
		this.kontrahentNazwa = kontrahentNazwa;
	}

	public String getWaluta() {
		return waluta;
	}

	public void setWaluta(String waluta) {
		this.waluta = waluta;
	}

	public String getSourceReportNumber() {
		return sourceReportNumber;
	}

	public void setSourceReportNumber(String sourceReportNumber) {
		this.sourceReportNumber = sourceReportNumber;
	}

	public String getSourcePositionLp() {
		return sourcePositionLp;
	}

	public void setSourcePositionLp(String sourcePositionLp) {
		this.sourcePositionLp = sourcePositionLp;
	}

    // pełne gettery i settery (wygeneruj w IDE)
    // equals/hashCode oparte na idZrodla
}

