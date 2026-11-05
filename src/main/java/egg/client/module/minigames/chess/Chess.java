package egg.client.module.minigames.chess;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ButtonSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.util.GuiSoundManager;
import net.minecraft.client.MinecraftClient;

public class Chess extends Module {
    private static Chess instance;

    public static Chess getInstance() {
        return instance;
    }

    private final ButtonSetting openBoardSetting = new ButtonSetting("Открыть шахматы", "Открыть интерактивную плашку с шахматами", () -> {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(ChessScreen.getInstance());
    });

    private final BooleanSetting autoOpenSetting = new BooleanSetting("Авто-открытие", true, "Открывать доску при входящем вызове");
    private final BooleanSetting soundSetting = new BooleanSetting("Звуки", true, "Воспроизводить звуки щелчка при ходах");
    private final ModeSetting cmdPrefixSetting = new ModeSetting("Команда", "msg", "msg", "tell", "w");

    private final ChessBoard board = new ChessBoard();
    private String opponentName = "";
    private int myColor = ChessBoard.COLOR_WHITE;
    private boolean inGame = false;
    private String pendingInviteFrom = null;
    private boolean pendingInviteAsWhite = true;

    public Chess() {
        super("Chess", "Шахматы между игроками через /msg", Category.MINI_GAMES);
        instance = this;
        addSettings(openBoardSetting, autoOpenSetting, soundSetting, cmdPrefixSetting);
    }

    public ChessBoard getBoard() {
        return board;
    }

    public int getMyColor() {
        return myColor;
    }

    public void setMyColor(int color) {
        this.myColor = color;
    }

    public boolean isInGame() {
        return inGame;
    }

    public String getOpponentName() {
        return opponentName;
    }

    public void setOpponentName(String opponentName) {
        this.opponentName = opponentName != null ? opponentName : "";
    }

    public String getPendingInviteFrom() {
        return pendingInviteFrom;
    }

    public void sendInvite(String opponent, boolean asWhite) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.networkHandler == null) return;

        this.opponentName = opponent;
        this.myColor = asWhite ? ChessBoard.COLOR_WHITE : ChessBoard.COLOR_BLACK;
        this.board.reset();

        String myName = mc.player.getNameForScoreboard();
        String payload = ChessProtocol.buildInvite(asWhite, myName);
        sendRawPayload(opponent, payload);
    }

    public void acceptInvite() {
        if (pendingInviteFrom == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.networkHandler == null) return;

        this.opponentName = pendingInviteFrom;
        this.myColor = pendingInviteAsWhite ? ChessBoard.COLOR_BLACK : ChessBoard.COLOR_WHITE;
        this.board.reset();
        this.inGame = true;

        String myName = mc.player.getNameForScoreboard();
        String payload = ChessProtocol.buildAccept(myName);
        sendRawPayload(opponentName, payload);

        this.pendingInviteFrom = null;
    }

    public void declineInvite() {
        if (pendingInviteFrom == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.networkHandler != null) {
            String myName = mc.player.getNameForScoreboard();
            String payload = ChessProtocol.buildDecline(myName);
            sendRawPayload(pendingInviteFrom, payload);
        }
        this.pendingInviteFrom = null;
    }

    public boolean makeMyMove(int fc, int fr, int tc, int tr, ChessPiece promo) {
        if (board.getTurnColor() != myColor) return false;

        boolean success = board.makeMove(fc, fr, tc, tr, promo);
        if (success) {
            if (soundSetting.get()) {
                GuiSoundManager.playClick();
            }
            if (!opponentName.isEmpty()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                String myName = (mc.player != null) ? mc.player.getNameForScoreboard() : "";
                String payload = ChessProtocol.buildMove(fc, fr, tc, tr, promo, myName);
                sendRawPayload(opponentName, payload);
            }
            return true;
        }
        return false;
    }

    public void sendResign() {
        if (board.isGameOver()) return;
        board.resign(myColor);
        if (!opponentName.isEmpty()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            String myName = (mc.player != null) ? mc.player.getNameForScoreboard() : "";
            sendRawPayload(opponentName, ChessProtocol.buildResign(myName));
        }
    }

    public void sendReset() {
        board.reset();
        if (!opponentName.isEmpty()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            String myName = (mc.player != null) ? mc.player.getNameForScoreboard() : "";
            sendRawPayload(opponentName, ChessProtocol.buildReset(myName));
        }
    }

    private void sendRawPayload(String target, String payload) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.networkHandler == null) return;
        String cmd = cmdPrefixSetting.get() + " " + target + " " + payload;
        mc.player.networkHandler.sendChatCommand(cmd);
    }

    public boolean handleIncomingChat(String text) {
        ChessProtocol.Packet packet = ChessProtocol.parse(text);
        if (packet == null) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        String myName = (mc.player != null) ? mc.player.getNameForScoreboard() : "";
        if (packet.sender().equalsIgnoreCase(myName)) {
            return true;
        }

        switch (packet.type()) {
            case "INV" -> {
                pendingInviteFrom = packet.sender();
                pendingInviteAsWhite = packet.data().equals("W");
                opponentName = pendingInviteFrom;
                if (autoOpenSetting.get()) {
                    mc.setScreen(ChessScreen.getInstance());
                }
                if (soundSetting.get()) {
                    GuiSoundManager.playSelect();
                }
            }
            case "ACC" -> {
                opponentName = packet.sender();
                inGame = true;
                board.reset();
                if (autoOpenSetting.get()) {
                    mc.setScreen(ChessScreen.getInstance());
                }
                if (soundSetting.get()) {
                    GuiSoundManager.playSelect();
                }
            }
            case "DEC" -> {
                pendingInviteFrom = null;
                if (soundSetting.get()) {
                    GuiSoundManager.playClick();
                }
            }
            case "MOV" -> {
                if (packet.data().length() >= 4) {
                    int fc = Character.getNumericValue(packet.data().charAt(0));
                    int fr = Character.getNumericValue(packet.data().charAt(1));
                    int tc = Character.getNumericValue(packet.data().charAt(2));
                    int tr = Character.getNumericValue(packet.data().charAt(3));
                    char promoChar = packet.data().length() > 4 ? packet.data().charAt(4) : 'N';
                    ChessPiece promo = ChessPiece.fromChar(promoChar);
                    board.makeMove(fc, fr, tc, tr, promo);
                    if (soundSetting.get()) {
                        GuiSoundManager.playClick();
                    }
                }
            }
            case "RES" -> {
                board.resign(myColor == ChessBoard.COLOR_WHITE ? ChessBoard.COLOR_BLACK : ChessBoard.COLOR_WHITE);
                if (soundSetting.get()) {
                    GuiSoundManager.playSelect();
                }
            }
            case "RST" -> {
                board.reset();
                if (soundSetting.get()) {
                    GuiSoundManager.playSelect();
                }
            }
        }

        return true;
    }
}
