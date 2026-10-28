package delta.render;

public class CustomFogData {
    public float start;
    public float end;
    public FogShape shape;
    public float red;
    public float green;
    public float blue;
    public float alpha;

    public CustomFogData(float start, float end, FogShape shape, float red, float green, float blue, float alpha) {
        this.start = start;
        this.end = end;
        this.shape = shape;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }
}
