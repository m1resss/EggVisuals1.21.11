package egg.client.command.impl;

import egg.client.command.Command;
import egg.client.module.minigames.chess.Chess;
import egg.client.module.minigames.chess.ChessScreen;
import net.minecraft.client.MinecraftClient;

public class ChessCommand extends Command {
    public ChessCommand() {
        super("chess", "Открыть шахматы или отправить вызов", ".chess [ник] [w/b]", "shahmaty");
    }

    @Override
    public void execute(String[] args) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (args.length == 0) {
            mc.setScreen(ChessScreen.getInstance());
        } else {
            String target = args[0];
            boolean asWhite = true;
            if (args.length >= 2) {
                asWhite = !args[1].equalsIgnoreCase("b") && !args[1].equalsIgnoreCase("black");
            }
            Chess chess = Chess.getInstance();
            if (chess != null) {
                chess.sendInvite(target, asWhite);
            }
            mc.setScreen(ChessScreen.getInstance());
        }
    }
}
