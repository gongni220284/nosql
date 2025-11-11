package qengine.storage;

import fr.boreal.model.logicalElements.api.*;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;
import org.apache.commons.lang3.NotImplementedException;
import qengine.model.RDFTriple;
import qengine.model.StarQuery;


import java.util.*;

/**
 * Implémentation Giant-Table : stockage simple sans index.
 * Baseline pour comparer les performances avec HexaStore.
 */
public class RDFGiantTable implements RDFStorage {
    
    //dictionaire
    private Map<String, Integer> stringToInt = new HashMap<>();
    private Map<Integer, String> intToString = new HashMap<>();
    private int nextId = 0;
    
    private int encode(String str) {
        if (!stringToInt.containsKey(str)) {
            stringToInt.put(str, nextId);
            intToString.put(nextId, str);
            nextId++;
        }
        return stringToInt.get(str);
    }
    
    private String decode(int id) {
        return intToString.get(id);
    }
    

    private List<int[]> encodedTriples = new ArrayList<>();
    private Set<RDFTriple> allTriples = new HashSet<>();
    

    @Override
    public boolean add(RDFTriple triple) {
        if (allTriples.contains(triple)) {
            return false;
        }
        
        int s = encode(triple.getTripleSubject().toString());
        int p = encode(triple.getTriplePredicate().toString());
        int o = encode(triple.getTripleObject().toString());
        
        encodedTriples.add(new int[]{s, p, o});
        allTriples.add(triple);
        
        return true;
    }
    
   
    @Override
    public long size() {
        return allTriples.size();
    }
    
 
    @Override
    public Iterator<Substitution> match(RDFTriple triple) {
        List<Substitution> results = new ArrayList<>();
        
        Term subjectTerm = triple.getTripleSubject();
        Term predicateTerm = triple.getTriplePredicate();
        Term objectTerm = triple.getTripleObject();
        
        boolean sIsVar = subjectTerm.isVariable();
        boolean pIsVar = predicateTerm.isVariable();
        boolean oIsVar = objectTerm.isVariable();
        
        Integer sEncoded = sIsVar ? null : encode(subjectTerm.toString());
        Integer pEncoded = pIsVar ? null : encode(predicateTerm.toString());
        Integer oEncoded = oIsVar ? null : encode(objectTerm.toString());
        
        for (int[] encodedTriple : encodedTriples) {
            int s = encodedTriple[0];
            int p = encodedTriple[1];
            int o = encodedTriple[2];
            
            boolean matches = true;
            if (!sIsVar && !sEncoded.equals(s)) matches = false;
            if (!pIsVar && !pEncoded.equals(p)) matches = false;
            if (!oIsVar && !oEncoded.equals(o)) matches = false;
            
            if (matches) {
                Substitution sub = new SubstitutionImpl();
                if (sIsVar) sub.add((Variable) subjectTerm, createLiteral(decode(s)));
                if (pIsVar) sub.add((Variable) predicateTerm, createLiteral(decode(p)));
                if (oIsVar) sub.add((Variable) objectTerm, createLiteral(decode(o)));
                results.add(sub);
            }
        }
        
        return results.iterator();
    }

    @Override
    public Iterator<Substitution> match(StarQuery q) {
        throw new NotImplementedException();
    }
    
    @Override
    public long howMany(RDFTriple triple) {
        long count = 0;
        Iterator<Substitution> matches = match(triple);
        while (matches.hasNext()) {
            matches.next();
            count++;
        }
        return count;
    }
    
    @Override
    public Collection<RDFTriple> getAtoms() {
        return new ArrayList<>(allTriples);
    }
    

    private Term createLiteral(String value) {
        return fr.boreal.model.logicalElements.factory.impl.SameObjectTermFactory
                .instance()
                .createOrGetLiteral(value);
    }
}