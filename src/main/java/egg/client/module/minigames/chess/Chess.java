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

    private final ModeSetting transportSetting = new ModeSetting("Транспорт", "Облако (P2P)", "Облако (P2P)", "Чат (/msg)");
    private final BooleanSetting autoOpenSetting = new BooleanSetting("Авто-открытие", true, "Открывать доску при входящем вызове");
    private final BooleanSetting soundSetting = new BooleanSetting("Звуки", true, "Воспроизводить звуки щелчка при ходах");
    private final ModeSetting cmdPrefixSetting = new ModeSetting("Команда /msg", "msg", "msg", "tell", "w");

    private final ChessBoard board = new ChessBoard();
    private String roomOrOpponent = "";
    private int myColor = ChessBoard.COLOR_WHITE;
    private boolean inGame = false;
    private boolean isHost = false;
    private String pendingInviteFrom = null;
    private boolean pendingInviteAsWhite = true;

    public Chess() {
        super("Chess", "Шахматы между игроками", Category.MINI_GAMES);
        instance = this;
        addSettings(openBoardSetting, transportSetting, autoOpenSetting, soundSetting, cmdPrefixSetting);
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

    public void setInGame(boolean inGame) {
        this.inGame = inGame;
    }

    public String getRoomOrOpponent() {
        return roomOrOpponent;
    }

    public void setRoomOrOpponent(String val) {
        this.roomOrOpponent = val != null ? val : "";
    }

    public String getPendingInviteFrom() {
        return pendingInviteFrom;
    }

    public boolean isCloudTransport() {
        return transportSetting.get().startsWith("Облако");
    }

    public String getMyPlayerName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            String name = mc.player.getNameForScoreboard();
            if (name != null && !name.isEmpty()) return name;
        }
        return "Player";
    }

    public void hostGame(String targetRoomOrNick, boolean asWhite) {
        this.isHost = true;
        this.myColor = asWhite ? ChessBoard.COLOR_WHITE : ChessBoard.COLOR_BLACK;
        this.board.reset();
        this.inGame = true;
        this.roomOrOpponent = targetRoomOrNick.trim();

        String room = computeRoomCode(this.roomOrOpponent);
        ChessRelay.connect(room);

        String myName = getMyPlayerName();
        String payload = "HOST:" + (asWhite ? "W" : "B") + ":" + myName;
        ChessRelay.send(payload);

        if (!isCloudTransport() && !this.roomOrOpponent.isEmpty()) {
            sendRawMsg(this.roomOrOpponent, ChessProtocol.buildInvite(asWhite, myName));
        }
    }

    public void joinGame(String targetRoomOrNick) {
        this.isHost = false;
        this.roomOrOpponent = targetRoomOrNick.trim();
        String room = computeRoomCode(this.roomOrOpponent);
        ChessRelay.connect(room);

        String myName = getMyPlayerName();
        String payload = "JOIN::" + myName;
        ChessRelay.send(payload);

        if (!isCloudTransport() && !this.roomOrOpponent.isEmpty()) {
            sendRawMsg(this.roomOrOpponent, ChessProtocol.buildAccept(myName));
        }
    }

    public void acceptInvite() {
        if (pendingInviteFrom == null) return;

        this.isHost = false;
        this.roomOrOpponent = pendingInviteFrom;
        this.myColor = pendingInviteAsWhite ? ChessBoard.COLOR_BLACK : ChessBoard.COLOR_WHITE;
        this.board.reset();
        this.inGame = true;

        String room = computeRoomCode(this.roomOrOpponent);
        ChessRelay.connect(room);

        String myName = getMyPlayerName();
        String payload = "ACC::" + myName;
        ChessRelay.send(payload);

        if (!isCloudTransport()) {
            sendRawMsg(pendingInviteFrom, ChessProtocol.buildAccept(myName));
        }

        this.pendingInviteFrom = null;
    }

    public void declineInvite() {
        if (pendingInviteFrom == null) return;
        String myName = getMyPlayerName();
        ChessRelay.send("DEC::" + myName);
        if (!isCloudTransport()) {
            sendRawMsg(pendingInviteFrom, ChessProtocol.buildDecline(myName));
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

            char promoChar = (promo != null && promo != ChessPiece.NONE) ? promo.getNotation().charAt(0) : '0';
            String myName = getMyPlayerName();
            String payload = "MOV:" + fc + "" + fr + "" + tc + "" + tr + "" + promoChar + ":" + myName;

            ChessRelay.send(payload);

            if (!isCloudTransport() && !roomOrOpponent.isEmpty()) {
                sendRawMsg(roomOrOpponent, ChessProtocol.buildMove(fc, fr, tc, tr, promo, myName));
            }
            return true;
        }
        return false;
    }

    public void sendResign() {
        if (board.isGameOver()) return;
        board.resign(myColor);
        String myName = getMyPlayerName();
        ChessRelay.send("RES::" + myName);
        if (!isCloudTransport() && !roomOrOpponent.isEmpty()) {
            sendRawMsg(roomOrOpponent, ChessProtocol.buildResign(myName));
        }
    }

    public void sendReset() {
        board.reset();
        String myName = getMyPlayerName();
        ChessRelay.send("RST::" + myName);
        if (!isCloudTransport() && !roomOrOpponent.isEmpty()) {
            sendRawMsg(roomOrOpponent, ChessProtocol.buildReset(myName));
        }
    }

    private void sendRawMsg(String target, String payload) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.networkHandler == null) return;
        String cmd = cmdPrefixSetting.get() + " " + target + " " + payload;
        mc.player.networkHandler.sendChatCommand(cmd);
    }

    public String computeRoomCode(String input) {
        String s = input.trim().toLowerCase();
        if (s.isEmpty()) return "default";
        String myName = getMyPlayerName().toLowerCase();
        if (!s.matches("\\d+") && !s.contains("_")) {
            return (myName.compareTo(s) < 0) ? (myName + "_" + s) : (s + "_" + myName);
        }
        return s;
    }

    public boolean handleIncomingPayload(String rawText) {
        if (rawText == null || rawText.isEmpty()) return false;

        String text = rawText;
        if (rawText.contains(ChessProtocol.PREFIX)) {
            String decoded = ChessProtocol.decode(rawText);
            if (decoded != null) text = decoded;
        }

        String[] parts = text.split(":", 3);
        if (parts.length < 2) return false;

        String type = parts[0];
        String data = parts.length >= 3 ? parts[1] : "";
        String sender = parts.length >= 3 ? parts[2] : parts[1];

        String myName = getMyPlayerName();
        if (sender != null && !sender.isEmpty() && sender.equalsIgnoreCase(myName)) {
            return true;
        }

        switch (type) {
            case "HOST" -> {
                pendingInviteFrom = sender;
                pendingInviteAsWhite = data.equals("W");
                if (roomOrOpponent.isEmpty() || roomOrOpponent.equalsIgnoreCase(sender)) {
                    roomOrOpponent = sender;
                }
                if (!isHost) {
                    myColor = pendingInviteAsWhite ? ChessBoard.COLOR_BLACK : ChessBoard.COLOR_WHITE;
                }
                if (autoOpenSetting.get()) {
                    MinecraftClient.getInstance().setScreen(ChessScreen.getInstance());
                }
                if (soundSetting.get()) {
                    GuiSoundManager.playSelect();
                }
            }
            case "JOIN" -> {
                if (isHost) {
                    if (roomOrOpponent.isEmpty() || roomOrOpponent.equalsIgnoreCase(sender)) {
                        roomOrOpponent = sender;
                    }
                    inGame = true;
                    board.reset();
                    String reply = "START:" + (myColor == ChessBoard.COLOR_WHITE ? "W" : "B") + ":" + myName;
                    ChessRelay.send(reply);
                    if (soundSetting.get()) {
                        GuiSoundManager.playSelect();
                    }
                }
            }
            case "START" -> {
                boolean hostIsWhite = data.equals("W");
                myColor = hostIsWhite ? ChessBoard.COLOR_BLACK : ChessBoard.COLOR_WHITE;
                if (roomOrOpponent.isEmpty() || roomOrOpponent.equalsIgnoreCase(sender)) {
                    roomOrOpponent = sender;
                }
                inGame = true;
                board.reset();
                if (autoOpenSetting.get()) {
                    MinecraftClient.getInstance().setScreen(ChessScreen.getInstance());
                }
                if (soundSetting.get()) {
                    GuiSoundManager.playSelect();
                }
            }
            case "INV" -> {
                pendingInviteFrom = sender;
                pendingInviteAsWhite = data.equals("W");
                roomOrOpponent = pendingInviteFrom;
                if (autoOpenSetting.get()) {
                    MinecraftClient.getInstance().setScreen(ChessScreen.getInstance());
                }
                if (soundSetting.get()) {
                    GuiSoundManager.playSelect();
                }
            }
            case "ACC" -> {
                if (roomOrOpponent.isEmpty() || roomOrOpponent.equalsIgnoreCase(sender)) {
                    roomOrOpponent = sender;
                }
                inGame = true;
                if (board.getMoveHistory().isEmpty()) {
                    board.reset();
                }
                if (autoOpenSetting.get()) {
                    MinecraftClient.getInstance().setScreen(ChessScreen.getInstance());
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
                if (data.length() >= 4) {
                    int fc = Character.getNumericValue(data.charAt(0));
                    int fr = Character.getNumericValue(data.charAt(1));
                    int tc = Character.getNumericValue(data.charAt(2));
                    int tr = Character.getNumericValue(data.charAt(3));
                    char promoChar = data.length() > 4 ? data.charAt(4) : '0';
                    ChessPiece promo = ChessPiece.fromChar(promoChar);

                    int movingColor = board.getColor(fc, fr);
                    if (movingColor != ChessBoard.COLOR_NONE && movingColor == myColor) {
                        myColor = (movingColor == ChessBoard.COLOR_WHITE) ? ChessBoard.COLOR_BLACK : ChessBoard.COLOR_WHITE;
                    }

                    board.forceMove(fc, fr, tc, tr, promo);
                    inGame = true;
                    if (!sender.isEmpty()) {
                        roomOrOpponent = sender;
                    }

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
