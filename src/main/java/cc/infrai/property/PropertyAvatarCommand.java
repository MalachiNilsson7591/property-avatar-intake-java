package cc.infrai.property;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PropertyAvatarCommand {
    private PropertyAvatarCommand() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: PropertyAvatarCommand <user-id> <avatar-file>");
            System.exit(2);
        }
        PropertyProfile profile = new PropertyProfile(
                args[0],
                List.of(new PropertyProfile.MaintenanceRequest("MR-1042", PropertyProfile.Severity.ROUTINE, false)),
                List.of(new PropertyProfile.TenantDocument("LEASE-88", LocalDate.now().plusMonths(8))),
                List.of(new PropertyProfile.InspectionReminder("UNIT-14B", LocalDate.now().plusDays(21), false)));
        AvatarPipeline pipeline = new AvatarPipeline(new InfraiClient(InfraiConfig.fromEnvironment()), Clock.systemUTC());
        try {
            AvatarPipeline.AvatarResult result = pipeline.process(profile, Path.of(args[1]), UUID.randomUUID().toString());
            System.out.println(Json.write(java.util.Map.of(
                    "userId", result.userId(),
                    "avatarUrl", result.avatarUrl(),
                    "reviewPriority", result.reviewPriority().name(),
                    "state", result.state())));
        } catch (InfraiException error) {
            if (error.isClientRejection()) {
                System.err.println("Avatar request rejected: " + error.code() + " (HTTP " + error.statusCode() + ")");
                System.exit(3);
            }
            throw error;
        }
    }
}
