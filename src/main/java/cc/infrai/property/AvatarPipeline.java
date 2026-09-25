package cc.infrai.property;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;

public final class AvatarPipeline {
    private final InfraiClient infrai;
    private final Clock clock;

    public AvatarPipeline(InfraiClient infrai, Clock clock) {
        this.infrai = infrai;
        this.clock = clock;
    }

    public AvatarResult process(PropertyProfile profile, Path avatar, String requestId)
            throws IOException, InterruptedException, InfraiException {
        PropertyProfile.ReviewPriority priority = profile.reviewPriority(LocalDate.now(clock));
        String uploaded = infrai.upload(avatar, requestId + ":upload");
        String cropped = infrai.smartCrop(uploaded, "1:1", requestId + ":crop");
        String optimized = infrai.optimizeAvatar(cropped, requestId + ":resize");
        infrai.updateUserAvatar(profile.userId(), optimized, requestId + ":user");
        return new AvatarResult(profile.userId(), optimized, priority, "PROFILE_UPDATED");
    }

    public record AvatarResult(String userId, String avatarUrl,
                               PropertyProfile.ReviewPriority reviewPriority, String state) {}
}
