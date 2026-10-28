package egg.client.util.animation;

public class Animation {
    private float fromValue;
    private float targetValue;
    private float currentValue;
    private long durationMs;
    private long startTimeNano;
    private Easing easing;
    private boolean finished;

    public Animation(float initialValue, long durationMs, Easing easing) {
        this.fromValue = initialValue;
        this.targetValue = initialValue;
        this.currentValue = initialValue;
        this.durationMs = durationMs;
        this.easing = easing != null ? easing : Easing.EASE_OUT_CUBIC;
        this.startTimeNano = System.nanoTime();
        this.finished = true;
    }

    public Animation(float initialValue, long durationMs) {
        this(initialValue, durationMs, Easing.EASE_OUT_CUBIC);
    }

    public Animation(float initialValue) {
        this(initialValue, 250L, Easing.EASE_OUT_CUBIC);
    }

    public void setTarget(float target) {
        if (this.targetValue == target) return;
        this.fromValue = this.currentValue;
        this.targetValue = target;
        this.startTimeNano = System.nanoTime();
        this.finished = (this.fromValue == target);
    }

    public void setTarget(float target, long durationMs, Easing easing) {
        this.durationMs = durationMs;
        if (easing != null) this.easing = easing;
        setTarget(target);
    }

    public void setValue(float value) {
        this.fromValue = value;
        this.targetValue = value;
        this.currentValue = value;
        this.finished = true;
    }

    public void update() {
        if (this.finished) {
            this.currentValue = this.targetValue;
            return;
        }

        long elapsedNano = System.nanoTime() - this.startTimeNano;
        float progress = (float) elapsedNano / (this.durationMs * 1_000_000.0f);

        if (progress >= 1.0f) {
            this.currentValue = this.targetValue;
            this.finished = true;
        } else {
            float eased = this.easing.ease(progress);
            this.currentValue = this.fromValue + (this.targetValue - this.fromValue) * eased;
        }
    }

    public float getValue() {
        update();
        return this.currentValue;
    }

    public float getTarget() {
        return this.targetValue;
    }

    public boolean isFinished() {
        update();
        return this.finished;
    }

    public long getDuration() {
        return this.durationMs;
    }

    public void setDuration(long durationMs) {
        this.durationMs = durationMs;
    }

    public void setEasing(Easing easing) {
        this.easing = easing;
    }
}
