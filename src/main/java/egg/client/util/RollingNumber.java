package egg.client.util;

import egg.client.render.msdf.Fonts;
import org.joml.Matrix3x2f;

public class RollingNumber {
    private String currentVal = "";
    private String prevVal = "";
    private long changeTime = 0L;
    private long durationMs = 180L;

    public RollingNumber(long durationMs) {
        this.durationMs = durationMs;
    }

    public RollingNumber() {
        this(180L);
    }

    public void reset(int val) {
        reset(String.valueOf(val));
    }

    public void reset(String val) {
        this.currentVal = val != null ? val : "";
        this.prevVal = this.currentVal;
        this.changeTime = 0L;
    }

    public void update(int newVal) {
        update(String.valueOf(newVal));
    }

    public void update(String newVal) {
        if (newVal == null) newVal = "";
        if (currentVal.isEmpty()) {
            currentVal = newVal;
            prevVal = newVal;
            return;
        }
        if (!newVal.equals(currentVal)) {
            prevVal = currentVal;
            currentVal = newVal;
            changeTime = System.currentTimeMillis();
        }
    }

    public void update(float newVal, int decimals) {
        update(String.format(java.util.Locale.ROOT, "%." + decimals + "f", newVal));
    }

    public String getCurrent() {
        return currentVal;
    }

    public void render(Matrix3x2f mat, float x, float y, float size, int color, float alphaFactor) {
        renderWithSuffix(mat, x, y, size, color, alphaFactor, "");
    }

    public void renderWithSuffix(Matrix3x2f mat, float x, float y, float size, int color, float alphaFactor, String suffix) {
        long elapsed = System.currentTimeMillis() - changeTime;
        int baseA = (int) (((color >> 24) & 0xFF) * alphaFactor);
        int rgb = color & 0x00FFFFFF;

        String curStr = currentVal + suffix;
        String oldStr = prevVal + suffix;

        if (elapsed >= durationMs || prevVal.equals(currentVal)) {
            Fonts.d.a(mat, curStr, x, y, size, (baseA << 24) | rgb);
            return;
        }

        float t = (float) elapsed / (float) durationMs;
        float ease = 1.0f - (float) Math.pow(1.0f - t, 3);

        float oldY = y - ease * 5.0f;
        int oldA = Math.round(baseA * (1.0f - ease));
        if (oldA > 5) {
            Fonts.d.a(mat, oldStr, x, oldY, size, (oldA << 24) | rgb);
        }

        float newY = y + (1.0f - ease) * 5.0f;
        int newA = Math.round(baseA * ease);
        if (newA > 5) {
            Fonts.d.a(mat, curStr, x, newY, size, (newA << 24) | rgb);
        }
    }
}
