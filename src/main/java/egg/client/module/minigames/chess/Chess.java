package egg.client.module.minigames.chess;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ButtonSetting;
import egg.client.util.GuiSoundManager;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;

public class Chess extends Module {
    private static Chess instance;

    public static Chess getInstance() {
        return instance;
    }

    public static final String SESSION_ID = UUID.randomUUID().toString().substring(0, 8);

    private final ButtonSetting openBoardSetting = new ButtonSetting("Открыть шахматы", "Открыть интерактивную плашку с шахматами", () -> {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(ChessScreen.getInstance());
    });

    private final BooleanSetting autoOpenSetting = new BooleanSetting("Авто-открытие", true, "Открывать доску при входящем вызове");
    private final BooleanSetting soundSetting = new BooleanSetting("Звуки", true, "Воспроизводить звуки щелчка при ходах");

    private final ChessBoard board = new ChessBoard();
    private String roomCode = "";
    private int myColor = ChessBoard.COLOR_WHITE;
    private boolean inGame = false;
    private boolean isHost = false;

    public Chess() {
        super("Chess", "Шахматы между игроками", Category.MINI_GAMES);
        instance = this;
        addSettings(openBoardSetting, autoOpenSetting, soundSetting);
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

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String val) {
        this.roomCode = val != null ? val : "";
    }

    public void hostGame(String targetRoom, boolean asWhite) {
        this.isHost = true;
        this.myColor = asWhite ? ChessBoard.COLOR_WHITE : ChessBoard.COLOR_BLACK;
        this.board.reset();
        this.inGame = true;
        this.roomCode = sanitizeRoom(targetRoom);

        ChessRelay.connect(this.roomCode);

        String hostTag = asWhite ? "HOST_W" : "HOST_B";
        String payload = "HELLO:" + hostTag + ":" + SESSION_ID;
        ChessRelay.send(payload);
    }

    public void joinGame(String targetRoom) {
        this.isHost = false;
        this.myColor = ChessBoard.COLOR_BLACK;
        this.board.reset();
        this.inGame = true;
        this.roomCode = sanitizeRoom(targetRoom);

        ChessRelay.connect(this.roomCode);

        String payload = "HELLO:JOIN:" + SESSION_ID;
        ChessRelay.send(payload);
    }

    public boolean makeMyMove(int fc, int fr, int tc, int tr, ChessPiece promo) {
        if (board.getTurnColor() != myColor) return false;

        boolean success = board.makeMove(fc, fr, tc, tr, promo);
        if (success) {
            inGame = true;
            if (soundSetting.get()) {
                GuiSoundManager.playClick();
            }

            char promoChar = (promo != null && promo != ChessPiece.NONE) ? promo.getNotation().charAt(0) : '0';
            String payload = "MOV:" + fc + "" + fr + "" + tc + "" + tr + "" + promoChar + ":" + SESSION_ID;
            ChessRelay.send(payload);
            return true;
        }
        return false;
    }

    public void sendResign() {
        if (board.isGameOver()) return;
        board.resign(myColor);
        ChessRelay.send("RES::" + SESSION_ID);
    }

    public void sendReset() {
        board.reset();
        ChessRelay.send("RST::" + SESSION_ID);
    }

    private String sanitizeRoom(String input) {
        String s = input.trim().toLowerCase();
        if (s.isEmpty()) return "default";
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

        if (sender != null && sender.equals(SESSION_ID)) {
            return true;
        }

        switch (type) {
            case "HELLO" -> {
                if ("JOIN".equals(data)) {
                    if (isHost) {
                        String hostTag = (myColor == ChessBoard.COLOR_WHITE) ? "HOST_W" : "HOST_B";
                        ChessRelay.send("HELLO:" + hostTag + ":" + SESSION_ID);
                        for (ChessMove m : board.getMoveHistory()) {
                            char promoChar = (m.getPromoPiece() != null && m.getPromoPiece() != ChessPiece.NONE) ? m.getPromoPiece().getNotation().charAt(0) : '0';
                            String movPayload = "MOV:" + m.getFromCol() + "" + m.getFromRow() + "" + m.getToCol() + "" + m.getToRow() + "" + promoChar + ":" + SESSION_ID;
                            ChessRelay.send(movPayload);
                        }
                    }
                } else if ("HOST_W".equals(data) || "W".equals(data)) {
                    if (!isHost) {
                        this.myColor = ChessBoard.COLOR_BLACK;
                    }
                    this.inGame = true;
                    if (board.getMoveHistory().isEmpty()) {
                        this.board.reset();
                    }
                    if (autoOpenSetting.get()) {
                        MinecraftClient.getInstance().setScreen(ChessScreen.getInstance());
                    }
                    if (soundSetting.get()) {
                        GuiSoundManager.playSelect();
                    }
                } else if ("HOST_B".equals(data) || "B".equals(data)) {
                    if (!isHost) {
                        this.myColor = ChessBoard.COLOR_WHITE;
                    }
                    this.inGame = true;
                    if (board.getMoveHistory().isEmpty()) {
                        this.board.reset();
                    }
                    if (autoOpenSetting.get()) {
                        MinecraftClient.getInstance().setScreen(ChessScreen.getInstance());
                    }
                    if (soundSetting.get()) {
                        GuiSoundManager.playSelect();
                    }
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
