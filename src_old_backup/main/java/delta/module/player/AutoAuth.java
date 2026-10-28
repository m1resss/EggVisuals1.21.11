package delta.module.player;

import static delta.core.Interface.aM_;
import delta.core.Module;
import delta.util.ServerUtil;

import delta.core.Category;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.core.ModuleRegister;
import delta.event.PacketEvent;
import delta.event.TickEvent;

import delta.setting.StringSetting;
import lombok.Generated;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;

@ModuleRegister(a = "Auto Auth", b = "Автоматически вводит пароль при авторизации и регистрации", c = Category.Player)
public class AutoAuth extends Module implements Interface {
    private final StringSetting b = (StringSetting) new StringSetting("Пароль авторизации", "").a();
    private final StringSetting c = (StringSetting) new StringSetting("Пароль регистрации", "").a();
    private String d;

    @Generated
    public StringSetting q() {
        return this.b;
    }

    @Generated
    public StringSetting r() {
        return this.c;
    }

    @Generated
    public String s() {
        return this.d;
    }

    public AutoAuth() {
        a(this.b, this.c);
    }

    @EventTarget
    public void a(PacketEvent eventPacket) {
        if (eventPacket.c()) {
            GameMessageS2CPacket class_7439VarD = (GameMessageS2CPacket) eventPacket.d();
            if (class_7439VarD instanceof GameMessageS2CPacket) {
                GameMessageS2CPacket packet = class_7439VarD;
                String message = packet.content().getString();
                if ((message.contains("Зарегистрируйтесь") || message.contains("/reg") || message.contains("/register")) && !this.c.c().isEmpty()) {
                    this.d = "/reg " + this.c.c();
                }
                if ((message.contains("Авторизуйтесь") || message.contains("Войдите в игру") || message.contains("/login")) && !this.b.c().isEmpty()) {
                    this.d = "/login " + this.b.c();
                }
            }
        }
    }

    @EventTarget
    public void a(TickEvent tickEvent) {
        if (this.d != null) {
            if (ServerUtil.a.a() && ServerUtil.a.c()) {
                return;
            }
            aM_.player.networkHandler.sendChatMessage(this.d);
            this.d = null;
        }
    }
}
