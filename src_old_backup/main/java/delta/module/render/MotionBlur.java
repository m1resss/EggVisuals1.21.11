package delta.module.render;

import delta.core.Category;
import delta.core.EventTarget;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.DrawEvent;
import delta.render.SadnesMotionBlurCapture;
import delta.setting.SliderSetting;

@ModuleRegister(a = "MotionBlur", b = "Кинематографичное размытие в движении при повороте камеры", c = Category.Render)
public class MotionBlur extends Module {

    private final SliderSetting strength = new SliderSetting("Сила размытия", 1.0f, 0.1f, 3.0f, 0.1f);

    public MotionBlur() {
        a(this.strength);
    }

    @Override
    public void c() {
        super.c();
        SadnesMotionBlurCapture.INSTANCE.reset();
    }

    @EventTarget
    public void onRender(DrawEvent event) {
        if (!m() || !event.b()) {
            return;
        }
        SadnesMotionBlurCapture.INSTANCE.process(this.strength.c().floatValue());
    }
}
