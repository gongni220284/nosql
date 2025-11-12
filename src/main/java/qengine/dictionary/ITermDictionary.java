package qengine.dictionary;

/**
 * Dictionary: maps a term (String) to an integer and vice versa.
 */
public interface ITermDictionary {
    int encode(String term);
    String decode(int id);
}
