package egg.client.util;

public record LyricLine(long timeMs, String text) implements Comparable<LyricLine> {
    @Override
    public int compareTo(LyricLine o) {
        return Long.compare(this.timeMs, o.timeMs);
    }
}
