package pl.edashi.converter.service;

import pl.edashi.dms.parser.DmsParser;
import pl.edashi.dms.parser.DmsParserDK;
import pl.edashi.dms.parser.DmsParserDS;
import pl.edashi.dms.parser.DmsParserDZ;
import pl.edashi.dms.parser.DmsParserKO;
import pl.edashi.dms.parser.DmsParserKZ;
import pl.edashi.dms.parser.DmsParserRD;

public class DmsParserFactory {

    public static DmsParser getParser(String type) {
        if (type == null) {
            throw new IllegalArgumentException("Brak typu dokumentu");
        }

        switch (type.trim().toUpperCase()) {
            case "DS": return new DmsParserDS();
            case "DZ": return new DmsParserDZ();
            case "KO": return new DmsParserKO();
            case "KZ": return new DmsParserKZ();
            case "DK": return new DmsParserDK();
            case "RD": return new DmsParserRD();             
            default:
                throw new IllegalArgumentException("Nieobsługiwany typ dokumentu: " + type);
        }
    }
}

