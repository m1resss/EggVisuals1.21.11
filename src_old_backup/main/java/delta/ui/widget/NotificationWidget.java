package delta.ui.widget;

import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.render.ColorUtil;
import delta.util.MathUtil;

import delta.config.ThemeInfo;
import delta.core.GlobalEvent;
import delta.core.Interface;
import delta.event.BackendEvent;
import delta.event.DrawEvent;
import delta.event.PacketEvent;
import delta.notification.Notification;
import delta.ui.element.DragInfo;
import delta.ui.widget.Widget;
import delta.module.render.Interface_2;

import delta.setting.BooleanSetting;
import delta.setting.ModeSetting;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class NotificationWidget extends Widget implements Interface {
    private static NotificationWidget instance;

    private final ModeSetting position;
    private final ModeSetting style;
    private final BooleanSetting progressBar;
    private final BooleanSetting sound;
    private final BooleanSetting f; // Оповещать о поднятии донат-предметов
    private final BooleanSetting g; // Обновления и уведомления друзей

    public static NotificationWidget getInstance() {
        return instance;
    }

    public boolean isSoundEnabled() {
        return this.sound != null && this.sound.c().booleanValue();
    }

    public NotificationWidget() {
        super(new DragInfo("Уведомления", 0.0f, 0.0f, 135.0f, 28.0f));
        instance = this;
        this.position = new ModeSetting("Позиция", "Справа снизу", "Справа снизу", "Справа сверху", "По центру сверху", "Свободная");
        this.style = new ModeSetting("Стиль", "Карточка", "Карточка", "Компактный");
        this.progressBar = new BooleanSetting("Полоска времени", true);
        this.sound = new BooleanSetting("Звуковой эффект", true);
        this.f = new BooleanSetting("Оповещать о поднятии донат-предметов", true);
        this.g = new BooleanSetting("Обновления и уведомления друзей", true);
        j().a(this);
        j().a(1);
        a(this.position, this.style, this.progressBar, this.sound, this.g, this.f);
    }

    @Override
    public void a(GlobalEvent event) {
        d().a((aM_.currentScreen instanceof ChatScreen) || !Delta.h().d().m().b().isEmpty());
        super.a(event);
    }

    private static class ParsedNotification {
        final String title;
        final String subtitle;
        final int statusType; // 0: standard, 1: enabled/success, 2: disabled/danger

        ParsedNotification(String title, String subtitle, int statusType) {
            this.title = title;
            this.subtitle = subtitle;
            this.statusType = statusType;
        }
    }

    private ParsedNotification parseNotification(Notification notification, boolean isCompact) {
        if (notification.getTitle() != null && !notification.getTitle().isEmpty()) {
            String sub = notification.c() instanceof Text ? ((Text) notification.c()).getString() : String.valueOf(notification.c());
            return new ParsedNotification(notification.getTitle(), sub, 0);
        }

        Object msgObj = notification.c();
        String raw = msgObj instanceof Text ? ((Text) msgObj).getString() : String.valueOf(msgObj);
        if (raw == null || raw.trim().isEmpty()) {
            return new ParsedNotification("Egg Client", "Уведомление", 0);
        }

        String trimmed = raw.trim();
        if (trimmed.endsWith(" активирован")) {
            String modName = trimmed.substring(0, trimmed.length() - " активирован".length());
            return new ParsedNotification(modName, "Активирован", 1);
        }
        if (trimmed.endsWith(" деактивирован")) {
            String modName = trimmed.substring(0, trimmed.length() - " деактивирован".length());
            return new ParsedNotification(modName, "Деактивирован", 2);
        }
        if (trimmed.contains(" подобрал ")) {
            int idx = trimmed.indexOf(" подобрал ");
            String player = trimmed.substring(0, idx);
            String item = trimmed.substring(idx + 10);
            return new ParsedNotification(item, "Подобрал: " + player, 0);
        }
        if (trimmed.contains(": ")) {
            int idx = trimmed.indexOf(": ");
            String title = trimmed.substring(0, idx);
            String sub = trimmed.substring(idx + 2);
            return new ParsedNotification(title, sub, 0);
        }

        if (isCompact) {
            return new ParsedNotification(trimmed, "", 0);
        } else {
            return new ParsedNotification("Egg Client", trimmed, 0);
        }
    }

    @Override
    public void a(DrawEvent event) {
        d().a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());
        float widgetAnim = a();
        if (widgetAnim <= 0.0f) {
            super.a(event);
            return;
        }

        List<Notification> allNotifs = Delta.h().d().m().b();
        if (allNotifs.isEmpty()) {
            super.a(event);
            return;
        }

        // Filter active notifications
        List<Notification> activeList = new ArrayList<>();
        for (Notification n : allNotifs) {
            if (n.a().c() > 0.0f) {
                activeList.add(n);
            }
        }
        if (activeList.isEmpty()) {
            super.a(event);
            return;
        }

        // HUD Styling properties
        float radius = getRounding();
        float blurVal = getBlur();
        float opacityVal = getOpacity();
        int primaryColor = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();

        Interface_2 inter = Interface_2.getInstance();
        int tc1 = inter != null ? inter.getTitleColor1().c().intValue() : 0xFF8B5CF6;
        int tc2 = inter != null ? inter.getTitleColor2().c().intValue() : 0xFF00CEC9;

        boolean isCompact = this.style.c().equals("Компактный");
        float cardHeight = isCompact ? 19.5f : 32.0f;
        float titleFontSize = 8.5f;
        float subFontSize = 7.5f;
        float gap = 4.5f;

        String posMode = this.position.c();
        int screenWidth = aM_.getWindow().getScaledWidth();
        int screenHeight = aM_.getWindow().getScaledHeight();

        boolean isBottomRight = posMode.equals("Справа снизу");
        boolean isTopRight = posMode.equals("Справа сверху");
        boolean isTopCenter = posMode.equals("По центру сверху");
        boolean isFree = posMode.equals("Свободная");

        // Calculate card widths and target coordinates
        float chatOffset = (aM_.currentScreen instanceof ChatScreen) ? 26.0f : 0.0f;
        float currentStackY = isBottomRight ? (screenHeight - 10.0f - chatOffset) : 10.0f;
        if (isTopCenter) {
            float bossOffset = 0.0f;
            if (!((platform.inject.accessors.BossBarHudAccessor) aM_.inGameHud.getBossBarHud()).getBossBars().isEmpty()) {
                int size = ((platform.inject.accessors.BossBarHudAccessor) aM_.inGameHud.getBossBarHud()).getBossBars().size() - 1;
                bossOffset = (((12 + (size * 19)) + 5) * aM_.getWindow().calculateScaleFactor(((Integer) aM_.options.getGuiScale().getValue()).intValue(), aM_.forcesUnicodeFont())) / aM_.getWindow().calculateScaleFactor(2, aM_.forcesUnicodeFont()) + 12.0f;
            }
            currentStackY = 10.0f + bossOffset;
        } else if (isFree) {
            currentStackY = j().b();
        }

        float minBoxX = Float.MAX_VALUE;
        float maxBoxX = -Float.MAX_VALUE;
        float minBoxY = Float.MAX_VALUE;
        float maxBoxY = -Float.MAX_VALUE;

        for (int i = 0; i < activeList.size(); i++) {
            Notification notification = activeList.get(i);
            float anim = notification.a().c() * widgetAnim;
            if (anim <= 0.0f) {
                continue;
            }

            ParsedNotification parsed = parseNotification(notification, isCompact);

            // Compute width
            float textW;
            if (isCompact) {
                if (parsed.subtitle.isEmpty()) {
                    textW = Fonts.e.a(parsed.title, titleFontSize);
                } else {
                    textW = Fonts.e.a(parsed.title, titleFontSize) + 6.0f + Fonts.e.a("•", 6.5f) + 6.0f + Fonts.e.a(parsed.subtitle, subFontSize);
                }
            } else {
                float titleW = Fonts.e.a(parsed.title, titleFontSize);
                float subW = parsed.subtitle.isEmpty() ? 0.0f : (Fonts.e.a("• ", subFontSize) + Fonts.e.a(parsed.subtitle, subFontSize));
                textW = Math.max(titleW, subW);
            }

            float iconWidthEst = 12.0f;
            float cardWidth = 9.0f + iconWidthEst + 7.0f + textW + 12.0f;
            cardWidth = Math.max(isCompact ? 100.0f : 125.0f, cardWidth);

            // Determine target coordinates
            float targetX;
            float targetY;

            if (isBottomRight) {
                targetX = screenWidth - cardWidth - 10.0f;
                targetY = currentStackY - cardHeight;
                currentStackY -= (cardHeight + gap);
            } else if (isTopRight) {
                targetX = screenWidth - cardWidth - 10.0f;
                targetY = currentStackY;
                currentStackY += (cardHeight + gap);
            } else if (isTopCenter) {
                targetX = (screenWidth - cardWidth) / 2.0f;
                targetY = currentStackY;
                currentStackY += (cardHeight + gap);
            } else {
                targetX = j().a();
                targetY = currentStackY;
                currentStackY += (cardHeight + gap);
            }

            // Smooth position interpolation
            if (notification.getAnimatedY() < 0.0f) {
                notification.setAnimatedY(targetY);
                notification.setAnimatedX((isBottomRight || isTopRight) ? (targetX + 50.0f) : targetX);
            } else {
                notification.setAnimatedY(MathUtil.c(notification.getAnimatedY(), targetY, 0.22f));
                notification.setAnimatedX(MathUtil.c(notification.getAnimatedX(), targetX, 0.22f));
            }

            float drawX = notification.getAnimatedX();
            float drawY = notification.getAnimatedY();

            // Entry/Exit slide offset based on individual notification animation
            if (isBottomRight || isTopRight) {
                drawX += (1.0f - anim) * 55.0f;
            } else if (isTopCenter) {
                drawY -= (1.0f - anim) * 20.0f;
            }

            minBoxX = Math.min(minBoxX, drawX);
            maxBoxX = Math.max(maxBoxX, drawX + cardWidth);
            minBoxY = Math.min(minBoxY, drawY);
            maxBoxY = Math.max(maxBoxY, drawY + cardHeight);

            // Styling colors
            int hudBg = getHudBackgroundColor();
            float baseA = ((hudBg >> 24) & 0xFF) / 255.0f;
            if (baseA <= 0.0f) baseA = 0.85f;
            int cardBg = ColorUtil.a(hudBg, (int) (opacityVal * baseA * 255.0f * anim));
            int borderColor = ColorUtil.a(255, 255, 255, (int) (0.12f * 255.0f * anim));

            // 1. Frosted card background (supports liquid glass and blur)
            if (blurVal > 0.0f && !event.d().e().e().isEmpty()) {
                event.d().b(event.h(), drawX, drawY, cardWidth, cardHeight, radius, cardBg, anim);
            } else {
                event.d().a(event.h(), drawX, drawY, cardWidth, cardHeight, radius, cardBg);
            }
            Widget.drawHudThemeGradient(event.d(), event.h(), drawX, drawY, cardWidth, cardHeight, radius, anim);

            // 2. Outer card contour border
            event.d().a(event.h(), drawX, drawY, cardWidth, cardHeight, radius, 1.0f, borderColor);

            // 3. Render Icon
            float padL = 9.0f;
            float textStartX = drawX + padL;
            if (notification.d() instanceof ItemStack) {
                ItemStack stack = (ItemStack) notification.d();
                float itemScale = isCompact ? 0.65f : 0.75f;
                float itemSize = 16.0f * itemScale;
                float iconY = drawY + (cardHeight - itemSize) / 2.0f - (isCompact ? 0.0f : 1.5f);
                Delta.h().d().j().a(event.i(), stack, drawX + padL, iconY, 0, anim, itemScale, false);
                textStartX += itemSize + 7.0f;
            } else {
                String iconStr = (String) notification.d();
                float iconSize = isCompact ? 9.5f : 11.5f;
                float iconW = Fonts.a.a(iconStr, iconSize);
                float iconH = Fonts.a.a(iconSize);
                int iconColor;
                if (parsed.statusType == 1) {
                    iconColor = ColorUtil.a(0xFF55EF8C, anim);
                } else if (parsed.statusType == 2) {
                    iconColor = ColorUtil.a(0xFFFF6B6B, anim);
                } else if (notification.e() != -1) {
                    iconColor = ColorUtil.a(notification.e(), anim);
                } else {
                    iconColor = ColorUtil.a(primaryColor, anim);
                }
                float iconY = drawY + (cardHeight - iconH) / 2.0f - (isCompact ? 0.0f : 1.5f) - 0.2f;
                Fonts.a.a(event.h(), iconStr, drawX + padL, iconY, iconSize, iconColor);
                textStartX += iconW + 7.0f;
            }

            // 4. Text rendering
            if (isCompact) {
                float textY = drawY + ((cardHeight - Fonts.e.a(titleFontSize)) / 2.0f) - 0.5f;
                renderTitleText(event, parsed.title, textStartX, textY, titleFontSize, tc1, tc2, anim);
                if (!parsed.subtitle.isEmpty()) {
                    float dotX = textStartX + Fonts.e.a(parsed.title, titleFontSize) + 5.0f;
                    Fonts.e.a(event.h(), "•", dotX, textY, 6.5f, ColorUtil.a(145, 148, 160, (int) (0.85f * 255.0f * anim)));
                    float subX = dotX + Fonts.e.a("•", 6.5f) + 5.0f;
                    int statusCol = getStatusColor(parsed.statusType, primaryColor, anim);
                    Fonts.e.a(event.h(), parsed.subtitle, subX, textY, subFontSize, statusCol);
                }
            } else {
                float titleY = drawY + 4.5f;
                renderTitleText(event, parsed.title, textStartX, titleY, titleFontSize, tc1, tc2, anim);

                if (!parsed.subtitle.isEmpty()) {
                    float subY = drawY + 14.5f;
                    int statusCol = getStatusColor(parsed.statusType, primaryColor, anim);
                    if (parsed.statusType == 1 || parsed.statusType == 2) {
                        Fonts.e.a(event.h(), "• " + parsed.subtitle, textStartX, subY, subFontSize, statusCol);
                    } else {
                        Fonts.e.a(event.h(), parsed.subtitle, textStartX, subY, subFontSize, statusCol);
                    }
                }
            }

            // 5. Embedded internal progress bar (sits cleanly inside the card with padding)
            if (this.progressBar.c().booleanValue()) {
                float elapsed = (float) notification.b().c();
                float total = (float) notification.f();
                float progress = total > 0.0f ? Math.max(0.0f, Math.min(1.0f, 1.0f - (elapsed / total))) : 0.75f;

                float barPadX = 8.0f;
                float barH = isCompact ? 1.5f : 1.8f;
                float barY = drawY + cardHeight - barH - (isCompact ? 1.8f : 2.5f);
                float totalTrackW = cardWidth - (barPadX * 2.0f);
                float activeBarW = totalTrackW * progress;

                // Subtle track underneath
                int trackColor = ColorUtil.a(255, 255, 255, (int) (0.08f * 255.0f * anim));
                event.d().a(event.h(), drawX + barPadX, barY, totalTrackW, barH, barH * 0.5f, trackColor);

                // Active progress bar with rounded ends
                if (activeBarW > 1.0f) {
                    int barCol1 = ColorUtil.a(tc1, anim);
                    int barCol2;
                    if (parsed.statusType == 1) {
                        barCol2 = ColorUtil.a(0xFF55EF8C, anim);
                    } else if (parsed.statusType == 2) {
                        barCol2 = ColorUtil.a(0xFFFF6B6B, anim);
                    } else if (notification.e() != -1) {
                        barCol2 = ColorUtil.a(notification.e(), anim);
                    } else {
                        barCol2 = ColorUtil.a(tc2, anim);
                    }
                    event.d().a(event.h(), drawX + barPadX, barY, activeBarW, barH, barH * 0.5f, barCol1, barCol2, barCol1, barCol2);
                }
            }
        }

        // Update DragInfo bounds so ChatScreen selection box matches active cards
        if (minBoxX < Float.MAX_VALUE) {
            j().a(minBoxX);
            j().b(minBoxY);
            j().c(Math.max(100.0f, maxBoxX - minBoxX));
            j().d(Math.max(20.0f, maxBoxY - minBoxY));
        }

        super.a(event);
    }

    private void renderTitleText(DrawEvent event, String title, float x, float y, float size, int tc1, int tc2, float anim) {
        if (title.equalsIgnoreCase("Egg") || title.equalsIgnoreCase("Egg Client")) {
            int c1Anim = ColorUtil.a(tc1, anim);
            int c2Anim = ColorUtil.a(tc2, anim);
            float time = (float) (System.currentTimeMillis() % 4000L) / 4000.0f;
            float cur = x;
            for (int i = 0; i < title.length(); i++) {
                String s = String.valueOf(title.charAt(i));
                float progress = (float) i / Math.max(1, title.length() - 1);
                float wave = 0.5f + 0.5f * (float) Math.sin((progress * 2.2f) - (time * Math.PI * 2.0));
                int color = ColorUtil.a(c1Anim, c2Anim, wave);
                Fonts.e.a(event.h(), s, cur, y, size, color);
                cur += Fonts.e.a(s, size);
            }
        } else {
            int whiteColor = ColorUtil.a(245, 248, 255, (int) (0.95f * 255.0f * anim));
            Fonts.e.a(event.h(), title, x, y, size, whiteColor);
        }
    }

    private int getStatusColor(int statusType, int primaryColor, float anim) {
        if (statusType == 1) {
            return ColorUtil.a(85, 239, 140, (int) (0.95f * 255.0f * anim));
        }
        if (statusType == 2) {
            return ColorUtil.a(255, 107, 107, (int) (0.95f * 255.0f * anim));
        }
        return ColorUtil.a(165, 170, 185, (int) (0.85f * 255.0f * anim));
    }

    @Override
    public void a(PacketEvent event) {
        if (this.f.c().booleanValue() && event.c()) {
            ItemPickupAnimationS2CPacket class_2775VarD = (ItemPickupAnimationS2CPacket) event.d();
            if (class_2775VarD instanceof ItemPickupAnimationS2CPacket) {
                ItemPickupAnimationS2CPacket itemPickupAnimationS2CPacket = class_2775VarD;
                ClientPlayerEntity class_746VarMethod_8469 = (ClientPlayerEntity) aM_.world.getEntityById(itemPickupAnimationS2CPacket.getCollectorEntityId());
                if (class_746VarMethod_8469 instanceof PlayerEntity) {
                    ClientPlayerEntity class_746Var = (ClientPlayerEntity) (PlayerEntity) class_746VarMethod_8469;
                    ItemEntity class_1542VarMethod_8469 = (ItemEntity) aM_.world.getEntityById(itemPickupAnimationS2CPacket.getEntityId());
                    if (class_1542VarMethod_8469 instanceof ItemEntity) {
                        ItemEntity itemEntity = class_1542VarMethod_8469;
                        if (class_746Var != aM_.player && !itemEntity.getStack().getName().getString().contains("Упс.") && ((itemEntity.getStack().contains(DataComponentTypes.CUSTOM_NAME) && itemEntity.getStack().contains(DataComponentTypes.LORE)) || itemEntity.getStack().isOf(Items.ENCHANTED_GOLDEN_APPLE))) {
                            Delta.h().d().m().a(new Notification(itemEntity.getStack().copy(), class_746Var.getName().copy().append(" подобрал ").append(itemEntity.getStack().getName()).append(itemPickupAnimationS2CPacket.getStackAmount() > 1 ? " x" + itemPickupAnimationS2CPacket.getStackAmount() : ""), 1500));
                        }
                    }
                }
            }
        }
        super.a(event);
    }

    @Override
    public void a(BackendEvent event) {
        String message = event.d().a().a(event.d().c(), "message");
        if ("friend".equals(event.d().b()) && this.g.c().booleanValue() && message != null) {
            Delta.h().d().m().a(new Notification("o", message, 5000));
        }
        if ("application".equals(event.d().b()) && message != null) {
            Delta.h().d().m().a(new Notification("o", message, 15000));
        }
        super.a(event);
    }
}
