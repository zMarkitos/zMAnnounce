package dev.zm.announce.bossbar;

public record BossbarStartResult(
        boolean success,
        String target,
        int recipientCount,
        String error
) {

    public static BossbarStartResult success(String target, int recipientCount) {
        return new BossbarStartResult(true, target, recipientCount, null);
    }

    public static BossbarStartResult failure(String error) {
        return new BossbarStartResult(false, null, 0, error);
    }
}
