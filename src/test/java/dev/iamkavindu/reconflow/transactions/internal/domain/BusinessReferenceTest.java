package dev.iamkavindu.reconflow.transactions.internal.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BusinessReferenceTest {
    @Test
    void createsBusinessReferenceWithValidValue() {
        BusinessReference reference = new BusinessReference("ORD-1001");

        assertEquals("ORD-1001", reference.value());
    }

    @Test
    void rejectsNullValue() {
        assertThrows(NullPointerException.class, () -> new BusinessReference(null));
    }

    @Test
    void rejectsEmptyValue() {
        assertThrows(IllegalArgumentException.class, () -> new BusinessReference(""));
    }

    @Test
    void rejectsBlankValue() {
        assertThrows(IllegalArgumentException.class, () -> new BusinessReference("   \t   "));
    }

    @Test
    void stripsLeadingAndTrailingWhitespace() {
        BusinessReference reference = new BusinessReference("   ORD-1001   ");

        assertEquals("ORD-1001", reference.value());
    }

    @Test
    void acceptsValueWithExactlyOneHundredCharacters() {
        String value = "x".repeat(100);

        BusinessReference reference = new BusinessReference(value);

        assertEquals(value, reference.value());
    }

    @Test
    void rejectsValueLongerThanOneHundredCharacters() {
        String value = "x".repeat(101);

        assertThrows(IllegalArgumentException.class, () -> new BusinessReference(value));
    }
}
