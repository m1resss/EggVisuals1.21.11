package egg.client.cosmetic.watch;

import java.time.Instant;
import java.time.ZoneId;

public final class WatchClockDriver {
    private static int cachedOffsetSeconds;
    private static long lastOffsetCheckNanos;
    private static boolean offsetInitialized;

    private WatchClockDriver() {
    }

    private static void updateZoneOffset(Instant now) {
        long currentNanos = System.nanoTime();
        if (!offsetInitialized || currentNanos - lastOffsetCheckNanos > 1_000_000_000L) {
            cachedOffsetSeconds = ZoneId.systemDefault().getRules().getOffset(now).getTotalSeconds();
            lastOffsetCheckNanos = currentNanos;
            offsetInitialized = true;
        }
    }

    public static float getSecondAngle() {
        Instant now = Instant.now();
        updateZoneOffset(now);
        long secOfDay = Math.floorMod(now.getEpochSecond() + cachedOffsetSeconds, 86400L);
        double secFraction = (secOfDay % 60L) + now.getNano() / 1_000_000_000.0;
        return (float) (secFraction * 6.0);
    }

    public static float getMinuteAngle() {
        Instant now = Instant.now();
        updateZoneOffset(now);
        long secOfDay = Math.floorMod(now.getEpochSecond() + cachedOffsetSeconds, 86400L);
        int min = (int) ((secOfDay / 60L) % 60L);
        double secFraction = (secOfDay % 60L) + now.getNano() / 1_000_000_000.0;
        return (float) ((min + secFraction / 60.0) * 6.0);
    }

    public static float getHourAngle() {
        Instant now = Instant.now();
        updateZoneOffset(now);
        long secOfDay = Math.floorMod(now.getEpochSecond() + cachedOffsetSeconds, 86400L);
        int hour = (int) (secOfDay / 3600L);
        int min = (int) ((secOfDay / 60L) % 60L);
        double secFraction = (secOfDay % 60L) + now.getNano() / 1_000_000_000.0;
        return (float) (((hour % 12) + min / 60.0 + secFraction / 3600.0) * 30.0);
    }
}
