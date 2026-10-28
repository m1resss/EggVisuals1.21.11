package delta.module.render;

import delta.core.Interface;

import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.core.InterfaceC0020Opcode;
import delta.core.Module;
import delta.render.Fonts;
import delta.render.ColorUtil;
import delta.util.InventoryUtil;
import delta.util.MathUtil;
import delta.util.ProjectUtil;
import delta.util.ServerUtil;

import delta.core.Category;
import delta.core.EventTarget;
import delta.core.ModuleRegister;
import delta.event.DrawEvent;
import delta.module.misc.StreamerMode;
import delta.setting.BooleanSetting;

import delta.setting.MultiModeSetting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Generated;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.MutableText;
import net.minecraft.client.network.ClientPlayerEntity;
import org.joml.Vector2f;

@ModuleRegister(a = "Tags", b = "Отображает информацию о сущностях над их головой", c = Category.Render)
public class EntityESP extends Module {
    private final MultiModeSetting b = new MultiModeSetting("Отслеживаемые сущности", new BooleanSetting("Игроки", true), new BooleanSetting("Животные", false), new BooleanSetting("Мобы", false), new BooleanSetting("Предметы", false));
    private final List<a> c = new ArrayList();

    @Generated
    public List<a> q() {
        return this.c;
    }

    public EntityESP() {
        a(this.b);
    }

    /**
     * Used by the player renderer to replace the vanilla name tag in the
     * entity's own render pass. This is deliberately separate from the 2D
     * overlay: vanilla labels are cancelled while Entity ESP is active.
     */
    public boolean r() {
        BooleanSetting players = this.b.a("Игроки");
        return m() && players != null && players.c().booleanValue();
    }

    @EventTarget
    public void a(DrawEvent event) {
        // Player ESP is invoked explicitly from InGameHudMixin at the end of
        // the HUD pass. EventManager silently swallows renderer exceptions.
    }

    public void s(DrawEvent event) {
        String str;
        if (event.b() && aM_.world != null && aM_.player != null) {
            for (Entity class_746Var : aM_.world.getEntities()) {
                if (class_746Var != aM_.player && !class_746Var.isRemoved()) {
                    if (!aM_.player.canSee(class_746Var) || class_746Var.isInvisible() || (class_746Var instanceof LivingEntity living && living.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY))) {
                        continue;
                    }
                    // Many servers implement lobby NPCs as armour stands with a player skin.
                    // Treat them as players so the default ESP category still shows them.
                    if ((class_746Var instanceof PlayerEntity) || (class_746Var instanceof ArmorStandEntity)) {
                        str = "Игроки";
                    } else if (class_746Var instanceof HostileEntity) {
                        str = "Мобы";
                    } else if ((class_746Var instanceof AnimalEntity) || (class_746Var instanceof ShulkerEntity) || (class_746Var instanceof VillagerEntity)) {
                        str = "Животные";
                    } else {
                        str = ((class_746Var instanceof ItemEntity) || (class_746Var instanceof ArrowEntity)) ? "Предметы" : null;
                    }
                    String key = str;
                    BooleanSetting category = key == null ? null : this.b.a(key);
                    if (category != null && category.c().booleanValue()) {
                        int color = ((class_746Var instanceof PlayerEntity) && Delta.h().d().e().d(class_746Var.getName().getString())) ? ColorUtil.a(0, 100, 0, InterfaceC0020Opcode.bN) : ColorUtil.a(0, 0, 0, 80);
                        Vec3d interpolated = MathUtil.a((Entity) class_746Var, event.g());
                        Vec3d entityPos = interpolated.add(0.0d, class_746Var.getHeight() + 0.25f, 0.0d);
                        Vector2f screenPos = ProjectUtil.a(entityPos.getX(), entityPos.getY(), entityPos.getZ());
                        if (ProjectUtil.a(screenPos)) {
                            if ((class_746Var instanceof ItemEntity) || (class_746Var instanceof ArrowEntity)) {
                                c(class_746Var, event, screenPos, 7.5f, 2.0f, color);
                            } else if (class_746Var instanceof LivingEntity) {
                                a(class_746Var, event, screenPos, 7.5f, 2.0f, color);
                                Vector2f effectPos = ProjectUtil.a(interpolated.x, interpolated.y - 0.25d, interpolated.z);
                                if (ProjectUtil.a(effectPos)) {
                                    b(class_746Var, event, effectPos, 7.5f, 2.0f, color);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void a(Entity entity, DrawEvent event, Vector2f screenPos, float fontSize, float padding, int color) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        StreamerMode streamerMode = Delta.h().d().t().aE();
        Text name = entity.getName();
        if (streamerMode != null && streamerMode.m() && streamerMode.r().c().booleanValue()) {
            name = Text.literal(streamerMode.a(name.getString())).setStyle(name.getStyle());
        }
        MutableText display = name.copy().setStyle(name.getStyle().withColor(16777215));
        MutableText teamOrDisplay = (entity.getScoreboardTeam() != null ? entity.getScoreboardTeam().getPrefix().copy().append(display) : display).copy();
        if (!living.isInvisible()) {
            teamOrDisplay.append(Text.literal(" " + ((int) Math.ceil(living.getHealth()))).setStyle(Style.EMPTY.withColor(16711680)));
        }
        Text text = teamOrDisplay;
        float textWidth = Fonts.e.a(text, fontSize);
        float textHeight = Fonts.e.d().lineHeight() * fontSize;
        Globals globals = Globals.getInstance();
        boolean isGlobal = globals != null && globals.m() && globals.isRenderInESP() && globals.isGlobalUser(entity);

        float extraPrefixWidth = 0.0f;
        float logoGap = 3.0f;
        if (isGlobal) {
            extraPrefixWidth = globals.getESPLogoWidth(textHeight, fontSize) + logoGap;
        }

        float totalWidth = textWidth + extraPrefixWidth;
        float textX = screenPos.x() - (totalWidth / 2.0f);
        float textY = screenPos.y();
        float bgX = textX - padding;
        float bgWidth = totalWidth + (padding * 2.0f);
        event.d().a(event.h(), bgX, textY, bgWidth, textHeight, 0.0f, color);

        float curX = textX;
        if (isGlobal) {
            curX = globals.renderESPLogo(event, curX, textY, textHeight, fontSize);
            curX += logoGap;
        }

        Fonts.e.a(event.h(), text, curX, textY, fontSize);
        a(entity, event, bgWidth, bgX, textY, color, textHeight);
    }

    private void a(Entity entity, DrawEvent event, float nameTagWidth, float nameTagX, float nameTagY, int color, float textHeight) {
        if (entity instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) entity;
            float spacing = textHeight * 0.3f;
            ItemStack[] stacks = {player.getMainHandStack(), player.getEquippedStack(EquipmentSlot.HEAD), player.getEquippedStack(EquipmentSlot.CHEST), player.getEquippedStack(EquipmentSlot.LEGS), player.getEquippedStack(EquipmentSlot.FEET), player.getOffHandStack()};
            int count = 0;
            for (ItemStack class_1799Var : stacks) {
                if (!class_1799Var.isEmpty()) {
                    count++;
                }
            }
            if (count > 0) {
                float x = nameTagX + ((nameTagWidth - ((count * textHeight) + ((count - 1) * spacing))) / 2.0f);
                float y = (nameTagY - textHeight) - spacing;
                for (ItemStack stack : stacks) {
                    if (!stack.isEmpty()) {
                        event.d().a(event.h(), x, y, textHeight, textHeight, 0.0f, color);
                        event.e().a(event.i(), InventoryUtil.a(stack), x, y, 0, 1.0f, textHeight / 16.0f, true);
                        x += textHeight + spacing;
                    }
                }
            }
        }
    }

    private void b(Entity entity, DrawEvent event, Vector2f screenPos, float fontSize, float padding, int color) {
        List<StatusEffectInstance> effects;
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            List<a> trackers = new ArrayList<>();
            for (int i = this.c.size() - 1; i >= 0; i--) {
                a entry = this.c.get(i);
                if (entry.b() == living.getId()) {
                    if (living.age < entry.c()) {
                        this.c.remove(i);
                    } else {
                        trackers.add(entry);
                    }
                }
            }
            if (!trackers.isEmpty()) {
                effects = new ArrayList<>();
                for (a tracker : trackers) {
                    for (StatusEffectInstance effectInstance : tracker.a()) {
                        int remaining = effectInstance.getDuration() - Math.max(0, living.age - tracker.c());
                        if (remaining > 0) {
                            StatusEffectInstance remainingEffect = effectInstance.getDuration() > 1000000 ? effectInstance : new StatusEffectInstance(effectInstance.getEffectType(), remaining, effectInstance.getAmplifier());
                            StatusEffectInstance existing = null;
                            for (StatusEffectInstance instance : effects) {
                                if (instance.getEffectType().equals(effectInstance.getEffectType())) {
                                    existing = instance;
                                    break;
                                }
                            }
                            if (existing == null) {
                                effects.add(remainingEffect);
                            } else if (remainingEffect.getAmplifier() > existing.getAmplifier() || (remainingEffect.getAmplifier() == existing.getAmplifier() && remainingEffect.getDuration() > existing.getDuration())) {
                                effects.remove(existing);
                                effects.add(remainingEffect);
                            }
                        }
                    }
                }
                if (effects.isEmpty()) {
                    effects = new ArrayList<>((Collection<? extends StatusEffectInstance>) living.getStatusEffects());
                }
            } else {
                effects = new ArrayList<>((Collection<? extends StatusEffectInstance>) living.getStatusEffects());
            }
            float lineHeight = Fonts.e.d().lineHeight() * fontSize;
            float maxWidth = 0.0f;
            for (StatusEffectInstance effect : effects) {
                int seconds = effect.getDuration() / 20;
                maxWidth = Math.max(maxWidth, Fonts.e.a(Text.translatable(((StatusEffect) effect.getEffectType().value()).getTranslationKey()).getString() + " " + MathUtil.a(effect.getAmplifier()) + (effect.getDuration() > 1000000 ? " ∞" : " - " + (seconds / 60) + ":" + String.format("%02d", Integer.valueOf(seconds % 60))), fontSize));
            }
            float textX = screenPos.x() - (maxWidth / 2.0f);
            float textY = screenPos.y() + padding;
            event.d().a(event.h(), textX - padding, textY, maxWidth + (padding * 2.0f), effects.size() * lineHeight, 0.0f, color);
            float lineY = textY;
            for (StatusEffectInstance effect2 : effects) {
                String duration = effect2.getDuration() > 1000000 ? " ∞" : " - " + ((effect2.getDuration() / 20) / 60) + ":" + String.format("%02d", Integer.valueOf((effect2.getDuration() / 20) % 60));
                String line = Text.translatable(((StatusEffect) effect2.getEffectType().value()).getTranslationKey()).getString() + " " + MathUtil.a(effect2.getAmplifier()) + duration;
                Fonts.e.a(event.h(), line, screenPos.x() - (Fonts.e.a(line, fontSize) / 2.0f), lineY, fontSize, ColorUtil.a(((StatusEffect) effect2.getEffectType().value()).getColor(), 1.0f), 0.0f);
                lineY += lineHeight;
            }
        }
    }

    private void c(Entity entity, DrawEvent event, Vector2f screenPos, float fontSize, float padding, int color) {
        MutableText text = entity instanceof ItemEntity ? ((ItemEntity) entity).getStack().getName().copy() : entity.getName().copy();
        if (entity instanceof ItemEntity) {
            ItemEntity item = (ItemEntity) entity;
            if (item.getStack().getCount() > 1) {
                text.append(Text.literal(" x" + item.getStack().getCount()));
            }
        }
        float textWidth = Fonts.e.a((Text) text, fontSize);
        float textHeight = Fonts.e.d().lineHeight() * fontSize;
        float textX = screenPos.x() - (textWidth / 2.0f);
        float textY = screenPos.y();
        event.d().a(event.h(), textX - padding, textY, textWidth + (padding * 2.0f), textHeight, 0.0f, color);
        Fonts.e.a(event.h(), text, textX, textY, fontSize);
    }

    public static final class a {
        private final List<StatusEffectInstance> a;
        private final int b;
        private final int c;

        public a(List<StatusEffectInstance> effects, int id, int age) {
            this.a = effects;
            this.b = id;
            this.c = age;
        }
public List<StatusEffectInstance> a() {
            return this.a;
        }

        public int b() {
            return this.b;
        }

        public int c() {
            return this.c;
        }
    }
}
