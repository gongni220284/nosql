package qengine.program;

import fr.boreal.model.logicalElements.api.Substitution;
import fr.boreal.model.logicalElements.api.Term;
import fr.boreal.model.logicalElements.api.Variable;
import fr.boreal.model.logicalElements.factory.impl.SameObjectTermFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import qengine.model.RDFTriple;
import qengine.parser.RDFTriplesParser;
import qengine.storage.RDFHexaStore;
import qengine.storage.RDFGiantTable;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TestStorage {

    private static final String WORKING_DIR = "data/";
    private static final String SAMPLE_DATA_FILE = WORKING_DIR + "sample_data.nt";

    public static void main(String[] args) throws IOException {
        System.out.println("Reading RDF data from: " + SAMPLE_DATA_FILE);
        List<RDFTriple> rdfAtoms = parseRDFData(SAMPLE_DATA_FILE);

        RDFGiantTable giantTable = new RDFGiantTable();
        for (RDFTriple triple : rdfAtoms) giantTable.add(triple);
        System.out.println("RDFGiantTable total triples: " + giantTable.size());

        RDFHexaStore hexStore = new RDFHexaStore();
        for (RDFTriple triple : rdfAtoms) hexStore.add(triple);
        System.out.println("HexStore total triples: " + hexStore.size());

        Term s = createVariable("s");
        Term p = createVariable("p");
        Term o = createVariable("o");
        RDFTriple query = new RDFTriple(s, p, o);

        System.out.println("\n--- Query results for RDFGiantTable ---");
        Iterator<Substitution> giantResults = giantTable.match(query);
        int countGiant = 0;
        while (giantResults.hasNext()) {
            System.out.println(giantResults.next());
            countGiant++;
        }
        System.out.println("Total query results: " + countGiant);

        System.out.println("\n--- Query results for HexaStore ---");
        Iterator<Substitution> hexResults = hexStore.match(query);
        int countHex = 0;
        while (hexResults.hasNext()) {
            System.out.println(hexResults.next());
            countHex++;
        }
        System.out.println("Total query results: " + countHex);
    }

    private static List<RDFTriple> parseRDFData(String filePath) throws IOException {
        List<RDFTriple> rdfAtoms = new ArrayList<>();
        try (FileReader rdfFile = new FileReader(filePath);
             RDFTriplesParser parser = new RDFTriplesParser(rdfFile, RDFFormat.NTRIPLES)) {

            int count = 0;
            while (parser.hasNext()) {
                RDFTriple triple = parser.next();
                rdfAtoms.add(triple);
                System.out.println("RDF Triple #" + (++count) + ": " + triple);
            }
            System.out.println("Total RDF Triples parsed: " + count);
        }
        return rdfAtoms;
    }

    private static Variable createVariable(String name) {
        return SameObjectTermFactory.instance().createOrGetVariable(name);
    }

    private static Term createLiteral(String value) {
        return SameObjectTermFactory.instance().createOrGetLiteral(value);
    }
}
