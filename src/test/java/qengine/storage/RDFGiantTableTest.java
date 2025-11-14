package qengine.storage;

import fr.boreal.model.logicalElements.api.Substitution;
import fr.boreal.model.logicalElements.api.Term;
import fr.boreal.model.logicalElements.api.Variable;
import fr.boreal.model.logicalElements.factory.impl.SameObjectTermFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import qengine.dictionary.ITermDictionary;
import qengine.dictionary.TermDictionaryImpl;
import qengine.model.RDFTriple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RDFGiantTable}.
 */
class RDFGiantTableTest {

    private final SameObjectTermFactory termFactory =
            (SameObjectTermFactory) SameObjectTermFactory.instance();


    ITermDictionary dict; // concrete type on purpose
    RDFStorage store;

    @BeforeEach
    void setup() {
        dict = new TermDictionaryImpl();
        store = new RDFGiantTable(dict);
    }

    /**
     * Helper method to create a concrete RDF triple of literals (s, p, o).
     */
    private RDFTriple triple(String s, String p, String o) {
        Term sTerm = termFactory.createOrGetLiteral(s);
        Term pTerm = termFactory.createOrGetLiteral(p);
        Term oTerm = termFactory.createOrGetLiteral(o);
        return new RDFTriple(sTerm, pTerm, oTerm);
    }

    /**
     * Verifies that add() returns true only for new triples
     * and that size() reflects the number of distinct stored triples.
     */
    @Test
    void addAndSizeShouldReflectNumberOfDistinctTriples() {
        RDFTriple t1 = triple("U1", "likes", "P0");
        RDFTriple t2 = triple("U1", "birthDate", "1988-09-24");

        assertTrue(store.add(t1));
        assertTrue(store.add(t2));
        assertFalse(store.add(t1), "Adding the same triple twice should return false");

        assertEquals(2, store.size(), "Store size must equal number of distinct triples");
    }

    /**
     * Verifies that a pattern with only variables (?s ?p ?o)
     * returns one substitution for each stored triple.
     */
    @Test
    void matchAllVariablesShouldReturnAllTriples() {
        RDFTriple t1 = triple("U1", "likes", "P0");
        RDFTriple t2 = triple("U2", "likes", "P1");

        store.add(t1);
        store.add(t2);

        Variable sVar = termFactory.createOrGetVariable("s");
        Variable pVar = termFactory.createOrGetVariable("p");
        Variable oVar = termFactory.createOrGetVariable("o");

        RDFTriple pattern = new RDFTriple(sVar, pVar, oVar); // (?s, ?p, ?o)

        Iterator<Substitution> it = store.match(pattern);
        List<Substitution> subs = new ArrayList<>();
        it.forEachRemaining(subs::add);

        assertEquals(2, subs.size(), "Matching ?s ?p ?o must return all triples as substitutions");
    }

    /**
     * Verifies that a pattern (U1, ?p, ?o) only matches triples
     * with subject U1 and correctly binds predicate and object variables.
     */
    @Test
    void matchWithConstantSubjectAndVariablesShouldReturnBindingsForThatSubject() {
        RDFTriple t1 = triple("U1", "likes", "P0");
        RDFTriple t2 = triple("U2", "likes", "P1");

        store.add(t1);
        store.add(t2);

        Term subjectU1 = termFactory.createOrGetLiteral("U1");
        Variable pVar = termFactory.createOrGetVariable("p");
        Variable oVar = termFactory.createOrGetVariable("o");

        RDFTriple pattern = new RDFTriple(subjectU1, pVar, oVar); // (U1, ?p, ?o)

        Iterator<Substitution> it = store.match(pattern);
        List<Substitution> subs = new ArrayList<>();
        it.forEachRemaining(subs::add);

        // We expect only the triple with subject U1
        assertEquals(1, subs.size(), "Only triples with subject U1 should match");

        Substitution sub = subs.get(0);
        Term pBound = sub.createImageOf(pVar);
        Term oBound = sub.createImageOf(oVar);

        assertNotNull(pBound);
        assertNotNull(oBound);
        assertEquals("likes", pBound.toString());
        assertEquals("P0", oBound.toString());
    }

    /**
     * Verifies that matching with an unknown constant (here in subject position)
     * returns no result and does not create a new entry in the dictionary.
     */
    @Test
    void matchWithUnknownConstantShouldNotPolluteDictionary() {
        RDFTriple t1 = triple("U1", "likes", "P0");
        store.add(t1);

        // Before matching, "Ahmedou" is not known to the dictionary
        assertNull(dict.tryGetId("Ahmedou"));

        Term subjectAhmedou = termFactory.createOrGetLiteral("Ahmedou");
        Variable pVar = termFactory.createOrGetVariable("p");
        Variable oVar = termFactory.createOrGetVariable("o");

        RDFTriple pattern = new RDFTriple(subjectAhmedou, pVar, oVar); // (Ahmedou, ?p, ?o)

        Iterator<Substitution> it = store.match(pattern);

        assertFalse(it.hasNext(), "No triple should match an unknown subject");

        // Crucial: match should NOT have created an id for 'Ahmedou'
        assertNull(dict.tryGetId("Ahmedou"),
                "tryGetId for 'Ahmedou' must still be null after match: no dictionary pollution");
    }

    /**
     * Verifies that a fully constant pattern (U1, likes, P0)
     * matches and returns exactly one empty substitution, since there is no variable to bind.
     */
    @Test
    void matchCCCShouldReturnEmptySubstitutionWhenTripleExists() {
        ITermDictionary dict = new TermDictionaryImpl();
        RDFGiantTable store = new RDFGiantTable(dict);

        RDFTriple t = triple("U1", "likes", "P0");
        store.add(t);

        RDFTriple pattern = triple("U1", "likes", "P0"); // (C, C, C)

        Iterator<Substitution> it = store.match(pattern);
        assertTrue(it.hasNext(), "Existing triple should be matched by CCC pattern");

        Substitution sub = it.next();
        assertTrue(sub.isEmpty(), "CCC pattern should return an empty substitution");
        assertFalse(it.hasNext(), "Only one triple should match");
    }

    /**
     * Verifies that a pattern (U1, likes, ?o) (C C ?)
     * correctly binds the object variable to the matching object.
     */
    @Test
    void matchCCVarShouldBindObject() {
        ITermDictionary dict = new TermDictionaryImpl();
        RDFGiantTable store = new RDFGiantTable(dict);

        store.add(triple("U1", "likes", "P0"));
        store.add(triple("U1", "birthDate", "1988-09-24"));

        Term sConst = termFactory.createOrGetLiteral("U1");
        Term pConst = termFactory.createOrGetLiteral("likes");
        Variable oVar = termFactory.createOrGetVariable("o");

        RDFTriple pattern = new RDFTriple(sConst, pConst, oVar); // (U1, likes, ?o)

        Iterator<Substitution> it = store.match(pattern);
        List<Substitution> subs = new ArrayList<>();
        it.forEachRemaining(subs::add);

        assertEquals(1, subs.size(), "Only one triple should match (U1, likes, ?o)");

        Term oBound = subs.get(0).createImageOf(oVar);
        assertNotNull(oBound);
        assertEquals("P0", oBound.toString());
    }

    /**
     * Verifies that a pattern (U1, ?p, P0) (C ? C)
     * correctly binds the predicate variable for the matching triple.
     */
    @Test
    void matchCVarCShouldBindPredicate() {
        ITermDictionary dict = new TermDictionaryImpl();
        RDFGiantTable store = new RDFGiantTable(dict);

        store.add(triple("U1", "likes", "P0"));
        store.add(triple("U1", "birthDate", "1988-09-24"));

        Term sConst = termFactory.createOrGetLiteral("U1");
        Variable pVar = termFactory.createOrGetVariable("p");
        Term oConst = termFactory.createOrGetLiteral("P0");

        RDFTriple pattern = new RDFTriple(sConst, pVar, oConst); // (U1, ?p, P0)

        Iterator<Substitution> it = store.match(pattern);
        List<Substitution> subs = new ArrayList<>();
        it.forEachRemaining(subs::add);

        assertEquals(1, subs.size(), "Only the (U1, likes, P0) triple should match");

        Term pBound = subs.get(0).createImageOf(pVar);
        assertNotNull(pBound);
        assertEquals("likes", pBound.toString());
    }

    /**
     * Verifies that a pattern (?s, likes, P0) (? C C)
     * correctly binds the subject variable for all matching triples.
     */
    @Test
    void matchVarCCShouldBindSubject() {
        ITermDictionary dict = new TermDictionaryImpl();
        RDFGiantTable store = new RDFGiantTable(dict);

        store.add(triple("U1", "likes", "P0"));
        store.add(triple("U2", "likes", "P0"));
        store.add(triple("U3", "likes", "P1"));

        Variable sVar = termFactory.createOrGetVariable("s");
        Term pConst = termFactory.createOrGetLiteral("likes");
        Term oConst = termFactory.createOrGetLiteral("P0");

        RDFTriple pattern = new RDFTriple(sVar, pConst, oConst); // (?s, likes, P0)

        Iterator<Substitution> it = store.match(pattern);
        List<Substitution> subs = new ArrayList<>();
        it.forEachRemaining(subs::add);

        assertEquals(2, subs.size(), "Two subjects should match (?s, likes, P0)");

        List<String> subjects = subs.stream()
                .map(sub -> sub.createImageOf(sVar).toString())
                .toList();

        assertTrue(subjects.contains("U1"));
        assertTrue(subjects.contains("U2"));
        assertFalse(subjects.contains("U3"));
    }

    /**
     * Verifies that howMany(pattern) returns the same count
     * as iterating over match(pattern) and counting substitutions.
     */
    @Test
    void howManyShouldCountMatchesCorrectly() {
        ITermDictionary dict = new TermDictionaryImpl();
        RDFGiantTable store = new RDFGiantTable(dict);

        store.add(triple("U1", "likes", "P0"));
        store.add(triple("U2", "likes", "P0"));
        store.add(triple("U3", "likes", "P1"));

        Variable sVar = termFactory.createOrGetVariable("s");
        Term pConst = termFactory.createOrGetLiteral("likes");
        Term oConst = termFactory.createOrGetLiteral("P0");

        RDFTriple pattern = new RDFTriple(sVar, pConst, oConst); // (?s, likes, P0)

        long count = store.howMany(pattern);
        assertEquals(2L, count, "howMany should count exactly the matching triples");
    }

    /**
     * Verifies that getAtoms() returns all stored triples (without duplicates).
     */
    @Test
    void getAtomsShouldReturnAllStoredTriples() {
        ITermDictionary dict = new TermDictionaryImpl();
        RDFGiantTable store = new RDFGiantTable(dict);

        RDFTriple t1 = triple("U1", "likes", "P0");
        RDFTriple t2 = triple("U2", "likes", "P1");

        store.add(t1);
        store.add(t2);
        store.add(t1); // duplicate, should be ignored

        Collection<RDFTriple> atoms = store.getAtoms();

        assertEquals(2, atoms.size(), "getAtoms must return all distinct triples");
        assertTrue(atoms.contains(t1));
        assertTrue(atoms.contains(t2));
    }
}
