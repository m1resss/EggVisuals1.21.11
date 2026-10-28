package delta.discord;

import delta.core.NativeMethodLookup;
import delta.core.Delta;

import delta.config.BaseProcessor;
import delta.discord.ActivityType;
import delta.discord.DiscordIPC;

import delta.api.Compile;
import java.io.IOException;
import lombok.Generated;

public class DiscordProcessor extends BaseProcessor {
    private DiscordIPC b;

    @Override
    @Compile
    public void setup() {
    }

    @Override
    public void unSetup() {
    }

    static {
        NativeMethodLookup.lookup(DiscordProcessor.class, 25);
    }

    @Generated
    public DiscordIPC a() {
        return this.b;
    }

    public void a(Void result, Throwable ex) {
        if (ex == null) {
            try {
                this.b.a(new Activity.a().a(ActivityType.PLAYING).b("username: " + Delta.h().g().b()).a("build: " + (Delta.h().c() != null ? "development" : "public")).a("https://deltacracked.pidorasi/api/logotype.png", "https://deltacracked.pidorasi/").a(System.currentTimeMillis() / 1000).a("https://i.imgur.com/E6dkFRc.jpeg", "https://deltacracked.pidorasi/").c("Купить", "https://deltacracked.pidorasi/").c("Новости", "https://t.me/dlcformine").a());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
