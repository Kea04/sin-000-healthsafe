package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IngestionServiceAppTest {

    @Test
    void collapsesPaddingAndFixesCasing() {
        assertEquals("East Wing", IngestionServiceApp.titleCase("  east   wing "));
    }

    @Test
    void treatsPediatricsAndPaediatricsAsOneDepartment() {
        assertEquals("Paediatrics", IngestionServiceApp.normalizeDepartment("pediatrics"));
        assertEquals("Paediatrics", IngestionServiceApp.normalizeDepartment("PAEDIATRICS"));
    }

    @Test
    void keepsKnownAcronymsUppercase() {
        assertEquals("ICU", IngestionServiceApp.normalizeDepartment("icu"));
    }

    @Test
    void treatsPlaceholdersAsMissingBeds() {
        List<String> notes = new ArrayList<>();
        assertNull(IngestionServiceApp.parseBeds("N/A", notes));
        assertFalse(notes.isEmpty());
    }

    @Test
    void rejectsNegativeAndNonNumericBedCounts() {
        assertNull(IngestionServiceApp.parseBeds("-2", new ArrayList<>()));
        assertNull(IngestionServiceApp.parseBeds("five", new ArrayList<>()));
    }

    @Test
    void acceptsValidBedCounts() {
        List<String> notes = new ArrayList<>();
        assertEquals(3, IngestionServiceApp.parseBeds("3", notes));
        assertTrue(notes.isEmpty());
    }
}
