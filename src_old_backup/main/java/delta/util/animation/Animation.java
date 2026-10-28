package delta.util.animation;

public class Animation {
    public enum Direction {
        FORWARDS,
        BACKWARDS
    }

    private float fromValue;
    private float toValue;
    private float currentValue;
    private long durationNs;
    private long startTimeNs;
    private Easing easing;
    private Direction direction = Direction.FORWARDS;
    private boolean finished = true;

    public Animation(float initialValue, long durationMs, Easing easing) {
        this.currentValue = initialValue;
        this.fromValue = initialValue;
        this.toValue = initialValue;
        this.durationNs = durationMs * 1_000_000L;
        this.easing = easing != null ? easing : Easing.EASE_OUT_CUBIC;
        this.finished = true;
    }

    public Animation(float initialValue, long durationMs) {
        this(initialValue, durationMs, Easing.EASE_OUT_CUBIC);
    }

    public void setTarget(float target) {
        if (Math.abs(this.toValue - target) < 0.0001f && !this.finished) {
            return;
        }
        this.fromValue = this.currentValue;
        this.toValue = target;
        this.startTimeNs = System.nanoTime();
        this.finished = false;
        this.direction = (target >= fromValue) ? Direction.FORWARDS : Direction.BACKWARDS;
    }

    public void setTarget(float target, long durationMs) {
        this.durationNs = durationMs * 1_000_000L;
        setTarget(target);
    }

    public void setTarget(float target, long durationMs, Easing easing) {
        this.durationNs = durationMs * 1_000_000L;
        this.easing = easing != null ? easing : Easing.EASE_OUT_CUBIC;
        setTarget(target);
    }

    public void reset(float value) {
        this.currentValue = value;
        this.fromValue = value;
        this.toValue = value;
        this.finished = true;
    }

    public void setDirection(Direction dir) {
        if (this.direction != dir) {
            this.direction = dir;
            float target = (dir == Direction.FORWARDS) ? 1.0f : 0.0f;
            setTarget(target);
        }
    }

    public float update() {
        if (finished) {
            return currentValue;
        }

        long now = System.nanoTime();
        long elapsed = now - startTimeNs;

        if (elapsed >= durationNs || durationNs <= 0) {
            currentValue = toValue;
            finished = true;
            return currentValue;
        }

        float progress = (float) elapsed / (float) durationNs;
        if (progress < 0.0f) progress = 0.0f;
        if (progress > 1.0f) progress = 1.0f;

        float eased = easing.ease(progress);
        currentValue = fromValue + (toValue - fromValue) * eased;
        return currentValue;
    }

    public float getValue() {
        return update();
    }

    public float getRawValue() {
        return currentValue;
    }

    public float getTarget() {
        return toValue;
    }

    public boolean isFinished() {
        return finished;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDuration(long durationMs) {
        this.durationNs = durationMs * 1_000_000L;
    }

    public void setEasing(Easing easing) {
        if (easing != null) {
            this.easing = easing;
        }
    }
}
