package delta.module.misc;

import platform.inject.invokers.MinecraftClientInvoker;
import delta.core.Interface;

import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.core.Module;
import delta.util.ChatUtil;
import delta.util.InventoryUtil;
import delta.util.Look;
import delta.util.MathUtil;

import delta.core.Category;
import delta.core.EventTarget;
import delta.core.ModuleRegister;
import delta.event.TickEvent;
import delta.util.Rotation;

import net.minecraft.util.Hand;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

@ModuleRegister(a = "Clan Upgrader", b = "Быстро прокачивает клан с помощью редстоуна и факела", c = Category.Misc)
public class ClanUpgrader extends Module {
    private int b;

    @Override
    public void c() {
        super.c();
        if (this.b != -1) {
            aM_.player.getInventory().setSelectedSlot(this.b);
            this.b = -1;
        }
    }

    @EventTarget
    public void a(TickEvent event) {
        int redstone = InventoryUtil.a(Items.REDSTONE, true);
        int torch = InventoryUtil.a(Items.TORCH, true);
        int target = redstone != -1 ? redstone : torch;
        if (redstone == -1 && torch == -1) {
            ChatUtil.a((Object) "Вам необходимо иметь факел или редстоун в хотбаре");
            a();
            return;
        }
        float randomPitch = ((float) (Math.sin(System.currentTimeMillis() / 1220.0d) * ((double) (Math.abs(90.0f - aM_.player.getPitch()) / 8.0f)))) + MathUtil.a(-0.1f, 0.1f);
        Rotation rotation = new Rotation(Look.b() + MathUtil.a(-1.0f, 1.0f), MathUtil.b(88.0f + randomPitch, -90.0f, 90.0f));
        Delta.h().d().k().a(rotation, 90.0f, 1, 1);
        if (this.b == -1) {
            this.b = aM_.player.getInventory().getSelectedSlot();
        }
        if (aM_.player.getInventory().getSelectedSlot() != target) {
            aM_.player.getInventory().setSelectedSlot(target);
        }
        if (Rotation.b().a(rotation) <= 1.0d) {
            BlockPos position = aM_.player.getBlockPos();
            if (aM_.world.getBlockState(position).isOf(Blocks.REDSTONE_WIRE) || aM_.world.getBlockState(position).isOf(Blocks.TORCH)) {
                aM_.interactionManager.attackBlock(position, Direction.UP);
                aM_.player.swingHand(Hand.MAIN_HAND);
            } else {
                ((platform.inject.invokers.MinecraftClientInvoker) aM_).invokeDoItemUse();
            }
        }
    }
}
