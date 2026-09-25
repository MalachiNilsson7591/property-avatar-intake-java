package cc.infrai.property;

import java.time.LocalDate;
import java.util.List;

public record PropertyProfile(
        String userId,
        List<MaintenanceRequest> maintenanceRequests,
        List<TenantDocument> tenantDocuments,
        List<InspectionReminder> inspectionReminders) {

    public record MaintenanceRequest(String reference, Severity severity, boolean resolved) {}
    public record TenantDocument(String reference, LocalDate expiresOn) {}
    public record InspectionReminder(String propertyReference, LocalDate dueOn, boolean completed) {}
    public enum Severity { ROUTINE, URGENT }
    public enum ReviewPriority { STANDARD, COMPLIANCE_REVIEW }

    public ReviewPriority reviewPriority(LocalDate today) {
        boolean urgentRepair = maintenanceRequests.stream()
                .anyMatch(item -> item.severity() == Severity.URGENT && !item.resolved());
        boolean expiredDocument = tenantDocuments.stream()
                .anyMatch(item -> item.expiresOn().isBefore(today));
        boolean overdueInspection = inspectionReminders.stream()
                .anyMatch(item -> !item.completed() && item.dueOn().isBefore(today));
        return urgentRepair || expiredDocument || overdueInspection
                ? ReviewPriority.COMPLIANCE_REVIEW : ReviewPriority.STANDARD;
    }
}
