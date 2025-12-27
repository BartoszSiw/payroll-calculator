package pl.edashi.dms.service;
import pl.edashi.dms.model.DmsParsedContractor; import java.text.Normalizer; import java.util.*;
public class ContractorDeduplicator {

	    public List<DmsParsedContractor> deduplicatePersons(List<DmsParsedContractor> input) {

	        Map<String, DmsParsedContractor> map = new LinkedHashMap<>();

	        for (DmsParsedContractor c : input) {
	        	String key;
	            if (c.isCompany) {
	            	if (c.nip != null && !c.nip.isEmpty()) {
	                    key = "NIP:" + normalize(c.nip);
	            }
	            	else {
	                    key = "FIRMA:" + normalize(c.fullName) + "|" +
	                            normalize(c.kodPocztowy) + "|" +
	                            normalize(c.miasto);
	                }
	            } else {
	                key = "OSOBA:" + normalize(c.fullName) + "|" +
	                        normalize(c.kodPocztowy) + "|" +
	                        normalize(c.miasto);
	            }
	            if (!map.containsKey(key)) {
	                map.put(key, c);
	            } 
	        }

	        return new ArrayList<>(map.values());
	    }

	    private String normalize(String s) {
	        if (s == null) return "";
	        String out = s.trim().toLowerCase();
	        out = Normalizer.normalize(out, Normalizer.Form.NFD)
	                .replaceAll("\\p{M}", ""); // usuwamy polskie ogonki
	        out = out.replaceAll("\\s+", " "); // redukujemy spacje
	        return out;
	    }
	}
