package delta.collector;

import java.util.Arrays;
import delta.lib.javassist.TokenId;
import delta.core.NativeMethodLookup;
import delta.lib.json.JSONArray;
import delta.module.misc.Collector_2;
import delta.core.InterfaceC0020Opcode;


import delta.config.Condition;
import delta.config.ConfigProcessor;
import delta.config.DescriptionProcessor;
import delta.config.EnchantmentProcessor;
import delta.config.PotionCondition;
import delta.config.PotionProcessor;

import delta.lib.json.JSONObject;
import delta.api.Compile;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantments;

public class CollectorProcessor extends ConfigProcessor<Collector_2.b> {
    @Override
    @Compile
    protected List<Collector_2.b> a(String str) {
        if (this.d.isEmpty()) {
            this.d.addAll(a());
        }
        JSONArray jSONArray = new JSONArray(str);
        for (int i = 0; i < jSONArray.a(); i++) {
            final JSONObject jSONObjectJ = jSONArray.j(i);
            this.d.stream().filter(obj -> ((Collector_2.b) obj).j().equals(jSONObjectJ.l("name"))).findFirst().ifPresent(obj -> this.b(jSONObjectJ, (Collector_2.b) obj));
        }
        return new ArrayList(this.d);
    }

    @Override
    @Compile
    protected String a(List<Collector_2.b> data) {
        JSONArray jSONArray = new JSONArray();
        for (Collector_2.b bVar : data) {
            JSONObject jSONObject = new JSONObject();
            if (!(bVar instanceof Collector_2.b)) {
                throw new ClassCastException();
            }
            Collector_2.b bVar2 = bVar;
            jSONObject.c("name", bVar2.j());
            jSONObject.b("active", bVar2.k());
            jSONObject.b("count", bVar2.m());
            bVar2.f();
            if (bVar2.f() != null) {
                a(jSONObject, "enchantments", bVar2.f().b());
            }
            bVar2.e();
            if (bVar2.e() != null) {
                a(jSONObject, "descriptions", bVar2.e().b());
            }
            jSONArray.a(jSONObject);
        }
        return jSONArray.E(2);
    }

    @Compile
    public void b(JSONObject obj, Collector_2.b info) {
        if (obj.m("active")) {
            info.a(obj.b("active"));
        }
        if (obj.m("count")) {
            info.b(obj.h("count"));
        }
        info.f();
        if (info.f() != null) {
            b(obj, "enchantments", info.f().b());
        }
        info.e();
        if (info.e() != null) {
            b(obj, "descriptions", info.e().b());
        }
    }

    @Compile
    private void a(JSONObject obj, String key, List<? extends Condition> conditions) {
        if (conditions.isEmpty()) {
            return;
        }
        JSONObject jSONObject = new JSONObject();
        for (Condition condition : conditions) {
            if (!(condition instanceof Condition)) {
                throw new ClassCastException();
            }
            Condition condition2 = condition;
            if (!condition2.d()) {
                JSONObject jSONObject2 = new JSONObject();
                jSONObject2.b("level", condition2.g());
                jSONObject2.c("type", condition2.h().name());
                jSONObject.c(condition2.f(), jSONObject2);
            }
        }
        obj.c(key, jSONObject);
    }

    @Compile
    private void b(JSONObject obj, String key, List<? extends Condition> conditions) {
        if (obj.m(key)) {
            JSONObject jSONObjectJ = obj.j(key);
            if (jSONObjectJ == null) {
                return;
            }
            for (Condition condition : conditions) {
                if (!(condition instanceof Condition)) {
                    throw new ClassCastException();
                }
                Condition condition2 = condition;
                if (!condition2.d() && jSONObjectJ.m(condition2.f())) {
                    JSONObject jSONObjectJ2 = jSONObjectJ.j(condition2.f());
                    if (jSONObjectJ2 == null) {
                        continue;
                    }
                    if (jSONObjectJ2.m("level")) {
                        condition2.a(jSONObjectJ2.h("level"));
                    }
                    if (jSONObjectJ2.m("type")) {
                        if (ItemType.valueOf(jSONObjectJ2.l("type")) == ItemType.OFF) {
                            condition2.a(ItemType.OFF);
                        } else {
                            condition2.a(ItemType.ON);
                        }
                    }
                    if (jSONObjectJ2.m("enabled")) {
                        if (jSONObjectJ2.b("enabled")) {
                            condition2.a(ItemType.ON);
                        } else {
                            condition2.a(ItemType.OFF);
                        }
                    }
                }
            }
        }
    }

    static {
        NativeMethodLookup.lookup(CollectorProcessor.class, 21);
    }

    public CollectorProcessor() {
        this.d.addAll(a());
    }

    @Override
    protected String b() {
        return "collect.json";
    }

    private List<Collector_2.b> a() {
        return java.util.Arrays.asList(new Collector_2.b[]{Collector_2.b.a(Items.NETHERITE_SWORD, 1, "РќРµР·РµСЂРёС‚РѕРІС‹Р№ РјРµС‡").a(true).b(true).a(new EnchantmentProcessor().a().a(Enchantments.SHARPNESS, 7).a(Enchantments.FIRE_ASPECT, 2)).a(new DescriptionProcessor().a().a("РЇРґ", 3).a("Р’Р°РјРїРёСЂРёР·Рј", 2).a("РћРєРёСЃР»РµРЅРёРµ", 2).a("РћРїС‹С‚РЅС‹Р№", 3, false).a("Р”РµС‚РµРєС†РёСЏ", 3)), Collector_2.b.a(Items.MACE, 1, "Р‘СѓР»Р°РІР°").a(true).b(true).a(new EnchantmentProcessor().a().a(Enchantments.SHARPNESS, 7).a(Enchantments.BREACH, 3).a(Enchantments.DENSITY, 5)).a(new DescriptionProcessor().a()), Collector_2.b.a(Items.TRIDENT, 1, "РўСЂРµР·СѓР±РµС†").a(true).b(true).a(new DescriptionProcessor().a().a("РЎС‚СѓРїРѕСЂ", 3).a("РџСЂРёС‚СЏР¶РµРЅРёРµ", 2).a("РЎРєР°СѓС‚", 3).a("Р’РѕР·РІСЂР°С‰РµРЅРёРµ").a("РџРѕРґСЂС‹РІРЅРёРє")), Collector_2.b.a(Items.NETHERITE_HELMET, 1, "РќРµР·РµСЂРёС‚РѕРІС‹Р№ С€Р»РµРј").a(true).b(true).a(new EnchantmentProcessor().a().a(Enchantments.PROTECTION, 5).a(Enchantments.UNBREAKING, 5).a(Enchantments.RESPIRATION, 3).a(Enchantments.MENDING)), Collector_2.b.a(Items.NETHERITE_CHESTPLATE, 1, "РќРµР·РµСЂРёС‚РѕРІС‹Р№ РЅР°РіСЂСѓРґРЅРёРє").a(true).b(true).a(new EnchantmentProcessor().a().a(Enchantments.PROTECTION, 5).a(Enchantments.UNBREAKING, 5).a(Enchantments.MENDING)), Collector_2.b.a(Items.NETHERITE_LEGGINGS, 1, "РќРµР·РµСЂРёС‚РѕРІС‹Рµ РїРѕРЅРѕР¶Рё").a(true).b(true).a(new EnchantmentProcessor().a().a(Enchantments.PROTECTION, 5).a(Enchantments.UNBREAKING, 5).a(Enchantments.MENDING)), Collector_2.b.a(Items.NETHERITE_BOOTS, 1, "РќРµР·РµСЂРёС‚РѕРІС‹Рµ Р±РѕС‚РёРЅРєРё").a(true).b(true).a(new EnchantmentProcessor().a().a(Enchantments.PROTECTION, 5).a(Enchantments.UNBREAKING, 5).a(Enchantments.DEPTH_STRIDER, 3).a(Enchantments.MENDING)), Collector_2.b.a(Items.NETHERITE_SCRAP, 8, "РўСЂР°РїРєР°").a(true).a(new DescriptionProcessor().a().a("РљР°СЃС‚: РќРµСЂСѓС€РёРјР°СЏ РєР»РµС‚РєР°")), Collector_2.b.a(Items.SUGAR, 12, "РЇРІРЅР°СЏ РїС‹Р»СЊ").a(true).a(new DescriptionProcessor().a().a("РљР°СЃС‚: РЎРІРµС‚РѕРІР°СЏ РІСЃРїС‹С€РєР°")), Collector_2.b.a(Items.PHANTOM_MEMBRANE, 4, "Р‘РѕР¶СЊСЏ Р°СѓСЂР°").a(true).a(new DescriptionProcessor().a().a("РљР°СЃС‚: Р‘РѕР¶РµСЃС‚РІРµРЅРЅР°СЏ Р°СѓСЂР°")), Collector_2.b.a(Items.ENDER_EYE, 16, "Р”РµР·РѕСЂРёРµРЅС‚Р°С†РёСЏ").a(true).a(new DescriptionProcessor().a().a("РљР°СЃС‚: Р—РІСѓРєРѕРІР°СЏ РІРѕР»РЅР°")), Collector_2.b.a(Items.WIND_CHARGE, 32, "Р—Р°СЂСЏРґ РІРµС‚СЂР°").a(true), Collector_2.b.a(Items.DRIED_KELP, 16, "РџР»Р°СЃС‚").a(true).a(new DescriptionProcessor().a().a("РљР°СЃС‚: РќРµСЂСѓС€РёРјР°СЏ СЃС‚РµРЅР°")), Collector_2.b.a(Items.SNOWBALL, 4, "РЎРЅРµР¶РѕРє Р·Р°РјРѕСЂРѕР·РєР°").a(true).a(new DescriptionProcessor().a().a("РљР°СЃС‚: Р›РµРґСЏРЅР°СЏ СЃС„РµСЂР°")), Collector_2.b.a(Items.ENDER_PEARL, 16, "РџРµСЂРєР°").a(true), Collector_2.b.a(Items.TOTEM_OF_UNDYING, 1, "РўРѕС‚РµРј Р±РµСЃСЃРјРµСЂС‚РёСЏ").a(true), Collector_2.b.a(Items.CROSSBOW, 1, "РђСЂР±Р°Р»РµС‚").a(true).a(new EnchantmentProcessor().a().a(Enchantments.QUICK_CHARGE, 3).a(Enchantments.MENDING).a(Enchantments.MULTISHOT)), Collector_2.b.a(Items.GOLDEN_APPLE, 16, "Р—РѕР»РѕС‚РѕРµ СЏР±Р»РѕРєРѕ").a(true), Collector_2.b.a(Items.ENCHANTED_GOLDEN_APPLE, 8, "Р—Р°С‡Р°СЂРѕРІР°РЅРЅРѕРµ Р·РѕР»РѕС‚РѕРµ СЏР±").a(true), Collector_2.b.a(Items.GOLDEN_CARROT, 64, "Р—РѕР»РѕС‚Р°СЏ РјРѕСЂРєРѕРІСЊ").a(true), Collector_2.b.a(Items.CHORUS_FRUIT, 64, "РҐРѕСЂСѓСЃ").a(true), Collector_2.b.a(Items.ELYTRA, 1, "Р­Р»РёС‚СЂС‹").a(true), Collector_2.b.a(Items.FIREWORK_ROCKET, 64, "Р¤РµР№РµСЂРІРµСЂРє").a(true), Collector_2.b.a(Items.SPLASH_POTION, 1, "РҐР»РѕРїСѓС€РєР°").a(true).a(16711680).a(new PotionProcessor().a(new PotionCondition(StatusEffects.SLOWNESS, 10, InterfaceC0020Opcode.aN)).a(new PotionCondition(StatusEffects.SPEED, 5, TokenId.au_)).a(new PotionCondition(StatusEffects.BLINDNESS, 10, 100)).a(new PotionCondition(StatusEffects.GLOWING, 1, 3600))), Collector_2.b.a(Items.SPLASH_POTION, 1, "РЎРІСЏС‚Р°СЏ РІРѕРґР°").a(true).a(16777215).a(new PotionProcessor().a(new PotionCondition(StatusEffects.REGENERATION, 2, 900)).a(new PotionCondition(StatusEffects.INVISIBILITY, 2, 12000)).a(new PotionCondition(StatusEffects.INSTANT_HEALTH, 2, 0))), Collector_2.b.a(Items.SPLASH_POTION, 1, "Р—РµР»СЊРµ Р“РЅРµРІР°").a(true).b(true).a(10040115).a(new PotionProcessor().a(new PotionCondition(StatusEffects.STRENGTH, 5, 600)).a(new PotionCondition(StatusEffects.SLOWNESS, 4, 600))), Collector_2.b.a(Items.SPLASH_POTION, 1, "Р—РµР»СЊРµ РџР°Р»Р»Р°РґРёРЅР°").a(true).a(65535).a(new PotionProcessor().a(new PotionCondition(StatusEffects.RESISTANCE, 1, 12000)).a(new PotionCondition(StatusEffects.FIRE_RESISTANCE, 1, 12000)).a(new PotionCondition(StatusEffects.HEALTH_BOOST, 3, 1200)).a(new PotionCondition(StatusEffects.INVISIBILITY, 1, 18000))), Collector_2.b.a(Items.SPLASH_POTION, 1, "Р—РµР»СЊРµ РђСЃСЃР°СЃРёРЅР°").a(true).a(3355443).a(new PotionProcessor().a(new PotionCondition(StatusEffects.STRENGTH, 4, 1200)).a(new PotionCondition(StatusEffects.SPEED, 3, 6000)).a(new PotionCondition(StatusEffects.HASTE, 1, 1200)).a(new PotionCondition(StatusEffects.INSTANT_DAMAGE, 2, 0))), Collector_2.b.a(Items.SPLASH_POTION, 1, "Р—РµР»СЊРµ Р Р°РґРёР°С†РёРё").a(true).a(3329330).a(new PotionProcessor().a(new PotionCondition(StatusEffects.POISON, 2, TokenId.au_)).a(new PotionCondition(StatusEffects.WITHER, 2, TokenId.au_)).a(new PotionCondition(StatusEffects.SLOWNESS, 3, TokenId.au_)).a(new PotionCondition(StatusEffects.HUNGER, 5, TokenId.au_)).a(new PotionCondition(StatusEffects.GLOWING, 1, TokenId.au_))), Collector_2.b.a(Items.SPLASH_POTION, 1, "РЎРЅРѕС‚РІРѕСЂРЅРѕРµ").a(true).b(false).a(4737096).a(new PotionProcessor().a(new PotionCondition(StatusEffects.WEAKNESS, 2, 1800)).a(new PotionCondition(StatusEffects.MINING_FATIGUE, 2, InterfaceC0020Opcode.aN)).a(new PotionCondition(StatusEffects.WITHER, 3, 1800)).a(new PotionCondition(StatusEffects.BLINDNESS, 1, InterfaceC0020Opcode.aN))), Collector_2.b.a(Items.POTION, 1, "Р—РµР»СЊРµ").a(true).a(new PotionProcessor().a(new PotionCondition(StatusEffects.STRENGTH, 3, 3600)).a(new PotionCondition(StatusEffects.SPEED, 3, 3600))), Collector_2.b.a(Items.POTION, 1, "Р—РµР»СЊРµ СЂРµРіРµРЅРµСЂР°С†РёРё").a(true).a(new PotionProcessor().a(new PotionCondition(StatusEffects.INSTANT_HEALTH, 2, 0)).a(new PotionCondition(StatusEffects.REGENERATION, 1, 900))), Collector_2.b.a(Items.TIPPED_ARROW, 32, "РљСЂРѕРІР°РІР°СЏ СЃС‚СЂРµР»Р°").a(true).a(new PotionProcessor().a(new PotionCondition(StatusEffects.WEAKNESS, 3, 60)).a(new PotionCondition(StatusEffects.BLINDNESS, 1, 40)).a(new PotionCondition(StatusEffects.MINING_FATIGUE, 1, 40)).a(new PotionCondition(StatusEffects.NAUSEA, 1, 100))), Collector_2.b.a(Items.TIPPED_ARROW, 64, "РЎС‚СЂРµР»Р° РѕР±Р»РµРґРµРЅРµРЅРёСЏ").a(false).a(new PotionProcessor().a(new PotionCondition(StatusEffects.SLOWNESS, 10, 100)).a(new PotionCondition(StatusEffects.MINING_FATIGUE, 3, 40))), Collector_2.b.a(Items.TIPPED_ARROW, 64, "РњСѓС‡РёС‚РµР»СЊРЅР°СЏ СЃС‚СЂРµР»Р°").a(false).a(new PotionProcessor().a(new PotionCondition(StatusEffects.SLOWNESS, 3, 100)).a(new PotionCondition(StatusEffects.WITHER, 3, 100)).a(new PotionCondition(StatusEffects.POISON, 3, 100)))});
    }
}

