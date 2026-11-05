package egg.client.module.minigames.chess;

import egg.client.render.IconRenderer;
import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.GuiSoundManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

public class ChessScreen extends Screen {
    private static ChessScreen instance;

    public static ChessScreen getInstance() {
        if (instance == null) {
            instance = new ChessScreen();
        }
        return instance;
    }

    private static final Identifier MODS_ICON = Identifier.of("egg", "textures/icons/mods.png");

    private float panelX = -1;
    private float panelY = -1;
    private final float panelW = 520.0f;
    private final float panelH = 370.0f;

    private boolean isDragging = false;
    private float dragOffsetX = 0;
    private float dragOffsetY = 0;

    private int selectedCol = -1;
    private int selectedRow = -1;
    private List<ChessMove> currentLegalMoves = null;

    private String roomInput = "";
    private boolean roomInputFocused = false;
    private long lastBlinkTime = 0;

    public ChessScreen() {
        super(Text.literal("Chess"));
    }

    @Override
    protected void init() {
        super.init();
        if (panelX < 0 || panelY < 0) {
            panelX = (this.width - panelW) / 2.0f;
            panelY = (this.height - panelH) / 2.0f;
        }
        Chess mod = Chess.getInstance();
        if (mod != null && (roomInput == null || roomInput.isEmpty())) {
            roomInput = mod.getRoomOrOpponent();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Render2D.drawRect(context, 0, 0, this.width, this.height, 0x55000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (isDragging) {
            panelX = Math.max(10.0f, Math.min(this.width - panelW - 10.0f, mouseX - dragOffsetX));
            panelY = Math.max(10.0f, Math.min(this.height - panelH - 10.0f, mouseY - dragOffsetY));
        }

        renderMainPlate(context, mouseX, mouseY);
        renderHeader(context, mouseX, mouseY);
        renderBoard(context, mouseX, mouseY);
        renderSidePanel(context, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderMainPlate(DrawContext context, int mouseX, int mouseY) {
        Render2D.drawRoundedRect(context, panelX + 3.0f, panelY + 4.0f, panelW, panelH, 10.0f, 0x40000000);
        Render2D.drawRoundedRect(context, panelX, panelY, panelW, panelH, 10.0f, 0xF012151D);
        Render2D.drawRoundedOutline(context, panelX, panelY, panelW, panelH, 10.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 24));
    }

    private void renderHeader(DrawContext context, int mouseX, int mouseY) {
        float hH = 34.0f;
        Render2D.drawRoundedRect(context, panelX + 1.0f, panelY + 1.0f, panelW - 2.0f, hH, 9.0f, 0x22FFFFFF);

        IconRenderer.drawTextureScaled(context, MODS_ICON, panelX + 10.0f, panelY + 10.0f, 14.0f, 14.0f, 64, 64, Theme.getAccent());
        ClientFont.draw(context, "CHESS", panelX + 28.0f, panelY + 12.0f, ClientFont.SF_BOLD, 0xFFFFFFFF);

        boolean isOnline = ChessRelay.isConnected();
        String statusLabel = isOnline ? "\u25CF " + ChessRelay.getCurrentRoom() : "\u25CB Offline";
        int badgeCol = isOnline ? Theme.getAccent() : 0xFF7A8294;
        float badgeW = Math.max(54.0f, ClientFont.getWidth(statusLabel, ClientFont.SF_MEDIUM) + 12.0f);
        float badgeH = 13.0f;
        float badgeX = panelX + 70.0f;
        float badgeY = panelY + 11.0f;
        Render2D.drawRoundedRect(context, badgeX, badgeY, badgeW, badgeH, 3.0f, ColorUtil.withAlphaFloat(badgeCol, 0.22f));
        Render2D.drawRoundedOutline(context, badgeX, badgeY, badgeW, badgeH, 3.0f, 0.8f, ColorUtil.withAlphaFloat(badgeCol, 0.55f));
        ClientFont.drawCentered(context, statusLabel, badgeX + badgeW / 2.0f, badgeY + 2.5f, ClientFont.SF_MEDIUM, badgeCol);

        float closeX = panelX + panelW - 24.0f;
        float closeY = panelY + 9.0f;
        float closeSize = 16.0f;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + closeSize && mouseY >= closeY && mouseY <= closeY + closeSize;
        int closeBg = closeHover ? ColorUtil.rgba(230, 60, 60, 160) : ColorUtil.rgba(255, 255, 255, 18);
        Render2D.drawRoundedRect(context, closeX, closeY, closeSize, closeSize, 4.0f, closeBg);
        ClientFont.drawCentered(context, "\u2715", closeX + closeSize / 2.0f, closeY + 3.0f, ClientFont.SF_BOLD, closeHover ? 0xFFFFFFFF : 0xFFA0A5B5);

        Render2D.drawRect(context, panelX + 1.0f, panelY + hH, panelW - 2.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 15));
    }

    private void renderBoard(DrawContext context, int mouseX, int mouseY) {
        Chess mod = Chess.getInstance();
        ChessBoard board = (mod != null) ? mod.getBoard() : null;
        if (board == null) return;

        boolean asWhite = (mod.getMyColor() != ChessBoard.COLOR_BLACK);

        float bX = panelX + 14.0f;
        float bY = panelY + 44.0f;
        float tileSize = 38.0f;
        float bSize = tileSize * 8.0f;

        Render2D.drawRoundedRect(context, bX - 2.0f, bY - 2.0f, bSize + 4.0f, bSize + 4.0f, 6.0f, 0xFF191D27);
        Render2D.drawRoundedOutline(context, bX - 2.0f, bY - 2.0f, bSize + 4.0f, bSize + 4.0f, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 20));

        ChessMove lastMove = board.getLastMove();

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int drawC = asWhite ? c : (7 - c);
                int drawR = asWhite ? r : (7 - r);

                float tX = bX + drawC * tileSize;
                float tY = bY + drawR * tileSize;

                boolean isLight = ((c + r) % 2 == 0);
                int tileColor = isLight ? 0xFF353C4D : 0xFF212530;

                if (lastMove != null) {
                    if ((lastMove.getFromCol() == c && lastMove.getFromRow() == r) ||
                        (lastMove.getToCol() == c && lastMove.getToRow() == r)) {
                        tileColor = ColorUtil.interpolate(tileColor, 0xFF7A6A2E, 0.45f);
                    }
                }

                if (selectedCol == c && selectedRow == r) {
                    tileColor = ColorUtil.interpolate(tileColor, Theme.getAccent(), 0.55f);
                }

                if (board.getPiece(c, r) == ChessPiece.KING && board.isKingInCheck(board.getColor(c, r))) {
                    tileColor = ColorUtil.interpolate(tileColor, 0xFFCC3333, 0.65f);
                }

                boolean hovered = mouseX >= tX && mouseX < tX + tileSize && mouseY >= tY && mouseY < tY + tileSize;
                if (hovered && selectedCol == -1) {
                    tileColor = ColorUtil.interpolate(tileColor, 0xFFFFFFFF, 0.08f);
                }

                Render2D.drawRect(context, tX, tY, tileSize, tileSize, tileColor);

                if (selectedCol == c && selectedRow == r) {
                    Render2D.drawRoundedOutline(context, tX + 1.0f, tY + 1.0f, tileSize - 2.0f, tileSize - 2.0f, 2.0f, 1.5f, Theme.getAccent());
                }

                ChessPiece piece = board.getPiece(c, r);
                int color = board.getColor(c, r);
                if (piece != ChessPiece.NONE && color != ChessBoard.COLOR_NONE) {
                    float pSize = tileSize * 0.85f;
                    float pPad = (tileSize - pSize) / 2.0f;
                    ChessPieceRenderer.drawPiece(context, piece, color, tX + pPad, tY + pPad, pSize, 1.0f);
                }

                if (currentLegalMoves != null) {
                    for (ChessMove lm : currentLegalMoves) {
                        if (lm.getToCol() == c && lm.getToRow() == r) {
                            float dotR = (lm.getCapturedPiece() != ChessPiece.NONE || lm.isEnPassant()) ? 6.5f : 4.5f;
                            int dotColor = (lm.getCapturedPiece() != ChessPiece.NONE || lm.isEnPassant())
                                    ? ColorUtil.rgba(240, 80, 80, 200)
                                    : ColorUtil.withAlphaFloat(Theme.getAccent(), 0.85f);

                            if (lm.getCapturedPiece() != ChessPiece.NONE || lm.isEnPassant()) {
                                Render2D.drawRoundedOutline(context, tX + 2.0f, tY + 2.0f, tileSize - 4.0f, tileSize - 4.0f, 4.0f, 1.5f, dotColor);
                            } else {
                                Render2D.drawRoundedRect(context, tX + tileSize / 2.0f - dotR, tY + tileSize / 2.0f - dotR, dotR * 2.0f, dotR * 2.0f, dotR, dotColor);
                            }
                        }
                    }
                }

                if (drawC == 0) {
                    String rankStr = String.valueOf(8 - r);
                    int labelColor = isLight ? 0x88FFFFFF : 0x66FFFFFF;
                    ClientFont.draw(context, rankStr, tX + 2.0f, tY + 2.0f, ClientFont.SF_MEDIUM, labelColor);
                }
                if (drawR == 7) {
                    String fileStr = String.valueOf((char) ('a' + c));
                    int labelColor = isLight ? 0x88FFFFFF : 0x66FFFFFF;
                    ClientFont.draw(context, fileStr, tX + tileSize - 8.0f, tY + tileSize - 9.0f, ClientFont.SF_MEDIUM, labelColor);
                }
            }
        }
    }

    private void renderSidePanel(DrawContext context, int mouseX, int mouseY) {
        Chess mod = Chess.getInstance();
        if (mod == null) return;
        ChessBoard board = mod.getBoard();

        float sX = panelX + 328.0f;
        float sY = panelY + 44.0f;
        float sW = panelW - 342.0f;

        renderStatusCard(context, mod, board, sX, sY, sW);

        float oppY = sY + 68.0f;
        renderOpponentCard(context, mod, sX, oppY, sW, mouseX, mouseY);

        float actY = oppY + 120.0f;
        renderActionsCard(context, mod, sX, actY, sW, mouseX, mouseY);

        float histY = actY + 38.0f;
        renderHistoryCard(context, board, sX, histY, sW);
    }

    private void renderStatusCard(DrawContext context, Chess mod, ChessBoard board, float x, float y, float w) {
        float h = 60.0f;
        Render2D.drawRoundedRect(context, x, y, w, h, 6.0f, 0xFF191D27);
        Render2D.drawRoundedOutline(context, x, y, w, h, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 18));

        String turnText;
        int statusDotCol;

        if (board.isGameOver()) {
            if (board.isStalemate()) {
                turnText = "Ничья (Пат)";
                statusDotCol = 0xFFAAAAAA;
            } else {
                boolean whiteWon = (board.getWinnerColor() == ChessBoard.COLOR_WHITE);
                turnText = whiteWon ? "Мат! Победа Белых" : "Мат! Победа Черных";
                statusDotCol = Theme.getAccent();
            }
        } else if (mod.isInGame()) {
            boolean myTurn = (board.getTurnColor() == mod.getMyColor());
            turnText = myTurn ? "Ваш ход (" + (mod.getMyColor() == ChessBoard.COLOR_WHITE ? "Белые" : "Черные") + ")"
                              : "Ход противника...";
            statusDotCol = myTurn ? Theme.getAccent() : 0xFF7A8294;
        } else {
            turnText = "Партия не начата";
            statusDotCol = 0xFF555C6E;
        }

        Render2D.drawRoundedRect(context, x + 10.0f, y + 12.0f, 8.0f, 8.0f, 4.0f, statusDotCol);
        ClientFont.draw(context, turnText, x + 23.0f, y + 11.5f, ClientFont.SF_BOLD, 0xFFFFFFFF);

        String roomStr = mod.getRoomOrOpponent().isEmpty() ? "Комната: не выбрана" : "Комната: " + mod.getRoomOrOpponent();
        ClientFont.draw(context, roomStr, x + 10.0f, y + 27.0f, ClientFont.SF_REGULAR, 0xFFA0A5B5);

        String capturedStr = "Взято: " + board.getCapturedByWhite().size() + "Б / " + board.getCapturedByBlack().size() + "Ч";
        ClientFont.draw(context, capturedStr, x + 10.0f, y + 42.0f, ClientFont.SF_REGULAR, 0xFF757D90);
    }

    private void renderOpponentCard(DrawContext context, Chess mod, float x, float y, float w, int mouseX, int mouseY) {
        float h = 114.0f;
        Render2D.drawRoundedRect(context, x, y, w, h, 6.0f, 0xFF191D27);
        Render2D.drawRoundedOutline(context, x, y, w, h, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 18));

        ClientFont.draw(context, "Подключение к игре", x + 10.0f, y + 8.0f, ClientFont.SF_MEDIUM, 0xFFD0D5E5);

        float inX = x + 8.0f;
        float inY = y + 23.0f;
        float inW = w - 16.0f;
        float inH = 20.0f;
        int inBg = roomInputFocused ? 0xFF222838 : 0xFF1C202C;
        int inBorder = roomInputFocused ? Theme.getAccent() : ColorUtil.rgba(255, 255, 255, 24);
        Render2D.drawRoundedRect(context, inX, inY, inW, inH, 4.0f, inBg);
        Render2D.drawRoundedOutline(context, inX, inY, inW, inH, 4.0f, 1.0f, inBorder);

        String displayInput = (roomInput == null || roomInput.isEmpty()) ? (roomInputFocused ? "" : "Код (напр. 7777) или ник") : roomInput;
        int textCol = (roomInput == null || roomInput.isEmpty()) && !roomInputFocused ? 0xFF656B7D : 0xFFFFFFFF;
        ClientFont.draw(context, displayInput, inX + 6.0f, inY + 5.5f, ClientFont.SF_REGULAR, textCol);

        if (roomInputFocused) {
            long now = System.currentTimeMillis();
            if ((now - lastBlinkTime) % 1000 < 500) {
                float curX = inX + 6.0f + ClientFont.getWidth(roomInput, ClientFont.SF_REGULAR);
                Render2D.drawRect(context, curX + 1.0f, inY + 4.0f, 1.0f, 12.0f, Theme.getAccent());
            }
        }

        if (mod.getPendingInviteFrom() != null) {
            float invBoxY = inY + 25.0f;
            float invBoxH = 58.0f;
            Render2D.drawRoundedRect(context, inX, invBoxY, inW, invBoxH, 4.0f, 0xFF222938);
            Render2D.drawRoundedOutline(context, inX, invBoxY, inW, invBoxH, 4.0f, 1.0f, Theme.getAccent());

            String invText = "Вызов: " + mod.getPendingInviteFrom();
            ClientFont.draw(context, invText, inX + 6.0f, invBoxY + 6.0f, ClientFont.SF_MEDIUM, 0xFFFFFFFF);

            float bW = (inW - 6.0f) / 2.0f;
            float bH = 20.0f;
            float bY = invBoxY + 30.0f;

            boolean accHov = mouseX >= inX && mouseX <= inX + bW && mouseY >= bY && mouseY <= bY + bH;
            Render2D.drawRoundedRect(context, inX, bY, bW, bH, 4.0f, accHov ? Theme.getAccentHover() : Theme.getAccent());
            ClientFont.drawCentered(context, "Принять", inX + bW / 2.0f, bY + 5.5f, ClientFont.SF_BOLD, 0xFFFFFFFF);

            float decX = inX + bW + 6.0f;
            boolean decHov = mouseX >= decX && mouseX <= decX + bW && mouseY >= bY && mouseY <= bY + bH;
            Render2D.drawRoundedRect(context, decX, bY, bW, bH, 4.0f, decHov ? 0xFFD03E3E : 0xFFAA2E2E);
            ClientFont.drawCentered(context, "Отклонить", decX + bW / 2.0f, bY + 5.5f, ClientFont.SF_BOLD, 0xFFFFFFFF);
        } else {
            float btnH = 19.0f;
            float btnY1 = inY + 24.0f;
            float btnY2 = btnY1 + 22.0f;
            float btnY3 = btnY2 + 22.0f;

            boolean wHov = mouseX >= inX && mouseX <= inX + inW && mouseY >= btnY1 && mouseY <= btnY1 + btnH;
            Render2D.drawRoundedRect(context, inX, btnY1, inW, btnH, 4.0f, wHov ? Theme.getAccentHover() : Theme.getAccent());
            ClientFont.drawCentered(context, "\u2654 Создать (Белые)", inX + inW / 2.0f, btnY1 + 5.0f, ClientFont.SF_BOLD, 0xFFFFFFFF);

            boolean bHov = mouseX >= inX && mouseX <= inX + inW && mouseY >= btnY2 && mouseY <= btnY2 + btnH;
            Render2D.drawRoundedRect(context, inX, btnY2, inW, btnH, 4.0f, bHov ? 0xFF2D3446 : 0xFF232836);
            Render2D.drawRoundedOutline(context, inX, btnY2, inW, btnH, 4.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 25));
            ClientFont.drawCentered(context, "\u265A Создать (Черные)", inX + inW / 2.0f, btnY2 + 5.0f, ClientFont.SF_BOLD, 0xFFD0D5E5);

            boolean jHov = mouseX >= inX && mouseX <= inX + inW && mouseY >= btnY3 && mouseY <= btnY3 + btnH;
            Render2D.drawRoundedRect(context, inX, btnY3, inW, btnH, 4.0f, jHov ? 0xFF353D52 : 0xFF282E3E);
            Render2D.drawRoundedOutline(context, inX, btnY3, inW, btnH, 4.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 30));
            ClientFont.drawCentered(context, "\u25B6 Войти по коду / нику", inX + inW / 2.0f, btnY3 + 5.0f, ClientFont.SF_MEDIUM, 0xFFE0E5F5);
        }
    }

    private void renderActionsCard(DrawContext context, Chess mod, float x, float y, float w, int mouseX, int mouseY) {
        float h = 32.0f;
        Render2D.drawRoundedRect(context, x, y, w, h, 6.0f, 0xFF191D27);
        Render2D.drawRoundedOutline(context, x, y, w, h, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 18));

        float btnW = (w - 18.0f) / 2.0f;
        float btnH = 20.0f;
        float bY = y + 6.0f;

        float rX = x + 6.0f;
        boolean rHov = mouseX >= rX && mouseX <= rX + btnW && mouseY >= bY && mouseY <= bY + btnH;
        int rBg = rHov ? 0xFF882525 : 0xFF351E22;
        Render2D.drawRoundedRect(context, rX, bY, btnW, btnH, 4.0f, rBg);
        Render2D.drawRoundedOutline(context, rX, bY, btnW, btnH, 4.0f, 0.8f, ColorUtil.rgba(255, 100, 100, 40));
        ClientFont.drawCentered(context, "Сдаться", rX + btnW / 2.0f, bY + 5.5f, ClientFont.SF_MEDIUM, 0xFFFFA0A0);

        float nX = rX + btnW + 6.0f;
        boolean nHov = mouseX >= nX && mouseX <= nX + btnW && mouseY >= bY && mouseY <= bY + btnH;
        int nBg = nHov ? 0xFF2B3345 : 0xFF202533;
        Render2D.drawRoundedRect(context, nX, bY, btnW, btnH, 4.0f, nBg);
        Render2D.drawRoundedOutline(context, nX, bY, btnW, btnH, 4.0f, 0.8f, ColorUtil.rgba(255, 255, 255, 22));
        ClientFont.drawCentered(context, "Сброс", nX + btnW / 2.0f, bY + 5.5f, ClientFont.SF_MEDIUM, 0xFFD0D5E5);
    }

    private void renderHistoryCard(DrawContext context, ChessBoard board, float x, float y, float w) {
        float h = panelH - (y - panelY) - 12.0f;
        Render2D.drawRoundedRect(context, x, y, w, h, 6.0f, 0xFF191D27);
        Render2D.drawRoundedOutline(context, x, y, w, h, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 18));

        ClientFont.draw(context, "История ходов", x + 10.0f, y + 6.0f, ClientFont.SF_MEDIUM, 0xFF8890A5);

        List<ChessMove> history = board.getMoveHistory();
        float lineY = y + 20.0f;
        int startIdx = Math.max(0, history.size() - 6);

        for (int i = startIdx; i < history.size(); i++) {
            ChessMove m = history.get(i);
            String moveNum = (i + 1) + ". ";
            String notation = moveNum + m.getNotation();
            int col = (i % 2 == 0) ? 0xFFFFFFFF : 0xFFA5B0C5;
            ClientFont.draw(context, notation, x + 10.0f, lineY, ClientFont.SF_REGULAR, col);
            lineY += 13.0f;
        }

        if (history.isEmpty()) {
            ClientFont.draw(context, "Ходов пока нет", x + 10.0f, lineY, ClientFont.SF_REGULAR, 0xFF555C6E);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        float mouseX = (float) click.x();
        float mouseY = (float) click.y();
        int button = click.button();

        if (button == 0) {
            float closeX = panelX + panelW - 24.0f;
            float closeY = panelY + 9.0f;
            float closeSize = 16.0f;
            if (mouseX >= closeX && mouseX <= closeX + closeSize && mouseY >= closeY && mouseY <= closeY + closeSize) {
                GuiSoundManager.playClick();
                this.close();
                return true;
            }

            if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + 34.0f) {
                isDragging = true;
                dragOffsetX = mouseX - panelX;
                dragOffsetY = mouseY - panelY;
                return true;
            }

            if (handleBoardClick(mouseX, mouseY)) {
                return true;
            }

            if (handleSidePanelClick(mouseX, mouseY)) {
                return true;
            }
        }

        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            isDragging = false;
        }
        return super.mouseReleased(click);
    }

    private boolean handleBoardClick(float mouseX, float mouseY) {
        Chess mod = Chess.getInstance();
        if (mod == null) return false;
        ChessBoard board = mod.getBoard();
        if (board == null || board.isGameOver()) return false;

        boolean asWhite = (mod.getMyColor() != ChessBoard.COLOR_BLACK);
        float bX = panelX + 14.0f;
        float bY = panelY + 44.0f;
        float tileSize = 38.0f;
        float bSize = tileSize * 8.0f;

        if (mouseX < bX || mouseX >= bX + bSize || mouseY < bY || mouseY >= bY + bSize) {
            return false;
        }

        int clickDrawC = (int) ((mouseX - bX) / tileSize);
        int clickDrawR = (int) ((mouseY - bY) / tileSize);

        int col = asWhite ? clickDrawC : (7 - clickDrawC);
        int row = asWhite ? clickDrawR : (7 - clickDrawR);

        if (selectedCol != -1 && selectedRow != -1 && currentLegalMoves != null) {
            ChessMove targetMove = null;
            for (ChessMove m : currentLegalMoves) {
                if (m.getToCol() == col && m.getToRow() == row) {
                    targetMove = m;
                    break;
                }
            }

            if (targetMove != null) {
                boolean moved = mod.makeMyMove(selectedCol, selectedRow, col, row, targetMove.getPromoPiece());
                if (moved) {
                    selectedCol = -1;
                    selectedRow = -1;
                    currentLegalMoves = null;
                    return true;
                }
            }
        }

        int pieceCol = board.getColor(col, row);
        if (pieceCol == mod.getMyColor() && board.getTurnColor() == mod.getMyColor()) {
            selectedCol = col;
            selectedRow = row;
            currentLegalMoves = board.getLegalMoves(col, row);
            GuiSoundManager.playClick();
            return true;
        } else {
            selectedCol = -1;
            selectedRow = -1;
            currentLegalMoves = null;
        }

        return false;
    }

    private boolean handleSidePanelClick(float mouseX, float mouseY) {
        Chess mod = Chess.getInstance();
        if (mod == null) return false;

        float sX = panelX + 328.0f;
        float oppY = panelY + 44.0f + 68.0f;
        float w = panelW - 342.0f;
        float inX = sX + 8.0f;
        float inY = oppY + 23.0f;
        float inW = w - 16.0f;
        float inH = 20.0f;

        if (mouseX >= inX && mouseX <= inX + inW && mouseY >= inY && mouseY <= inY + inH) {
            roomInputFocused = true;
            lastBlinkTime = System.currentTimeMillis();
            return true;
        } else {
            roomInputFocused = false;
        }

        if (mod.getPendingInviteFrom() != null) {
            float invBoxY = inY + 25.0f;
            float bW = (inW - 6.0f) / 2.0f;
            float bH = 20.0f;
            float bY = invBoxY + 30.0f;

            if (mouseX >= inX && mouseX <= inX + bW && mouseY >= bY && mouseY <= bY + bH) {
                GuiSoundManager.playClick();
                mod.acceptInvite();
                return true;
            }

            float decX = inX + bW + 6.0f;
            if (mouseX >= decX && mouseX <= decX + bW && mouseY >= bY && mouseY <= bY + bH) {
                GuiSoundManager.playClick();
                mod.declineInvite();
                return true;
            }
        } else {
            float btnH = 19.0f;
            float btnY1 = inY + 24.0f;
            float btnY2 = btnY1 + 22.0f;
            float btnY3 = btnY2 + 22.0f;

            if (mouseX >= inX && mouseX <= inX + inW && mouseY >= btnY1 && mouseY <= btnY1 + btnH) {
                GuiSoundManager.playClick();
                ensureRoomCode();
                mod.hostGame(roomInput, true);
                return true;
            }

            if (mouseX >= inX && mouseX <= inX + inW && mouseY >= btnY2 && mouseY <= btnY2 + btnH) {
                GuiSoundManager.playClick();
                ensureRoomCode();
                mod.hostGame(roomInput, false);
                return true;
            }

            if (mouseX >= inX && mouseX <= inX + inW && mouseY >= btnY3 && mouseY <= btnY3 + btnH) {
                GuiSoundManager.playClick();
                if (!roomInput.isEmpty()) {
                    mod.joinGame(roomInput);
                }
                return true;
            }
        }

        float actY = oppY + 120.0f;
        float btnW = (w - 18.0f) / 2.0f;
        float btnH = 20.0f;
        float bY = actY + 6.0f;

        float rX = sX + 6.0f;
        if (mouseX >= rX && mouseX <= rX + btnW && mouseY >= bY && mouseY <= bY + btnH) {
            GuiSoundManager.playClick();
            mod.sendResign();
            return true;
        }

        float nX = rX + btnW + 6.0f;
        if (mouseX >= nX && mouseX <= nX + btnW && mouseY >= bY && mouseY <= bY + btnH) {
            GuiSoundManager.playClick();
            mod.sendReset();
            return true;
        }

        return false;
    }

    private void ensureRoomCode() {
        if (roomInput == null || roomInput.trim().isEmpty()) {
            int code = 1000 + new Random().nextInt(9000);
            roomInput = String.valueOf(code);
            Chess.getInstance().setRoomOrOpponent(roomInput);
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (roomInputFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                roomInputFocused = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !roomInput.isEmpty()) {
                roomInput = roomInput.substring(0, roomInput.length() - 1);
                Chess.getInstance().setRoomOrOpponent(roomInput);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER) {
                roomInputFocused = false;
                if (!roomInput.isEmpty()) {
                    Chess.getInstance().joinGame(roomInput);
                }
                return true;
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (roomInputFocused) {
            String ch = input.asString();
            if (ch != null && !ch.isEmpty() && roomInput.length() < 24) {
                roomInput += ch;
                Chess.getInstance().setRoomOrOpponent(roomInput);
                return true;
            }
        }
        return super.charTyped(input);
    }
}
