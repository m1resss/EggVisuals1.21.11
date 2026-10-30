package egg.client.cosmetic;

import egg.client.module.ModuleManager;
import egg.client.module.misc.Globals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Map;

public class CosmeticsRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    public static String previewOverrideCat = null;
    public static String previewOverrideId = null;

    public CosmeticsRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isLocal = false;
        Entity entity = null;
        if (mc.world != null) {
            entity = mc.world.getEntityById(state.id);
            if (mc.player != null && (entity == mc.player || state.id == mc.player.getId())) {
                isLocal = true;
            }
        }
        if (mc.player != null) {
            if (state.id == mc.player.getId() || state.id <= 0) {
                isLocal = true;
            }
            if (entity == mc.player) {
                isLocal = true;
            }
            String myName = mc.player.getNameForScoreboard();
            if (myName != null && !myName.isEmpty()) {
                if (state.displayName != null && state.displayName.getString().contains(myName)) {
                    isLocal = true;
                }
                if (state.playerName != null && state.playerName.getString().contains(myName)) {
                    isLocal = true;
                }
            }
        }
        if (isLocal || state.id <= 0) {
            CosmeticsManager.getInstance().renderAttached(matrices, queue, getContextModel(), light);
            return;
        }

        // Render cosmetics for other Egg Visuals users when Globals and its "Косметика" checkbox are enabled
        Globals globals = ModuleManager.getInstance().getModule(Globals.class);
        if (globals != null && globals.isEnabled() && globals.isCosmeticsEnabled()) {
            Map<String, String> playerCosmetics = null;
            if (entity instanceof PlayerEntity player && Globals.isEggUser(player)) {
                playerCosmetics = Globals.getPlayerCosmetics(player);
            } else if (Globals.isEggUser(state)) {
                playerCosmetics = Globals.getPlayerCosmetics(state);
            }
            if (playerCosmetics != null && !playerCosmetics.isEmpty()) {
                CosmeticsManager.getInstance().renderAttachedForPlayer(matrices, queue, getContextModel(), light, playerCosmetics);
            }
        }
    }
}
