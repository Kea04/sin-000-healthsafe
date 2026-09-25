package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record WardRecord(
        String wardId,
        String wing,
        String department,
        Integer bedsAvailable,
        String notes
) {
    // Explicit @JsonCreator/@JsonProperty instead of relying on Jackson's
    // record auto-detection: that auto-detection needs a `-parameters`
    // compiler flag (or a fairly recent Jackson version) to see the record's
    // parameter names, and I'd rather not depend on that. This makes the
    // JSON mapping unambiguous regardless of Jackson/javac version.
    @JsonCreator
    public WardRecord(
            @JsonProperty("wardId") String wardId,
            @JsonProperty("wing") String wing,
            @JsonProperty("department") String department,
            @JsonProperty("bedsAvailable") Integer bedsAvailable,
            @JsonProperty("notes") String notes
    ) {
        this.wardId = wardId;
        this.wing = wing;
        this.department = department;
        this.bedsAvailable = bedsAvailable;
        this.notes = notes;
    }
}
