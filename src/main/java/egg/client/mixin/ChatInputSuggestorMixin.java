package egg.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import egg.client.command.CommandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.command.CommandSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {

    @Shadow
    @Final
    TextFieldWidget textField;

    @Shadow
    boolean completingSuggestions;

    @Shadow
    private ParseResults<CommandSource> parse;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    private ChatInputSuggestor.SuggestionWindow window;

    @Shadow
    protected abstract void showCommandSuggestions();

    @Inject(method = "refresh", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/StringReader;canRead()Z", remap = false), cancellable = true)
    public void onRefresh(CallbackInfo callbackInfo, @Local StringReader reader) {
        String prefix = ".";
        if (reader.canRead(prefix.length()) && reader.getString().startsWith(prefix, reader.getCursor())) {
            String text = this.textField.getText();
            int cursor = this.textField.getCursor();
            StringReader reading = new StringReader(text);
            reading.setCursor(prefix.length());
            CommandDispatcher<CommandSource> dispatcher = CommandManager.getInstance().getDispatcher();
            MinecraftClient mc = MinecraftClient.getInstance();
            CommandSource source = (mc.getNetworkHandler() != null) ? mc.getNetworkHandler().getCommandSource() : null;
            if (source == null) return;

            this.parse = dispatcher.parse(reading, source);
            if (cursor >= prefix.length() && (this.window == null || !this.completingSuggestions)) {
                this.pendingSuggestions = dispatcher.getCompletionSuggestions(this.parse, cursor);
                this.pendingSuggestions.thenRun(() -> {
                    if (this.pendingSuggestions.isDone()) {
                        showCommandSuggestions();
                    }
                });
            }
            callbackInfo.cancel();
        }
    }
}
