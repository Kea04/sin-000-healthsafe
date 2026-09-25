package co.wethinkcode.healthsafe;

import com.opencsv.CSVReader;
import io.javalin.Javalin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class IngestionServiceApp {

    private static final Set<String> MISSING_VALUES = Set.of(
            "", "n/a", "na", "tbd", "unknown", "-", "nan"
    );

    // Departments that are the same real thing under different spellings,
    // or acronyms that titleCase() would otherwise mangle (e.g. "Icu").
    private static final Map<String, String> DEPARTMENT_CANONICAL = Map.of(
            "pediatrics", "Paediatrics",
            "paediatrics", "Paediatrics",
            "icu", "ICU"
    );

    public static void main(String[] args) throws Exception {
        List<WardRecord> wards = loadAndCleanWards();

        Javalin app = Javalin.create().start(7030);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/wards", ctx -> ctx.json(wards));

        System.out.println("ingestion-service loaded " + wards.size() + " cleaned ward records.");
    }

    private static List<WardRecord> loadAndCleanWards() throws Exception {
        List<WardRecord> cleaned = new ArrayList<>();
        // wardId -> index into `cleaned`, so we can detect and merge duplicates
        Map<String, Integer> indexByWardId = new LinkedHashMap<>();

        try (InputStream in = IngestionServiceApp.class.getResourceAsStream("/wards-outdated.csv");
             CSVReader reader = new CSVReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            boolean firstRow = true;
            String[] row;
            while ((row = reader.readNext()) != null) {
                if (firstRow) { // header
                    firstRow = false;
                    continue;
                }
                if (row.length < 4) continue;

                WardRecord record = cleanRow(row);
                Integer existingIndex = indexByWardId.get(record.wardId());

                if (existingIndex == null) {
                    indexByWardId.put(record.wardId(), cleaned.size());
                    cleaned.add(record);
                } else {
                    cleaned.set(existingIndex, mergeDuplicates(cleaned.get(existingIndex), record));
                }
            }
        }
        return cleaned;
    }

    private static WardRecord cleanRow(String[] row) {
        String wardId = normalizeSpaces(row[0]).toUpperCase();

        String wing = normalizeSpaces(row[1]);
        wing = wing.isEmpty() ? null : titleCase(wing);

        String department = normalizeDepartment(normalizeSpaces(row[2]));

        List<String> notes = new ArrayList<>();
        Integer bedsAvailable = parseBeds(normalizeSpaces(row[3]), notes);

        if (wing == null) {
            notes.add("wing was missing");
        }

        return new WardRecord(wardId, wing, department, bedsAvailable,
                notes.isEmpty() ? null : String.join("; ", notes));
    }

    private static String normalizeSpaces(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }

    static String titleCase(String value) {
        StringBuilder result = new StringBuilder();
        for (String word : value.toLowerCase().split(" ")) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(" ");
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    static String normalizeDepartment(String value) {
        String canonical = DEPARTMENT_CANONICAL.get(value.toLowerCase());
        return canonical != null ? canonical : titleCase(value);
    }

    static Integer parseBeds(String raw, List<String> notes) {
        if (MISSING_VALUES.contains(raw.toLowerCase())) {
            notes.add("bedsAvailable was missing/placeholder ('" + raw + "')");
            return null;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                notes.add("bedsAvailable was invalid (negative: '" + raw + "')");
                return null;
            }
            if (value > 100) {
                notes.add("bedsAvailable looks unrealistic ('" + raw + "') — flagged for follow-up");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            notes.add("bedsAvailable was non-numeric ('" + raw + "') — flagged for follow-up");
            return null;
        }
    }

    private static WardRecord mergeDuplicates(WardRecord existing, WardRecord incoming) {
        // Same real ward, two rows (casing/format variant, per the W-05 example in
        // the README). Prefer whichever record actually has a valid bed count.
        WardRecord winner = existing.bedsAvailable() != null ? existing
                : incoming.bedsAvailable() != null ? incoming
                : existing;

        String note = "merged duplicate record for " + existing.wardId()
                + " (kept " + (winner == existing ? "first" : "second") + " occurrence)";
        String mergedNotes = (winner.notes() == null || winner.notes().isBlank())
                ? note : winner.notes() + "; " + note;

        return new WardRecord(winner.wardId(), winner.wing(), winner.department(),
                winner.bedsAvailable(), mergedNotes);
    }
}