package delta.handler;

import delta.handler.Handler_2;
import platform.inject.invokers.ClientPlayerInteractionManagerInvoker;
import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.util.InventoryUtil;
import delta.util.Look;

import delta.core.EventTarget;
import delta.core.Interface;
import delta.event.TickEvent;
import delta.handler.BaseHandler;
import delta.util.Rotation;

import java.util.ArrayList;
import java.util.List;
import lombok.Generated;
import net.minecraft.util.Hand;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.network.packet.c2s.play.BundleItemSelectedC2SPacket;

@Handler_2
public class UseableHandler extends BaseHandler implements Interface {
    private final List<a> b = new ArrayList();

    @Generated
    public List<a> a() {
        return this.b;
    }

    @EventTarget
    public void a(TickEvent event) {
        if (!this.b.isEmpty()) {
            a task = (a) this.b.getFirst();
            int hotbar = task.a().getItem() == Items.SPLASH_POTION ? InventoryUtil.b(task.a(), true) : InventoryUtil.a(task.a().getItem(), true);
            int inventory = task.a().getItem() == Items.SPLASH_POTION ? InventoryUtil.b(task.a(), false) : InventoryUtil.a(task.a().getItem(), false);
            if (task.d() == -1 && hotbar == -1 && inventory == -1) {
                this.b.remove(task);
                return;
            }
            task.c(task.d() + 1);
            if (task.d() == 0) {
                task.a(aM_.player.getInventory().getSelectedSlot());
                if (hotbar != -1) {
                    task.b(hotbar);
                    if (hotbar != aM_.player.getInventory().getSelectedSlot()) {
                        a(hotbar);
                        return;
                    }
                    return;
                }
                if (inventory != -1) {
                    int bundle = InventoryUtil.a(aM_.player.getInventory().getStack(inventory), task.a());
                    if (bundle != -1) {
                        aM_.player.networkHandler.sendPacket(new BundleItemSelectedC2SPacket(inventory < 9 ? 36 + inventory : inventory, bundle));
                    }
                    task.b((bundle == -1 || !aM_.player.getMainHandStack().isEmpty()) ? inventory : task.b());
                    Delta.h().d().v().a().a(inventory, aM_.player.getInventory().getSelectedSlot(), 1);
                    return;
                }
                return;
            }
            if (task.d() == 1) {
                a(task);
                if (aM_.player.getInventory().getStack(task.c()).contains(DataComponentTypes.BUNDLE_CONTENTS)) {
                    aM_.player.getInventory().setStack(task.b(), ItemStack.EMPTY);
                    Delta.h().d().v().a().a(task.c(), 36 + task.b(), 1);
                } else if (task.c() > 8) {
                    Delta.h().d().v().a().a(task.b(), task.c(), 1);
                } else if (task.b() != aM_.player.getInventory().getSelectedSlot()) {
                    a(task.b());
                }
                this.b.remove(task);
            }
        }
    }

    public void a(int slot) {
        aM_.player.getInventory().setSelectedSlot(slot);
    }

    public void a(a task) {
        ((platform.inject.invokers.ClientPlayerInteractionManagerInvoker) aM_.interactionManager).invokeSendSequencedPacket(aM_.world, sequence -> {
            return new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, aM_.player.getYaw(), aM_.player.getPitch());
        });
    }

    public void a(ItemStack itemStack) {
        this.b.add(new a(itemStack));
    }

    public static final class a {
        private final ItemStack a;
        private int b;
        private int c;
        private int d = -1;

        @Generated
        public void a(int selectedSlot) {
            this.b = selectedSlot;
        }

        @Generated
        public void b(int itemSlot) {
            this.c = itemSlot;
        }

        @Generated
        public void c(int ticks) {
            this.d = ticks;
        }

        @Generated
        public ItemStack a() {
            return this.a;
        }

        @Generated
        public int b() {
            return this.b;
        }

        @Generated
        public int c() {
            return this.c;
        }

        @Generated
        public int d() {
            return this.d;
        }

        public a(ItemStack itemStack) {
            this.a = itemStack;
        }
    }
}
