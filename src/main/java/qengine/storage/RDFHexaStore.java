package qengine.storage;

import fr.boreal.model.logicalElements.api.*;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;

import org.apache.commons.lang3.NotImplementedException;
import qengine.dictionary.ITermDictionary;
import qengine.dictionary.TermDictionaryImpl;
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

    private  final ITermDictionary termDictionary;

    public RDFHexaStore() {
        this(new TermDictionaryImpl());
    }

    public RDFHexaStore(ITermDictionary termDictionary) {
        this.termDictionary = termDictionary;
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
    
        int s = termDictionary.encode(triple.getTripleSubject().toString());
        int p = termDictionary.encode(triple.getTriplePredicate().toString());
        int o = termDictionary.encode(triple.getTripleObject().toString());
        
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
        Integer sEncoded = termDictionary.tryGetId(s.toString());
        Integer pEncoded = termDictionary.tryGetId(p.toString());
        Integer oEncoded = termDictionary.tryGetId(o.toString());

        if (sEncoded == null || pEncoded == null || oEncoded == null) {
            return Collections.emptyList();
        }
        
        if (spo.containsKey(sEncoded) && 
            spo.get(sEncoded).containsKey(pEncoded) && 
            spo.get(sEncoded).get(pEncoded).contains(oEncoded)) {
            return Collections.singletonList(new SubstitutionImpl());
        }
        return Collections.emptyList();
    }
    
    // CC?
    private List<Substitution> matchCCVar(Term s, Term p, Term oVar) {
        List<Substitution> results = new ArrayList<>();
        
        Integer sEncoded = termDictionary.tryGetId(s.toString());
        Integer pEncoded = termDictionary.tryGetId(p.toString());
        
        if (sEncoded == null || pEncoded == null) {
            return Collections.emptyList();
        }
        
        if (spo.containsKey(sEncoded) && spo.get(sEncoded).containsKey(pEncoded)) {
            Set<Integer> objects = spo.get(sEncoded).get(pEncoded);
            for (Integer oEncoded : objects) {
                Substitution sub = new SubstitutionImpl();
                sub.add((Variable) oVar, createLiteral(termDictionary.decode(oEncoded)));
                results.add(sub);
            }
        }
        return results;
    }
    
    // C?C
    private List<Substitution> matchCVarC(Term s, Term pVar, Term o) {
        List<Substitution> results = new ArrayList<>();
        
        Integer sEncoded = termDictionary.tryGetId(s.toString());
        Integer oEncoded = termDictionary.tryGetId(o.toString());
        
        if (sEncoded == null || oEncoded == null) {
            return Collections.emptyList();
        }
        
        if (sop.containsKey(sEncoded) && sop.get(sEncoded).containsKey(oEncoded)) {
            Set<Integer> predicates = sop.get(sEncoded).get(oEncoded);
            for (Integer pEncoded : predicates) {
                Substitution sub = new SubstitutionImpl();
                sub.add((Variable) pVar, createLiteral(termDictionary.decode(pEncoded)));
                results.add(sub);
            }
        }
        return results;
    }
    
    // ?CC
    private List<Substitution> matchVarCC(Term sVar, Term p, Term o) {
        List<Substitution> results = new ArrayList<>();
        
        Integer pEncoded = termDictionary.tryGetId(p.toString());
        Integer oEncoded = termDictionary.tryGetId(o.toString());
        
        if (pEncoded == null || oEncoded == null) {
            return Collections.emptyList();
        }
        
        if (pos.containsKey(pEncoded) && pos.get(pEncoded).containsKey(oEncoded)) {
            Set<Integer> subjects = pos.get(pEncoded).get(oEncoded);
            for (Integer sEncoded : subjects) {
                Substitution sub = new SubstitutionImpl();
                sub.add((Variable) sVar, createLiteral(termDictionary.decode(sEncoded)));
                results.add(sub);
            }
        }
        return results;
    }
    
    // C??
    private List<Substitution> matchCVarVar(Term s, Term pVar, Term oVar) {
        List<Substitution> results = new ArrayList<>();
        
        Integer sEncoded = termDictionary.tryGetId(s.toString());
        
        if (sEncoded == null) {
            return Collections.emptyList();
        }
        
        if (spo.containsKey(sEncoded)) {
            Map<Integer, Set<Integer>> predicateMap = spo.get(sEncoded);
            for (Map.Entry<Integer, Set<Integer>> entry : predicateMap.entrySet()) {
                int pEncoded = entry.getKey();
                for (Integer oEncoded : entry.getValue()) {
                    Substitution sub = new SubstitutionImpl();
                    sub.add((Variable) pVar, createLiteral(termDictionary.decode(pEncoded)));
                    sub.add((Variable) oVar, createLiteral(termDictionary.decode(oEncoded)));
                    results.add(sub);
                }
            }
        }
        return results;
    }
    
    // ?C?
    private List<Substitution> matchVarCVar(Term sVar, Term p, Term oVar) {
        List<Substitution> results = new ArrayList<>();
        
        Integer pEncoded = termDictionary.tryGetId(p.toString());
        
        if (pEncoded == null) {
            return Collections.emptyList();
        }
        
        if (pso.containsKey(pEncoded)) {
            Map<Integer, Set<Integer>> subjectMap = pso.get(pEncoded);
            for (Map.Entry<Integer, Set<Integer>> entry : subjectMap.entrySet()) {
                int sEncoded = entry.getKey();
                for (Integer oEncoded : entry.getValue()) {
                    Substitution sub = new SubstitutionImpl();
                    sub.add((Variable) sVar, createLiteral(termDictionary.decode(sEncoded)));
                    sub.add((Variable) oVar, createLiteral(termDictionary.decode(oEncoded)));
                    results.add(sub);
                }
            }
        }
        return results;
    }
    
    // ??C
    private List<Substitution> matchVarVarC(Term sVar, Term pVar, Term o) {
        List<Substitution> results = new ArrayList<>();
        
        Integer oEncoded = termDictionary.tryGetId(o.toString());
        
        if (oEncoded == null) {
            return Collections.emptyList();
        }
        
        if (osp.containsKey(oEncoded)) {
            Map<Integer, Set<Integer>> subjectMap = osp.get(oEncoded);
            for (Map.Entry<Integer, Set<Integer>> entry : subjectMap.entrySet()) {
                int sEncoded = entry.getKey();
                for (Integer pEncoded : entry.getValue()) {
                    Substitution sub = new SubstitutionImpl();
                    sub.add((Variable) sVar, createLiteral(termDictionary.decode(sEncoded)));
                    sub.add((Variable) pVar, createLiteral(termDictionary.decode(pEncoded)));
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
            int sEncoded = sEntry.getKey();
            for (Map.Entry<Integer, Set<Integer>> pEntry : sEntry.getValue().entrySet()) {
                int pEncoded = pEntry.getKey();
                for (Integer oEncoded : pEntry.getValue()) {
                    Substitution sub = new SubstitutionImpl();
                    sub.add((Variable) sVar, createLiteral(termDictionary.decode(sEncoded)));
                    sub.add((Variable) pVar, createLiteral(termDictionary.decode(pEncoded)));
                    sub.add((Variable) oVar, createLiteral(termDictionary.decode(oEncoded)));
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