package dev.zm.announce.announce;

public record AnnouncementResult(
        boolean success,
        String target,
        int recipientCount,
        String error
) {

    public static AnnouncementResult success(String target, int recipientCount) {
        return new AnnouncementResult(true, target, recipientCount, null);
    }

    public static AnnouncementResult failure(String error) {
        return new AnnouncementResult(false, null, 0, error);
    }
}
