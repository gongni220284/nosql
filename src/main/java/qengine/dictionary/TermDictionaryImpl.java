package qengine.dictionary;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * implementation of the TermDictionaryImpl.
 */
public class TermDictionaryImpl implements ITermDictionary {

    private final Map<String, Integer> stringToInt = new HashMap<>();
    private final Map<Integer, String> intToString = new HashMap<>();
    private int nextId = 0;

    @Override
    public int encode(String term) {
        Objects.requireNonNull(term, "term cannot be null");
        Integer existing = stringToInt.get(term);
        if (existing != null) {
            return existing;
        }
        intToString.put(nextId, term);
        stringToInt.put(term, nextId);
        return nextId++;
    }

    @Override
    public String decode(int id) {
        String s = intToString.get(id);
        if (s == null) {
            throw new NoSuchElementException("Unknown id: " + id);
        }
        return s;
    }

    @Override
    public Integer tryGetId(String term) {
        Objects.requireNonNull(term, "term cannot be null");
        return stringToInt.get(term);
    }
}
