package delta.event;








import delta.core.Event;
import delta.core.IEvent;

import lombok.Generated;
import delta.render.CustomFogData;
import delta.render.FogShape;

import net.minecraft.client.render.Camera;
import net.minecraft.world.biome.Biome;

public class AmbienceEvent {

    public static class c extends Event implements IEvent {
        private long a;

        @Generated
        public void a(long time) {
            this.a = time;
        }

        @Generated
        public c(long time) {
            this.a = time;
        }

        @Generated
        public long b() {
            return this.a;
        }
    }

    public static class a extends Event implements IEvent {
        private float a;
        private float b;
        private float c;
        private float d;

        @Generated
        public void a(float red) {
            this.a = red;
        }

        @Generated
        public void b(float green) {
            this.b = green;
        }

        @Generated
        public void c(float blue) {
            this.c = blue;
        }

        @Generated
        public void d(float alpha) {
            this.d = alpha;
        }

        @Generated
        public a(float red, float green, float blue, float alpha) {
            this.a = red;
            this.b = green;
            this.c = blue;
            this.d = alpha;
        }

        @Generated
        public float b() {
            return this.a;
        }

        @Generated
        public float c() {
            return this.b;
        }

        @Generated
        public float d() {
            return this.c;
        }

        @Generated
        public float e() {
            return this.d;
        }
    }

    public static class b extends Event implements IEvent {
        private Camera a;
        private float b;
        private CustomFogData c;

        @Generated
        public void a(Camera camera) {
            this.a = camera;
        }

        @Generated
        public void a(float viewDistance) {
            this.b = viewDistance;
        }

        @Generated
        public void a(CustomFogData fog) {
            this.c = fog;
        }

        @Generated
        public b(Camera camera, float viewDistance, CustomFogData fog) {
            this.a = camera;
            this.b = viewDistance;
            this.c = fog;
        }

        @Generated
        public Camera b() {
            return this.a;
        }

        @Generated
        public float c() {
            return this.b;
        }

        @Generated
        public CustomFogData d() {
            return this.c;
        }

        public void a(float start, float end, FogShape shape, float red, float green, float blue, float alpha) {
            this.c = new CustomFogData(start, end, shape, red, green, blue, alpha);
        }
    }

    public static class d extends Event implements IEvent {
        private final delta.event.AmbienceEvent.d.a a;
        private float b;
        private Biome.Precipitation c;

        public enum a {
            RAIN_GRADIENT,
            THUNDER_GRADIENT,
            PRECIPITATION_PARTICLES,
            PRECIPITATION
        }

        @Generated
        public delta.event.AmbienceEvent.d.a b() {
            return this.a;
        }

        @Generated
        public float c() {
            return this.b;
        }

        @Generated
        public void a(float floatValue) {
            this.b = floatValue;
        }

        @Generated
        public Biome.Precipitation d() {
            return this.c;
        }

        @Generated
        public void a(Biome.Precipitation precipitationValue) {
            this.c = precipitationValue;
        }

        public d(delta.event.AmbienceEvent.d.a type, float value) {
            this.a = type;
            this.b = value;
        }

        public d(delta.event.AmbienceEvent.d.a type, Biome.Precipitation value) {
            this.a = type;
            this.c = value;
        }
    }
}
