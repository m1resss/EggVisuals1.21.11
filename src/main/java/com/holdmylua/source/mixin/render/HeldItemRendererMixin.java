package com.holdmylua.source.mixin.render;

import com.holdmylua.source.LuaTestHMI;
import com.holdmylua.source.access.AlternateBlockRenderer;
import com.holdmylua.source.access.ItemStackAccessor;
import com.holdmylua.source.access.LivingEntityAccessor;
import com.holdmylua.source.global.DispatcherStorage;
import com.holdmylua.source.global.GlobalsStorage;
import com.holdmylua.source.global.item_model.ItemModelContext;
import com.holdmylua.source.global.item_model.ItemModelStorage;
import com.holdmylua.source.lua_runtime.LuaScriptCache;
import com.holdmylua.source.lua_runtime.ScriptHolder;
import com.holdmylua.source.patricles.Particle;
import com.holdmylua.source.patricles.ParticleRenderManager;
import java.util.ArrayList;
import java.util.List;
import javax.script.ScriptException;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.entity.BellBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.Attachment;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.block.entity.state.BellBlockEntityRenderState;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={HeldItemRenderer.class})
public abstract class HeldItemRendererMixin {
    @Unique
    boolean mainHandSwitchEvent = false;
    @Unique
    boolean offHandSwitchEvent = false;
    @Unique
    Item prevMainHand = Items.AIR;
    @Unique
    Item prevOffHand = Items.AIR;
    @Unique
    private final ArrayList<Particle> particles = new ArrayList();
    @Shadow
    @Final
    private MinecraftClient client;
    @Shadow
    private ItemStack mainHand;
    @Shadow
    private ItemStack offHand;
    @Unique
    private boolean swingMHand = false;
    @Unique
    private boolean swingOHand = false;
    @Unique
    private float mainHandSwingProgress = 0.0f;
    @Unique
    private float offHandSwingProgress = 0.0f;

    @Shadow
    protected abstract void renderArmHoldingItem(MatrixStack var1, OrderedRenderCommandQueue var2, int var3, float var4, float var5, Arm var6);

    @Shadow
    protected abstract void renderFirstPersonItem(AbstractClientPlayerEntity var1, float var2, float var3, Hand var4, float var5, ItemStack var6, float var7, MatrixStack var8, OrderedRenderCommandQueue var9, int var10);

    @Shadow
    protected abstract void renderFirstPersonMap(MatrixStack var1, OrderedRenderCommandQueue var2, int var3, ItemStack var4);

    @Shadow
    public abstract void renderItem(LivingEntity var1, ItemStack var2, ItemDisplayContext var3, MatrixStack var4, OrderedRenderCommandQueue var5, int var6);

    @Unique
    private void copyAppearanceComponents(ItemStack source, ItemStack target) {
        if (source.contains(DataComponentTypes.ENCHANTMENTS)) {
            target.set(DataComponentTypes.ENCHANTMENTS, ((ItemEnchantmentsComponent)source.get(DataComponentTypes.ENCHANTMENTS)));
        }
        if (source.contains(DataComponentTypes.ITEM_MODEL)) {
            target.set(DataComponentTypes.ITEM_MODEL, ((Identifier)source.get(DataComponentTypes.ITEM_MODEL)));
        }
        if (source.contains(DataComponentTypes.CUSTOM_MODEL_DATA)) {
            target.set(DataComponentTypes.CUSTOM_MODEL_DATA, ((CustomModelDataComponent)source.get(DataComponentTypes.CUSTOM_MODEL_DATA)));
        }
        if (source.contains(DataComponentTypes.CUSTOM_DATA)) {
            target.set(DataComponentTypes.CUSTOM_DATA, ((NbtComponent)source.get(DataComponentTypes.CUSTOM_DATA)));
        }
    }

    @Unique
    private void applyArmMatrices(MatrixStack matrices, int light, float equipProgress, float swingProgress, Arm arm) {
        boolean bl = arm != Arm.LEFT;
        float f = bl ? 1.0f : -1.0f;
        float g = MathHelper.sqrt((float)swingProgress);
        float h = -0.3f * MathHelper.sin((double)(g * (float)Math.PI));
        float i = 0.4f * MathHelper.sin((double)(g * ((float)Math.PI * 2)));
        float j = -0.4f * MathHelper.sin((double)(swingProgress * (float)Math.PI));
        matrices.translate(f * (h + 0.64000005f), i + -0.6f + equipProgress * -0.6f, j + -0.71999997f);
        matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_Y.rotationDegrees(f * 45.0f));
        float k = MathHelper.sin((double)(swingProgress * swingProgress * (float)Math.PI));
        float l = MathHelper.sin((double)(g * (float)Math.PI));
        matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_Y.rotationDegrees(f * l * 70.0f));
        matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_Z.rotationDegrees(f * k * -20.0f));
        ClientPlayerEntity abstractClientPlayerEntity = this.client.player;
        matrices.translate(f * -1.0f, 3.6f, 3.5f);
        matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_Z.rotationDegrees(f * 120.0f));
        matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_X.rotationDegrees(200.0f));
        matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_Y.rotationDegrees(f * -135.0f));
        matrices.translate(f * 5.6f, 0.0f, 0.0f);
    }

    @Unique
    private void itemPose(MatrixStack matrices, ItemStack item, boolean bl, float swingProgress, AbstractClientPlayerEntity player, boolean mainHand, Hand hand, float equipProgress, float mainHandSwingProgress, float offHandSwingProgress, boolean mainHandSwitchEvent, boolean offHandSwitchEvent, boolean swingMHand, boolean swingOHand, boolean interact, boolean blockBreaking, List<Particle> particles) throws ScriptException, NoSuchMethodException {
        int l = bl ? 1 : -1;
        matrices.translate(0.5 * (double)l, -0.15, -0.85);
        matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_X.rotationDegrees(15.0f), 0.5f, 0.5f, 0.5f);
        matrices.scale(0.9f, 0.9f, 0.9f);
        ScriptHolder.itemScriptCache.execute(matrices, bl, GlobalsStorage.registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        for (LuaScriptCache scriptCache : ScriptHolder.itemAddonsCache) {
            scriptCache.execute(matrices, bl, GlobalsStorage.registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        }
    }

    @Unique
    private void mainHandPose(MatrixStack matrices, ItemStack item, boolean bl, float swingProgress, float equipProgress, AbstractClientPlayerEntity player, boolean mainHand, Hand hand, float mainHandSwingProgress, float offHandSwingProgress, boolean mainHandSwitchEvent, boolean offHandSwitchEvent, boolean swingMHand, boolean swingOHand, boolean interact, boolean blockBreaking, List<Particle> particles) throws ScriptException, NoSuchMethodException {
        int l = bl ? 1 : -1;
        ScriptHolder.handRelativeScriptCache.execute(matrices, bl, GlobalsStorage.registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        for (LuaScriptCache scriptCache : ScriptHolder.handRelativeAddonsCache) {
            scriptCache.execute(matrices, bl, GlobalsStorage.registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        }
        if (!item.isEmpty()) {
            matrices.translate(1.5 * (double)l, -0.3, -0.6);
            matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_X.rotationDegrees(15.0f), 0.5f * (float)l, 0.5f, 0.5f);
            matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_Y.rotationDegrees((float)(35 * l)), 0.5f * (float)l, 0.5f, 0.5f);
            matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_Z.rotationDegrees((float)(-65 * l)), 0.5f * (float)l, 0.5f, 0.5f);
            matrices.scale(0.9f, 0.9f, 0.9f);
        }
    }

    @Unique
    private void scenePoseMain(MatrixStack matrices, ItemStack item, boolean bl, float swingProgress, float equipProgress, AbstractClientPlayerEntity player, boolean mainHand, Hand hand, float mainHandSwingProgress, float offHandSwingProgress, boolean mainHandSwitchEvent, boolean offHandSwitchEvent, boolean swingMHand, boolean swingOHand, boolean interact, boolean blockBreaking, List<Particle> particles) throws ScriptException, NoSuchMethodException {
        ScriptHolder.handScriptCache.execute(matrices, bl, GlobalsStorage.registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        for (LuaScriptCache scriptCache : ScriptHolder.handAddonsCache) {
            scriptCache.execute(matrices, bl, GlobalsStorage.registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        }
        if (!item.isEmpty()) {
            matrices.translate(0.0, -0.35, 0.2);
        }
    }

    @Redirect(method={"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"))
    private void renderOverhaul(HeldItemRenderer instance, AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light) throws ScriptException, NoSuchMethodException {
        if (!egg.client.module.render.HMI.usesAnimations()) {
            ItemStack main = player.getMainHandStack();
            ItemStack off = player.getOffHandStack();
            boolean mainMap = main.isOf(Items.FILLED_MAP);
            boolean offMap = off.isOf(Items.FILLED_MAP);
            if ((mainMap && offMap && hand == Hand.OFF_HAND)
                    || (mainMap && off.isEmpty() && hand == Hand.OFF_HAND)
                    || (offMap && main.isEmpty() && hand == Hand.MAIN_HAND)) {
                return;
            }
            if (player.isUsingItem()) {
                Hand activeHand = player.getActiveHand();
                ItemStack active = player.getActiveItem();
                if (active.isOf(Items.BOW) || active.isOf(Items.CROSSBOW) || active.isOf(Items.SPYGLASS)) {
                    if (hand != activeHand) return;
                }
            }
            this.renderFirstPersonItem(player, tickProgress, pitch, hand, swingProgress, item, equipProgress, matrices, orderedRenderCommandQueue, light);
            return;
        }
        if (!player.isUsingSpyglass()) {
            ((ItemStackAccessor)(Object)item).hMI5_0$setTransform(-1);
            boolean bl = hand == Hand.MAIN_HAND;
            boolean interact = false;
            boolean blockBreaking = false;
            if (bl && player.isUsingItem() && player.getActiveHand() != hand && (player.getOffHandStack().getUseAction() == UseAction.BOW || player.getOffHandStack().getUseAction() == UseAction.BOW || player.getOffHandStack().getUseAction() == UseAction.CROSSBOW && !CrossbowItem.isCharged((ItemStack)player.getOffHandStack()))) {
                item = Items.AIR.getDefaultStack();
            }
            if (!bl && player.isUsingItem() && player.getActiveHand() != hand && (player.getMainHandStack().getUseAction() == UseAction.BOW || player.getMainHandStack().getUseAction() == UseAction.CROSSBOW && !CrossbowItem.isCharged((ItemStack)player.getMainHandStack()))) {
                item = Items.AIR.getDefaultStack();
            }
            if (bl) {
                boolean bl2 = this.mainHandSwitchEvent = item.getItem() != this.prevMainHand;
            }
            if (!bl) {
                this.offHandSwitchEvent = item.getItem() != this.prevOffHand;
            }
            matrices.push();
            if (player instanceof LivingEntityAccessor) {
                float offHandProgress;
                float mainHandProgress;
                LivingEntityAccessor accessor = (LivingEntityAccessor)player;
                this.mainHandSwingProgress = mainHandProgress = accessor.hMI5_0$getMainHandSwingProgress(tickProgress);
                this.offHandSwingProgress = offHandProgress = accessor.hMI5_0$getOffHandSwingProgress(tickProgress);
                swingProgress = bl ? mainHandProgress : offHandProgress;
                this.swingMHand = accessor.hMI5_0$getMHandEvent();
                this.swingOHand = accessor.hMI5_0$getOHandEvent();
                interact = bl ? accessor.hMI5_0$getMInteract() : accessor.hMI5_0$getOInteract();
                blockBreaking = accessor.hMI5_0$getBlockBreak();
            }
            Arm arm = bl ? player.getMainArm() : player.getMainArm().getOpposite();
            boolean bl2 = arm == Arm.RIGHT;
            int l = bl2 ? 1 : -1;
            this.scenePoseMain(matrices, item, bl2, swingProgress, equipProgress, player, bl, hand, this.mainHandSwingProgress, this.offHandSwingProgress, this.mainHandSwitchEvent, this.offHandSwitchEvent, this.swingMHand, this.swingOHand, interact, blockBreaking, this.particles);
            matrices.push();
            this.mainHandPose(matrices, item, bl2, swingProgress, equipProgress, player, bl, hand, this.mainHandSwingProgress, this.offHandSwingProgress, this.mainHandSwitchEvent, this.offHandSwitchEvent, this.swingMHand, this.swingOHand, interact, blockBreaking, this.particles);
            int combinedLight = LightmapTextureManager.applyEmission((int)light, (int)Block.getBlockFromItem((Item)item.getItem()).getDefaultState().getLuminance());
            if (player.isInvisible()) {
                this.applyArmMatrices(matrices, combinedLight, 0.0f, 0.0f, arm);
            } else {
                this.renderArmHoldingItem(matrices, orderedRenderCommandQueue, combinedLight, 0.0f, 0.0f, arm);
            }
            matrices.pop();
            matrices.push();
            if (!(Block.getBlockFromItem((Item)item.getItem()) == Blocks.AIR || Block.getBlockFromItem((Item)item.getItem()).getDefaultState().isIn(BlockTags.INSIDE_STEP_SOUND_BLOCKS) || Block.getBlockFromItem((Item)item.getItem()).getDefaultState().isIn(BlockTags.CROPS) || item.getUseAction() == UseAction.EAT || item.isOf(Items.REDSTONE) || item.isIn(ItemTags.BANNERS) || item.isIn(ItemTags.SKULLS) || !GlobalsStorage.renderAsBlock.getOrDefault(item.getItem().toString(), true).booleanValue())) {
                swingProgress = 0.0f;
                BlockState blockState = Block.getBlockFromItem((Item)item.getItem()).getDefaultState();
                this.itemPose(matrices, item, bl2, swingProgress, player, bl, hand, equipProgress, this.mainHandSwingProgress, this.offHandSwingProgress, this.mainHandSwitchEvent, this.offHandSwitchEvent, this.swingMHand, this.swingOHand, interact, blockBreaking, this.particles);
                matrices.translate(0.22 * (double)l, 0.25, 0.2);
                if (item.isOf(Items.LEVER) || blockState.isIn(BlockTags.BUTTONS)) {
                    blockState = (BlockState)blockState.with((Property)Properties.BLOCK_FACE, (Comparable)BlockFace.FLOOR);
                }
                if (item.getName().toString().toLowerCase().contains("torch") || Block.getBlockFromItem((Item)item.getItem()) instanceof LanternBlock || blockState.isIn(BlockTags.ALL_HANGING_SIGNS)) {
                    matrices.translate(-0.05 * (double)l, 0.0, 0.0);
                    matrices.scale(1.75f, 1.75f, 1.75f);
                } else {
                    matrices.translate(-0.25 * (double)l, -0.05, 0.0);
                }
                matrices.push();
                matrices.scale(0.3f, 0.3f, 0.3f);
                matrices.translate(-0.9 * (double)l, -0.45, -0.7);
                matrices.pop();
                if (!bl2) {
                    matrices.translate(-0.3f, 0.0f, 0.0f);
                }
                matrices.scale(0.3f, 0.3f, 0.3f);
                matrices.translate(-0.9 * (double)l, -0.45, -0.7);
                if (item.isOf(Items.BELL)) {
                    BellBlockEntity bellBlockEntity = new BellBlockEntity(BlockPos.ORIGIN, Blocks.BELL.getDefaultState());
                    BellBlockEntityRenderState state = new BellBlockEntityRenderState();
                    state.lightmapCoordinates = light;
                    this.client.getBlockEntityRenderDispatcher().get((BlockEntity)bellBlockEntity).render((BlockEntityRenderState)state, matrices, orderedRenderCommandQueue, new CameraRenderState());
                    ((AlternateBlockRenderer)this.client.getBlockRenderManager()).renderSingleBlockWithEmission((BlockState)Blocks.BELL.getDefaultState().with((Property)Properties.ATTACHMENT, (Comparable)Attachment.CEILING), matrices, orderedRenderCommandQueue, light, this.client.world, player);
                } else {
                    if (blockState.contains((Property)Properties.DOUBLE_BLOCK_HALF)) {
                        matrices.push();
                        matrices.translate(0.0f, 1.0f, 0.0f);
                        ((AlternateBlockRenderer)this.client.getBlockRenderManager()).renderSingleBlockWithEmission((BlockState)blockState.with((Property)Properties.DOUBLE_BLOCK_HALF, (Comparable)DoubleBlockHalf.UPPER), matrices, orderedRenderCommandQueue, light, this.client.world, player);
                        matrices.pop();
                    }
                    ((AlternateBlockRenderer)this.client.getBlockRenderManager()).renderSingleBlockWithEmission(blockState, matrices, orderedRenderCommandQueue, light, this.client.world, player);
                }
                matrices.push();
                if (!bl2) {
                    matrices.translate(1.0f, 0.0f, 0.0f);
                }
                ParticleRenderManager.draw(this.particles, matrices, orderedRenderCommandQueue, "ITEM", hand, light, player, tickProgress);
                matrices.pop();
            } else if (item.getUseAction() == UseAction.BLOCK || item.getUseAction() == UseAction.BRUSH || item.getUseAction() == UseAction.TRIDENT) {
                ItemStack renderStack2;
                this.itemPose(matrices, item, bl2, swingProgress, player, bl, hand, equipProgress, this.mainHandSwingProgress, this.offHandSwingProgress, this.mainHandSwitchEvent, this.offHandSwitchEvent, this.swingMHand, this.swingOHand, interact, blockBreaking, this.particles);
                ItemStack renderStack = new ItemStack((ItemConvertible)item.getItem(), item.getCount());
                this.copyAppearanceComponents(item, renderStack);
                ItemStack itemStack = renderStack2 = bl ? GlobalsStorage.mainHandItem : GlobalsStorage.offHandItem;
                if (!renderStack2.isOf(Items.AIR)) {
                    renderStack = renderStack2;
                }
                DispatcherStorage.setItem(item);
                ItemModelStorage.addData(new ItemModelContext(bl2, swingProgress, player, hand, bl, LuaTestHMI.deltaTime, equipProgress, this.mainHandSwingProgress, this.offHandSwingProgress, this.mainHandSwitchEvent, this.offHandSwitchEvent, this.swingMHand, this.swingOHand, interact, blockBreaking, item), item);
                this.renderItem((LivingEntity)player, renderStack, bl2 ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND, matrices, orderedRenderCommandQueue, light);
            } else {
                this.itemPose(matrices, item, bl2, swingProgress, player, bl, hand, equipProgress, this.mainHandSwingProgress, this.offHandSwingProgress, this.mainHandSwitchEvent, this.offHandSwitchEvent, this.swingMHand, this.swingOHand, interact, blockBreaking, this.particles);
                if (item.contains(DataComponentTypes.MAP_ID)) {
                    matrices.push();
                    matrices.translate(-0.05 * (double)l, 0.2, 0.1);
                    matrices.multiply((Quaternionfc)RotationAxis.POSITIVE_Y.rotationDegrees((float)(-12 * l)));
                    this.renderFirstPersonMap(matrices, orderedRenderCommandQueue, light, item);
                    matrices.pop();
                } else {
                    ItemStack renderStack;
                    ItemStack itemStack = renderStack = bl ? GlobalsStorage.mainHandItem : GlobalsStorage.offHandItem;
                    if (renderStack.isOf(Items.AIR)) {
                        renderStack = item;
                    }
                    DispatcherStorage.setItem(item);
                    ItemModelStorage.addData(new ItemModelContext(bl2, swingProgress, player, hand, bl, LuaTestHMI.deltaTime, equipProgress, this.mainHandSwingProgress, this.offHandSwingProgress, this.mainHandSwitchEvent, this.offHandSwitchEvent, this.swingMHand, this.swingOHand, interact, blockBreaking, item), item);
                    this.renderItem((LivingEntity)player, renderStack, bl2 ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND, matrices, orderedRenderCommandQueue, light);
                }
                if (bl2) {
                    LuaTestHMI.matricesMain.set((Matrix4fc)matrices.peek().getPositionMatrix());
                } else {
                    LuaTestHMI.matricesOff.set((Matrix4fc)matrices.peek().getPositionMatrix());
                }
                matrices.push();
                ParticleRenderManager.draw(this.particles, matrices, orderedRenderCommandQueue, "ITEM", hand, light, player, tickProgress);
                matrices.pop();
            }
            matrices.pop();
            matrices.pop();
            if (bl) {
                this.prevMainHand = item.getItem();
            }
            if (!bl) {
                this.prevOffHand = item.getItem();
            }
            matrices.push();
            ParticleRenderManager.draw(this.particles, matrices, orderedRenderCommandQueue, "SCREEN", hand, light, player, tickProgress);
            matrices.pop();
            LuaTestHMI.tickProgress = tickProgress;
            GlobalsStorage.offHandItem = Items.AIR.getDefaultStack();
            GlobalsStorage.mainHandItem = Items.AIR.getDefaultStack();
        }
    }
}
