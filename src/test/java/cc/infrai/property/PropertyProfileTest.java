package cc.infrai.property;

import java.time.LocalDate;
import java.util.List;

public final class PropertyProfileTest {
    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 9, 23);
        PropertyProfile clear = new PropertyProfile(
                "user-17",
                List.of(new PropertyProfile.MaintenanceRequest("MR-1", PropertyProfile.Severity.ROUTINE, false)),
                List.of(new PropertyProfile.TenantDocument("DOC-1", today.plusDays(30))),
                List.of(new PropertyProfile.InspectionReminder("UNIT-1", today.plusDays(2), false)));
        require(clear.reviewPriority(today) == PropertyProfile.ReviewPriority.STANDARD,
                "current records should remain standard");

        PropertyProfile flagged = new PropertyProfile(
                "user-18",
                List.of(new PropertyProfile.MaintenanceRequest("MR-2", PropertyProfile.Severity.URGENT, false)),
                List.of(new PropertyProfile.TenantDocument("DOC-2", today.minusDays(1))),
                List.of(new PropertyProfile.InspectionReminder("UNIT-2", today.minusDays(3), false)));
        require(flagged.reviewPriority(today) == PropertyProfile.ReviewPriority.COMPLIANCE_REVIEW,
                "open risk signals should require compliance review");
        System.out.println("PropertyProfileTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
