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
        if (!stringToInt.containsKey(term)) {
            stringToInt.put(term, nextId);
            intToString.put(nextId, term);
            nextId++;
        }
        return stringToInt.get(term);
    }

    @Override
    public String decode(int id) {
        String s = intToString.get(id);
        if (s == null)
            throw new NoSuchElementException("Unknown id: " + id);
        return s;
    }
}