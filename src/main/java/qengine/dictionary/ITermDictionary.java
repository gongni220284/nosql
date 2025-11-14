package qengine.dictionary;

public interface ITermDictionary {

    /** Returns the id for the term, creating it if necessary. */
    int encode(String term);

    /** Returns the term for a given id, or throws if unknown (your current behavior). */
    String decode(int id);

    /**
     * Returns the id for the term if it already exists in the dictionary,
     * or null if the term was never encoded.
     * MUST NOT create a new id.
     */
    Integer tryGetId(String term);

    /**
     * Convenience method to check if a term is known.
     */
    default boolean containsTerm(String term) {
        return tryGetId(term) != null;
    }
}

