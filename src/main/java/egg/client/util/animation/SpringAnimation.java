package egg.client.util.animation;

public class SpringAnimation {
    private float value;
    private float target;
    private float velocity;
    private float stiffness;
    private float damping;

    public SpringAnimation(float initialValue, float stiffness, float damping) {
        this.value = initialValue;
        this.target = initialValue;
        this.velocity = 0.0f;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public SpringAnimation(float initialValue) {
        this(initialValue, 240.0f, 20.0f);
    }

    public void setTarget(float target) {
        this.target = target;
    }

    public void setValue(float value) {
        this.value = value;
        this.target = value;
        this.velocity = 0.0f;
    }

    public void update(float dt) {
        float safeDt = Math.min(dt, 0.05f);
        float steps = Math.max(1, (int) Math.ceil(safeDt / 0.005f));
        float subDt = safeDt / steps;
        for (int i = 0; i < steps; i++) {
            float force = -stiffness * (value - target) - damping * velocity;
            velocity += force * subDt;
            value += velocity * subDt;
        }
        if (Math.abs(value - target) < 0.0005f && Math.abs(velocity) < 0.0005f) {
            value = target;
            velocity = 0.0f;
        }
    }

    public float get() {
        return value;
    }

    public float getTarget() {
        return target;
    }

    public float getVelocity() {
        return velocity;
    }

    public boolean isResting() {
        return value == target && velocity == 0.0f;
    }

    public void setStiffness(float stiffness) {
        this.stiffness = stiffness;
    }

    public void setDamping(float damping) {
        this.damping = damping;
    }
}
