package delta.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import delta.core.EventTarget;
import delta.event.AttackEvent;
import delta.event.TickEvent;
import delta.util.ChatUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Command(a = "fp")
public class FPCommand extends BaseCommand {
    private static final Map<String, OtherClientPlayerEntity> fakePlayers = new LinkedHashMap<>();

    @Override
    public void a(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(a("add").executes(context -> {
            spawnFakePlayer(null);
            return 1;
        }).then(b("ник").executes(context -> {
            String name = a((CommandContext<CommandSource>) context, "ник");
            spawnFakePlayer(name);
            return 1;
        })));

        builder.then(a("remove").executes(context -> {
            removeFakePlayer(null);
            return 1;
        }).then(b("ник").suggests(a(fakePlayers::keySet, s -> s)).executes(context -> {
            String name = a((CommandContext<CommandSource>) context, "ник");
            removeFakePlayer(name);
            return 1;
        })));

        builder.then(a("clear").executes(context -> {
            clearAllFakePlayers();
            return 1;
        }));

        builder.then(a("list").executes(context -> {
            listFakePlayers();
            return 1;
        }));

        builder.executes(context -> {
            ChatUtil.a((Object) "Использование: .fp add [ник] | .fp remove [ник] | .fp clear | .fp list");
            return 1;
        });
    }

    private void spawnFakePlayer(String requestedName) {
        ClientPlayerEntity player = aM_.player;
        ClientWorld world = aM_.world;
        if (player == null || world == null) {
            ChatUtil.a((Object) "Вы должны находиться в мире!");
            return;
        }

        String name = (requestedName != null && !requestedName.trim().isEmpty())
                ? requestedName.trim()
                : "FakePlayer" + (fakePlayers.isEmpty() ? "" : "_" + (fakePlayers.size() + 1));

        if (fakePlayers.containsKey(name.toLowerCase())) {
            ChatUtil.a((Object) ("Фейк игрок с именем " + name + " уже существует."));
            return;
        }

        UUID uuid = UUID.randomUUID();
        GameProfile profile = new GameProfile(uuid, name);
        if (player.getGameProfile() != null && player.getGameProfile().properties() != null) {
            player.getGameProfile().properties().entries().forEach(entry -> {
                profile.properties().put(entry.getKey(), entry.getValue());
            });
        }

        OtherClientPlayerEntity fakePlayer = new OtherClientPlayerEntity(world, profile);
        fakePlayer.copyPositionAndRotation(player);
        fakePlayer.resetPosition();
        fakePlayer.headYaw = player.headYaw;
        fakePlayer.bodyYaw = player.bodyYaw;
        fakePlayer.setPitch(player.getPitch());
        fakePlayer.setYaw(player.getYaw());

        fakePlayer.getInventory().clone(player.getInventory());
        fakePlayer.setHealth(20.0f);
        fakePlayer.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));

        world.addEntity(fakePlayer);
        fakePlayers.put(name.toLowerCase(), fakePlayer);

        ChatUtil.a((Object) (name + " успешно создан."));
    }

    private void removeFakePlayer(String requestedName) {
        ClientWorld world = aM_.world;
        if (fakePlayers.isEmpty()) {
            ChatUtil.a((Object) "Нет активных фейк игроков.");
            return;
        }

        if (requestedName != null && !requestedName.trim().isEmpty()) {
            String key = requestedName.trim().toLowerCase();
            OtherClientPlayerEntity fakePlayer = fakePlayers.remove(key);
            if (fakePlayer != null) {
                if (world != null) {
                    world.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
                }
                fakePlayer.discard();
                ChatUtil.a((Object) ("Фейк игрок §c" + requestedName + "§f удален."));
            } else {
                ChatUtil.a((Object) ("Фейк игрок §e" + requestedName + "§f не найден."));
            }
        } else {
            String lastKey = null;
            for (String k : fakePlayers.keySet()) {
                lastKey = k;
            }
            if (lastKey != null) {
                OtherClientPlayerEntity fakePlayer = fakePlayers.remove(lastKey);
                if (fakePlayer != null) {
                    if (world != null) {
                        world.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
                    }
                    fakePlayer.discard();
                    ChatUtil.a((Object) ("Фейк игрок §c" + fakePlayer.getName().getString() + "§f удален."));
                }
            }
        }
    }

    private void clearAllFakePlayers() {
        ClientWorld world = aM_.world;
        if (fakePlayers.isEmpty()) {
            ChatUtil.a((Object) "Список фейк игроков пуст.");
            return;
        }
        int count = fakePlayers.size();
        for (OtherClientPlayerEntity fakePlayer : fakePlayers.values()) {
            if (world != null) {
                world.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
            }
            fakePlayer.discard();
        }
        fakePlayers.clear();
        ChatUtil.a((Object) ("Все фейк игроки (" + count + ") успешно удалены."));
    }

    private void listFakePlayers() {
        if (fakePlayers.isEmpty()) {
            ChatUtil.a((Object) "Список фейк игроков пуст.");
            return;
        }
        ChatUtil.a((Object) ("Список фейк игроков (" + fakePlayers.size() + "):"));
        for (OtherClientPlayerEntity fp : fakePlayers.values()) {
            ChatUtil.a((Object) (" - " + fp.getName().getString() + " [HP: " + (int) fp.getHealth() + "/20, X: " + (int) fp.getX() + ", Y: " + (int) fp.getY() + ", Z: " + (int) fp.getZ() + "]"));
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        Entity target = event.b();
        if (target instanceof OtherClientPlayerEntity && fakePlayers.containsValue(target)) {
            OtherClientPlayerEntity fakePlayer = (OtherClientPlayerEntity) target;
            event.a(true);

            ClientPlayerEntity player = aM_.player;
            ClientWorld world = aM_.world;
            if (player == null || world == null) return;

            player.resetTicksSinceLastAttack();

            // 1 удар = 1 хп урона (всего 20 хп)
            float curHp = fakePlayer.getHealth();
            float nextHp = curHp - 1.0f;

            if (nextHp <= 0.0f) {
                // Дошло до нуля -> срабатывает тотем!
                fakePlayer.setHealth(20.0f);
                fakePlayer.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));

                fakePlayer.handleStatus(EntityStatuses.USE_TOTEM_OF_UNDYING);
                world.playSoundClient(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f, false);
                world.playSoundClient(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 1.0f, false);

                for (int i = 0; i < 45; i++) {
                    double vx = (Math.random() - 0.5) * 0.45;
                    double vy = Math.random() * 0.45;
                    double vz = (Math.random() - 0.5) * 0.45;
                    world.addParticleClient(ParticleTypes.TOTEM_OF_UNDYING, fakePlayer.getX(), fakePlayer.getY() + 1.0, fakePlayer.getZ(), vx, vy, vz);
                }

                fakePlayer.animateDamage(player.getYaw());
            } else {
                // Обычный удар: отнимаем 1 хп
                fakePlayer.setHealth(nextHp);

                fakePlayer.animateDamage(player.getYaw());

                // Звуки критов вместо звука урона как в лаве
                boolean isCrit = (player.fallDistance > 0.0f && !player.isOnGround()) || !player.isOnGround();
                float pitch = isCrit ? 1.0f : 0.95f;
                world.playSoundClient(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, pitch, false);
                world.playSoundClient(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.7f, 1.0f, false);

                int critCount = isCrit ? 16 : 8;
                for (int i = 0; i < critCount; i++) {
                    world.addParticleClient(ParticleTypes.CRIT, fakePlayer.getX(), fakePlayer.getY() + 1.0, fakePlayer.getZ(),
                            (Math.random() - 0.5) * 0.5, Math.random() * 0.35 + 0.1, (Math.random() - 0.5) * 0.5);
                }

                for (int i = 0; i < 4; i++) {
                    world.addParticleClient(ParticleTypes.DAMAGE_INDICATOR, fakePlayer.getX(), fakePlayer.getY() + 1.0, fakePlayer.getZ(),
                            (Math.random() - 0.5) * 0.2, 0.1, (Math.random() - 0.5) * 0.2);
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (aM_.world == null) {
            fakePlayers.clear();
        } else {
            fakePlayers.values().removeIf(Entity::isRemoved);
        }
    }
}
