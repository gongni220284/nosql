package qengine.storage;

import fr.boreal.model.logicalElements.api.*;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;

import org.apache.commons.lang3.NotImplementedException;
import qengine.model.RDFTriple;
import qengine.model.StarQuery;


import java.util.*;


import java.util.*;

/**
 * Implémentation d'un HexaStore pour stocker des RDFAtom.
 * Cette classe utilise six index pour optimiser les recherches.
 * Les index sont basés sur les combinaisons (Sujet, Prédicat, Objet), (Sujet, Objet, Prédicat),
 * (Prédicat, Sujet, Objet), (Prédicat, Objet, Sujet), (Objet, Sujet, Prédicat) et (Objet, Prédicat, Sujet).
 */
public class RDFHexaStore implements RDFStorage {
    
    // dictionaire
    private Map<String, Integer> stringToInt = new HashMap<>();
    private Map<Integer, String> intToString = new HashMap<>();
    private int nextId = 0;
    
    //str -> int
    private int encode(String str) {
        if (!stringToInt.containsKey(str)) {
            stringToInt.put(str, nextId);
            intToString.put(nextId, str);
            nextId++;
        }
        return stringToInt.get(str);
    }
    
    //int -> str
    private String decode(int id) {
        return intToString.get(id);
    }
    
    // index
    private Map<Integer, Map<Integer, Set<Integer>>> spo = new HashMap<>();
    private Map<Integer, Map<Integer, Set<Integer>>> sop = new HashMap<>();
    private Map<Integer, Map<Integer, Set<Integer>>> pso = new HashMap<>();
    private Map<Integer, Map<Integer, Set<Integer>>> pos = new HashMap<>();
    private Map<Integer, Map<Integer, Set<Integer>>> osp = new HashMap<>();
    private Map<Integer, Map<Integer, Set<Integer>>> ops = new HashMap<>();
    
    private Set<RDFTriple> allTriples = new HashSet<>();
    

    private Map<String, Long> patternStats = new HashMap<>();
    //enregister le nombre de résultats pour un pattern donné
    private void updateStats(String pattern, long count) {
        patternStats.put(pattern, count);
    }
    //obtenir le nombre
    public long getPatternSelectivity(String pattern) {
        return patternStats.getOrDefault(pattern, 0L);
    }
    
    @Override
    public boolean add(RDFTriple triple) {
        if (allTriples.contains(triple)) {
            return false;
        }
    
        int s = encode(triple.getTripleSubject().toString());
        int p = encode(triple.getTriplePredicate().toString());
        int o = encode(triple.getTripleObject().toString());
        
        //SPO[s][p].add(o)
        spo.computeIfAbsent(s, k -> new HashMap<>())
           .computeIfAbsent(p, k -> new HashSet<>())
           .add(o);
        
        sop.computeIfAbsent(s, k -> new HashMap<>())
           .computeIfAbsent(o, k -> new HashSet<>())
           .add(p);
        
        pso.computeIfAbsent(p, k -> new HashMap<>())
           .computeIfAbsent(s, k -> new HashSet<>())
           .add(o);
        
        pos.computeIfAbsent(p, k -> new HashMap<>())
           .computeIfAbsent(o, k -> new HashSet<>())
           .add(s);
        
        osp.computeIfAbsent(o, k -> new HashMap<>())
           .computeIfAbsent(s, k -> new HashSet<>())
           .add(p);
        
        ops.computeIfAbsent(o, k -> new HashMap<>())
           .computeIfAbsent(p, k -> new HashSet<>())
           .add(s);
        
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
        
        //est-ce que le terme est une variable
        boolean sIsVar = subjectTerm.isVariable();
        boolean pIsVar = predicateTerm.isVariable();
        boolean oIsVar = objectTerm.isVariable();
        
        // <Bob, knows, ?x>   pattern = "CC?"
        String pattern = (sIsVar ? "?" : "C") + (pIsVar ? "?" : "C") + (oIsVar ? "?" : "C");
        
        if (!sIsVar && !pIsVar && !oIsVar) {
            // CCC
            results.addAll(matchCCC(subjectTerm, predicateTerm, objectTerm));
        } else if (!sIsVar && !pIsVar && oIsVar) {
            // CC?
            results.addAll(matchCCVar(subjectTerm, predicateTerm, objectTerm));
        } else if (!sIsVar && pIsVar && !oIsVar) {
            // C?C
            results.addAll(matchCVarC(subjectTerm, predicateTerm, objectTerm));
        } else if (sIsVar && !pIsVar && !oIsVar) {
            // ?CC
            results.addAll(matchVarCC(subjectTerm, predicateTerm, objectTerm));
        } else if (!sIsVar && pIsVar && pIsVar) {
            // C??
            results.addAll(matchCVarVar(subjectTerm, predicateTerm, objectTerm));
        } else if (sIsVar && !pIsVar && pIsVar) {
            // ?C?
            results.addAll(matchVarCVar(subjectTerm, predicateTerm, objectTerm));
        } else if (sIsVar && pIsVar && !oIsVar) {
            // ??C
            results.addAll(matchVarVarC(subjectTerm, predicateTerm, objectTerm));
        } else {
            // ???
            results.addAll(matchVarVarVar(subjectTerm, predicateTerm, objectTerm));
        }
        
        updateStats(pattern, results.size());
        
        return results.iterator();
    }
    
    // function
    // CCC
    private List<Substitution> matchCCC(Term s, Term p, Term o) {
        int sEnc = encode(s.toString());
        int pEnc = encode(p.toString());
        int oEnc = encode(o.toString());
        
        if (spo.containsKey(sEnc) && 
            spo.get(sEnc).containsKey(pEnc) && 
            spo.get(sEnc).get(pEnc).contains(oEnc)) {
            return Collections.singletonList(new SubstitutionImpl());
        }
        return Collections.emptyList();
    }
    
    // CC?
    private List<Substitution> matchCCVar(Term s, Term p, Term oVar) {
        List<Substitution> results = new ArrayList<>();
        int sEnc = encode(s.toString());
        int pEnc = encode(p.toString());
        
        if (spo.containsKey(sEnc) && spo.get(sEnc).containsKey(pEnc)) {
            Set<Integer> objects = spo.get(sEnc).get(pEnc);
            for (Integer oEnc : objects) {
                Substitution sub = new SubstitutionImpl();
                sub.add((Variable) oVar, createLiteral(decode(oEnc)));
                results.add(sub);
            }
        }
        return results;
    }
    
    // C?C
    private List<Substitution> matchCVarC(Term s, Term pVar, Term o) {
        List<Substitution> results = new ArrayList<>();
        int sEnc = encode(s.toString());
        int oEnc = encode(o.toString());
        
        if (sop.containsKey(sEnc) && sop.get(sEnc).containsKey(oEnc)) {
            Set<Integer> predicates = sop.get(sEnc).get(oEnc);
            for (Integer pEnc : predicates) {
                Substitution sub = new SubstitutionImpl();
                sub.add((Variable) pVar, createLiteral(decode(pEnc)));
                results.add(sub);
            }
        }
        return results;
    }
    
    // ?CC
    private List<Substitution> matchVarCC(Term sVar, Term p, Term o) {
        List<Substitution> results = new ArrayList<>();
        int pEnc = encode(p.toString());
        int oEnc = encode(o.toString());
        
        if (pos.containsKey(pEnc) && pos.get(pEnc).containsKey(oEnc)) {
            Set<Integer> subjects = pos.get(pEnc).get(oEnc);
            for (Integer sEnc : subjects) {
                Substitution sub = new SubstitutionImpl();
                sub.add((Variable) sVar, createLiteral(decode(sEnc)));
                results.add(sub);
            }
        }
        return results;
    }
    
    // C??
    private List<Substitution> matchCVarVar(Term s, Term pVar, Term oVar) {
        List<Substitution> results = new ArrayList<>();
        int sEnc = encode(s.toString());
        
        if (spo.containsKey(sEnc)) {
            Map<Integer, Set<Integer>> predicateMap = spo.get(sEnc);
            for (Map.Entry<Integer, Set<Integer>> entry : predicateMap.entrySet()) {
                int pEnc = entry.getKey();
                for (Integer oEnc : entry.getValue()) {
                    Substitution sub = new SubstitutionImpl();
                    sub.add((Variable) pVar, createLiteral(decode(pEnc)));
                    sub.add((Variable) oVar, createLiteral(decode(oEnc)));
                    results.add(sub);
                }
            }
        }
        return results;
    }
    
    // ?C?
    private List<Substitution> matchVarCVar(Term sVar, Term p, Term oVar) {
        List<Substitution> results = new ArrayList<>();
        int pEnc = encode(p.toString());
        
        if (pso.containsKey(pEnc)) {
            Map<Integer, Set<Integer>> subjectMap = pso.get(pEnc);
            for (Map.Entry<Integer, Set<Integer>> entry : subjectMap.entrySet()) {
                int sEnc = entry.getKey();
                for (Integer oEnc : entry.getValue()) {
                    Substitution sub = new SubstitutionImpl();
                    sub.add((Variable) sVar, createLiteral(decode(sEnc)));
                    sub.add((Variable) oVar, createLiteral(decode(oEnc)));
                    results.add(sub);
                }
            }
        }
        return results;
    }
    
    // ??C
    private List<Substitution> matchVarVarC(Term sVar, Term pVar, Term o) {
        List<Substitution> results = new ArrayList<>();
        int oEnc = encode(o.toString());
        
        if (osp.containsKey(oEnc)) {
            Map<Integer, Set<Integer>> subjectMap = osp.get(oEnc);
            for (Map.Entry<Integer, Set<Integer>> entry : subjectMap.entrySet()) {
                int sEnc = entry.getKey();
                for (Integer pEnc : entry.getValue()) {
                    Substitution sub = new SubstitutionImpl();
                    sub.add((Variable) sVar, createLiteral(decode(sEnc)));
                    sub.add((Variable) pVar, createLiteral(decode(pEnc)));
                    results.add(sub);
                }
            }
        }
        return results;
    }
    
    // ???
    private List<Substitution> matchVarVarVar(Term sVar, Term pVar, Term oVar) {
        List<Substitution> results = new ArrayList<>();
        
        for (Map.Entry<Integer, Map<Integer, Set<Integer>>> sEntry : spo.entrySet()) {
            int sEnc = sEntry.getKey();
            for (Map.Entry<Integer, Set<Integer>> pEntry : sEntry.getValue().entrySet()) {
                int pEnc = pEntry.getKey();
                for (Integer oEnc : pEntry.getValue()) {
                    Substitution sub = new SubstitutionImpl();
                    sub.add((Variable) sVar, createLiteral(decode(sEnc)));
                    sub.add((Variable) pVar, createLiteral(decode(pEnc)));
                    sub.add((Variable) oVar, createLiteral(decode(oEnc)));
                    results.add(sub);
                }
            }
        }
        
        return results;
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
    

    // str -> Term
    private Term createLiteral(String value) {
        return fr.boreal.model.logicalElements.factory.impl.SameObjectTermFactory
                .instance()
                .createOrGetLiteral(value);
    }
}