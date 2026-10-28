package delta.config;

import delta.core.NativeMethodLookup;
import delta.lib.json.JSONObject;
import delta.config.ConfigProcessor;
import delta.config.ThemeConstructor;
import delta.config.ThemeInfo;
import delta.config.ThemeType;

import delta.api.Compile;
import java.util.ArrayList;
import java.util.List;
import lombok.Generated;

public class ThemeProcessor extends ConfigProcessor<ThemeConstructor> {
    private ThemeType e = ThemeType.DARK;

    @Override
    @Compile
    protected List<ThemeConstructor> a(String json) throws Exception {
        if (json == null || json.isBlank() || json.trim().startsWith("[")) {
            return createDefaultThemes();
        }
        JSONObject jSONObject = new JSONObject(json);
        this.e = ThemeType.valueOf(jSONObject.a("type", ThemeType.DARK.name()));
        List<ThemeConstructor> listE = e();
        if (listE == null) {
            throw new NullPointerException();
        }
        listE.clear();
        ThemeInfo[] themeInfoArrValues = ThemeInfo.values();
        if (themeInfoArrValues == null) {
            throw new NullPointerException();
        }
        for (ThemeInfo themeInfo : themeInfoArrValues) {
            if (themeInfo == null) {
                throw new NullPointerException();
            }
            ThemeConstructor themeConstructorA = themeInfo.a(this.e);
            List<ThemeConstructor> listE2 = e();
            if (themeConstructorA == null) {
                throw new NullPointerException();
            }
            ThemeConstructor themeConstructor = new ThemeConstructor(themeConstructorA.c(), themeConstructorA.d(), themeConstructorA.e(), themeConstructorA.f(), themeConstructorA.g());
            if (listE2 == null) {
                throw new NullPointerException();
            }
            listE2.add(themeConstructor);
        }
        if (!jSONObject.m("primary")) {
            return null;
        }
        ThemeConstructor themeConstructorA2 = a(ThemeInfo.PRIMARY);
        int iH = jSONObject.h("primary");
        if (themeConstructorA2 == null) {
            throw new NullPointerException();
        }
        themeConstructorA2.a(iH);
        return null;
    }

    @Override
    @Compile
    protected String a(List<ThemeConstructor> data) throws Exception {
        JSONObject jSONObject = new JSONObject();
        jSONObject.c("type", this.e.name());
        jSONObject.c("primary", Integer.valueOf(a(ThemeInfo.PRIMARY).a()));
        return jSONObject.a(2);
    }

    public void setPrimaryColor(int color) {
        ThemeConstructor constructor = a(ThemeInfo.PRIMARY);
        if (constructor != null) {
            constructor.a(color);
        }
        ThemeInfo.PRIMARY.a(ThemeType.DARK).a(color);
        ThemeInfo.PRIMARY.a(ThemeType.LIGHT).a(color);
        boolean found = false;
        for (ThemeConstructor tc : this.d) {
            if (tc.c().equalsIgnoreCase("primary")) {
                tc.a(color);
                found = true;
            }
        }
        if (!found) {
            int[] c = delta.render.ColorUtil.b(color);
            this.d.add(new ThemeConstructor("primary", c[0], c[1], c[2], c[3]));
        }
        unSetup();
    }

    static {
        NativeMethodLookup.lookup(ThemeProcessor.class, 38);
    }

    @Generated
    public ThemeType a() {
        return this.e;
    }

    @Override
    protected String b() {
        return "theme.json";
    }

    public ThemeConstructor a(ThemeInfo type) {
        return (ThemeConstructor) this.d.stream().filter(constructor -> {
            return constructor.c().equalsIgnoreCase(type.a().c());
        }).findFirst().orElse(type.a(this.e));
    }

    private List<ThemeConstructor> createDefaultThemes() {
        List<ThemeConstructor> themes = new ArrayList<>();
        for (ThemeInfo themeInfo : ThemeInfo.values()) {
            ThemeConstructor defaults = themeInfo.a(this.e);
            themes.add(new ThemeConstructor(defaults.c(), defaults.d(), defaults.e(), defaults.f(), defaults.g()));
        }
        return themes;
    }
}
