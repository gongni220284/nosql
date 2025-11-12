package qengine.dictionary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TermDictionaryImpl}.
 * Verifies correct encoding, decoding, and exception behavior.
 */
class TermDictionaryImplTest {

    ITermDictionary dict;

    @BeforeEach
    void setUp() {
        dict = new TermDictionaryImpl();
    }

    @Test
    void encodeShouldAssignUniqueIdsAndBeStable() {
        int aliceId = dict.encode("Alice");
        int bobId = dict.encode("Bob");
        int aliceIdAgain = dict.encode("Alice");

        // Each distinct term must have a unique ID
        assertNotEquals(aliceId, bobId);

        // The same term must always return the same ID
        assertEquals(aliceId, aliceIdAgain);
    }

    @Test
    void decodeShouldReturnOriginalTerm() {
        int id = dict.encode("Charlie");
        String decoded = dict.decode(id);

        // decode() must return the original string
        assertEquals("Charlie", decoded);
    }

    @Test
    void decodeOfUnknownIdShouldThrowException() {
        // Expect NoSuchElementException when decoding a non-existing id
        assertThrows(NoSuchElementException.class, () -> dict.decode(999));
    }

    @Test
    void encodeShouldRejectNullInput() {
        // Expect NullPointerException when encoding a null term
        assertThrows(NullPointerException.class, () -> dict.encode(null));
    }

    @Test
    void idsShouldBeSequentiallyAssigned() {

        int id1 = dict.encode("X");
        int id2 = dict.encode("Y");
        int id3 = dict.encode("Z");

        // IDs are assigned sequentially starting from 0
        assertEquals(id1 + 1, id2);
        assertEquals(id2 + 1, id3);
    }
}

