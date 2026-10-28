package delta.ambience;

import delta.core.Module;
import delta.event.AmbienceEvent;
import delta.render.ColorUtil;

import delta.core.Category;
import delta.core.EventTarget;
import delta.core.ModuleRegister;

import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.ModeSetting;
import delta.setting.SliderSetting;
import net.minecraft.world.biome.Biome;
import delta.render.FogShape;

@ModuleRegister(a = "Ambience", b = "Настраивает атмосферу, погоду и кастомный туман мира", c = Category.Render)
public class Ambience extends Module {
    private final ModeSetting c = new ModeSetting("Время в мире", "Без изменений", "Без изменений", "Рассвет", "День", "Полдень", "Закат", "Ночь", "Полночь");
    public final ModeSetting b = new ModeSetting("Погода в мире", "Без изменений", "Без изменений", "Солнечно", "Дождь", "Снег", "Гроза");
    private final BooleanSetting d = new BooleanSetting("Кастомный туман", false);
    private final ModeSetting fogShape = (ModeSetting) new ModeSetting("Форма тумана", "Сфера", "Сфера", "Цилиндр").a(this.d::h);
    private final ColorSetting e = (ColorSetting) new ColorSetting("Цвет тумана", Integer.valueOf(ColorUtil.a(138, 92, 246, 255))).a(this.d::h);
    private final SliderSetting f = (SliderSetting) new SliderSetting("Начало тумана", 5.0f, -50.0f, 100.0f, 1.0f).a(this.d::h);
    private final SliderSetting g = (SliderSetting) new SliderSetting("Дальность тумана", 45.0f, 5.0f, 250.0f, 5.0f).a(this.d::h);

    public Ambience() {
        a(this.c, this.b, this.d, this.fogShape, this.e, this.f, this.g);
    }

    @EventTarget
    public void a(AmbienceEvent.c event) {
        long jB;
        switch (this.c.c()) {
            case "Рассвет":
                jB = 23041;
                break;
            case "День":
                jB = 1000;
                break;
            case "Полдень":
                jB = 6000;
                break;
            case "Закат":
                jB = 12610;
                break;
            case "Ночь":
                jB = 13000;
                break;
            case "Полночь":
                jB = 18000;
                break;
            default:
                jB = event.b();
                break;
        }
        event.a(jB);
    }

    @EventTarget
    public void a(AmbienceEvent.a event) {
        if (this.d.c().booleanValue()) {
            float[] rgba = ColorUtil.a(this.e.c().intValue());
            event.a(rgba[0]);
            event.b(rgba[1]);
            event.c(rgba[2]);
            event.d(rgba[3]);
            event.a(true);
        }
    }

    @EventTarget
    public void a(AmbienceEvent.b event) {
        if (this.d.c().booleanValue()) {
            FogShape shape = this.fogShape.l("Цилиндр") ? FogShape.CYLINDER : FogShape.SPHERE;
            float[] rgba = ColorUtil.a(this.e.c().intValue());
            float start = this.f.c().floatValue();
            float end = Math.max(start + 5.0f, this.g.c().floatValue());
            event.a(start, end, shape, rgba[0], rgba[1], rgba[2], rgba[3]);
        }
    }

    @EventTarget
    public void a(AmbienceEvent.d event) {
        float fC;
        float fC2;
        switch (event.b()) {
            case RAIN_GRADIENT:
                switch (this.b.c()) {
                    case "Солнечно":
                        fC2 = 0.0f;
                        break;
                    case "Дождь":
                    case "Гроза":
                        fC2 = 1.0f;
                        break;
                    case "Снег":
                        fC2 = 0.9f;
                        break;
                    default:
                        fC2 = event.c();
                        break;
                }
                event.a(fC2);
                break;
            case THUNDER_GRADIENT:
                switch (this.b.c()) {
                    case "Солнечно":
                    case "Дождь":
                    case "Снег":
                        fC = 0.0f;
                        break;
                    case "Гроза":
                        fC = 1.0f;
                        break;
                    default:
                        fC = event.c();
                        break;
                }
                event.a(fC);
                break;
            case PRECIPITATION_PARTICLES:
                if (this.b.l("Снег")) {
                    event.a(0.0f);
                }
                break;
            case PRECIPITATION:
                if (this.b.l("Снег")) {
                    event.a(Biome.Precipitation.SNOW);
                }
                break;
        }
    }
}
