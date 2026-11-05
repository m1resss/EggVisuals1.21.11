package egg.client.module.minigames.chess;

import java.util.ArrayList;
import java.util.List;

public class ChessBoard {
    public static final int COLOR_NONE = 0;
    public static final int COLOR_WHITE = 1;
    public static final int COLOR_BLACK = 2;

    private final ChessPiece[][] boardPieces = new ChessPiece[8][8];
    private final int[][] boardColors = new int[8][8];

    private int turnColor = COLOR_WHITE;
    private int enPassantCol = -1;
    private int enPassantRow = -1;

    private boolean whiteKingMoved = false;
    private boolean whiteRookA1Moved = false;
    private boolean whiteRookH1Moved = false;
    private boolean blackKingMoved = false;
    private boolean blackRookA8Moved = false;
    private boolean blackRookH8Moved = false;

    private final List<ChessMove> moveHistory = new ArrayList<>();
    private final List<ChessPiece> capturedByWhite = new ArrayList<>();
    private final List<ChessPiece> capturedByBlack = new ArrayList<>();

    private boolean gameOver = false;
    private int winnerColor = COLOR_NONE;
    private boolean isStalemate = false;

    public ChessBoard() {
        reset();
    }

    public void reset() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                boardPieces[r][c] = ChessPiece.NONE;
                boardColors[r][c] = COLOR_NONE;
            }
        }

        boardPieces[0][0] = ChessPiece.ROOK;
        boardPieces[0][1] = ChessPiece.KNIGHT;
        boardPieces[0][2] = ChessPiece.BISHOP;
        boardPieces[0][3] = ChessPiece.QUEEN;
        boardPieces[0][4] = ChessPiece.KING;
        boardPieces[0][5] = ChessPiece.BISHOP;
        boardPieces[0][6] = ChessPiece.KNIGHT;
        boardPieces[0][7] = ChessPiece.ROOK;
        for (int c = 0; c < 8; c++) {
            boardColors[0][c] = COLOR_BLACK;
            boardPieces[1][c] = ChessPiece.PAWN;
            boardColors[1][c] = COLOR_BLACK;
        }

        boardPieces[7][0] = ChessPiece.ROOK;
        boardPieces[7][1] = ChessPiece.KNIGHT;
        boardPieces[7][2] = ChessPiece.BISHOP;
        boardPieces[7][3] = ChessPiece.QUEEN;
        boardPieces[7][4] = ChessPiece.KING;
        boardPieces[7][5] = ChessPiece.BISHOP;
        boardPieces[7][6] = ChessPiece.KNIGHT;
        boardPieces[7][7] = ChessPiece.ROOK;
        for (int c = 0; c < 8; c++) {
            boardColors[7][c] = COLOR_WHITE;
            boardPieces[6][c] = ChessPiece.PAWN;
            boardColors[6][c] = COLOR_WHITE;
        }

        turnColor = COLOR_WHITE;
        enPassantCol = -1;
        enPassantRow = -1;

        whiteKingMoved = false;
        whiteRookA1Moved = false;
        whiteRookH1Moved = false;
        blackKingMoved = false;
        blackRookA8Moved = false;
        blackRookH8Moved = false;

        moveHistory.clear();
        capturedByWhite.clear();
        capturedByBlack.clear();

        gameOver = false;
        winnerColor = COLOR_NONE;
        isStalemate = false;
    }

    public ChessPiece getPiece(int col, int row) {
        if (col < 0 || col >= 8 || row < 0 || row >= 8) return ChessPiece.NONE;
        return boardPieces[row][col];
    }

    public int getColor(int col, int row) {
        if (col < 0 || col >= 8 || row < 0 || row >= 8) return COLOR_NONE;
        return boardColors[row][col];
    }

    public int getTurnColor() {
        return turnColor;
    }

    public boolean isWhiteTurn() {
        return turnColor == COLOR_WHITE;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public int getWinnerColor() {
        return winnerColor;
    }

    public boolean isStalemate() {
        return isStalemate;
    }

    public List<ChessMove> getMoveHistory() {
        return moveHistory;
    }

    public List<ChessPiece> getCapturedByWhite() {
        return capturedByWhite;
    }

    public List<ChessPiece> getCapturedByBlack() {
        return capturedByBlack;
    }

    public ChessMove getLastMove() {
        return moveHistory.isEmpty() ? null : moveHistory.get(moveHistory.size() - 1);
    }

    public boolean isSquareAttacked(int targetCol, int targetRow, int byColor) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (boardColors[r][c] == byColor) {
                    if (canPieceAttack(c, r, targetCol, targetRow)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean canPieceAttack(int fc, int fr, int tc, int tr) {
        ChessPiece p = boardPieces[fr][fc];
        int color = boardColors[fr][fc];
        int dc = tc - fc;
        int dr = tr - fr;

        switch (p) {
            case PAWN -> {
                int forward = (color == COLOR_WHITE) ? -1 : 1;
                return dr == forward && Math.abs(dc) == 1;
            }
            case KNIGHT -> {
                return (Math.abs(dc) == 1 && Math.abs(dr) == 2) || (Math.abs(dc) == 2 && Math.abs(dr) == 1);
            }
            case BISHOP -> {
                if (Math.abs(dc) != Math.abs(dr)) return false;
                return isPathClear(fc, fr, tc, tr);
            }
            case ROOK -> {
                if (dc != 0 && dr != 0) return false;
                return isPathClear(fc, fr, tc, tr);
            }
            case QUEEN -> {
                if (Math.abs(dc) != Math.abs(dr) && dc != 0 && dr != 0) return false;
                return isPathClear(fc, fr, tc, tr);
            }
            case KING -> {
                return Math.abs(dc) <= 1 && Math.abs(dr) <= 1;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean isPathClear(int fc, int fr, int tc, int tr) {
        int stepC = Integer.compare(tc, fc);
        int stepR = Integer.compare(tr, fr);
        int currC = fc + stepC;
        int currR = fr + stepR;
        while (currC != tc || currR != tr) {
            if (boardPieces[currR][currC] != ChessPiece.NONE) {
                return false;
            }
            currC += stepC;
            currR += stepR;
        }
        return true;
    }

    public boolean isKingInCheck(int color) {
        int kingC = -1;
        int kingR = -1;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (boardPieces[r][c] == ChessPiece.KING && boardColors[r][c] == color) {
                    kingC = c;
                    kingR = r;
                    break;
                }
            }
            if (kingC != -1) break;
        }
        if (kingC == -1) return false;
        int enemyColor = (color == COLOR_WHITE) ? COLOR_BLACK : COLOR_WHITE;
        return isSquareAttacked(kingC, kingR, enemyColor);
    }

    public List<ChessMove> getLegalMoves(int col, int row) {
        List<ChessMove> legal = new ArrayList<>();
        if (col < 0 || col >= 8 || row < 0 || row >= 8) return legal;
        if (gameOver) return legal;

        int color = boardColors[row][col];
        if (color != turnColor) return legal;

        List<ChessMove> candidates = getPseudoMoves(col, row);
        for (ChessMove m : candidates) {
            if (simulateAndVerifyLegal(m)) {
                legal.add(m);
            }
        }
        return legal;
    }

    private List<ChessMove> getPseudoMoves(int fc, int fr) {
        List<ChessMove> moves = new ArrayList<>();
        ChessPiece p = boardPieces[fr][fc];
        int color = boardColors[fr][fc];
        int enemy = (color == COLOR_WHITE) ? COLOR_BLACK : COLOR_WHITE;

        switch (p) {
            case PAWN -> {
                int forward = (color == COLOR_WHITE) ? -1 : 1;
                int startRow = (color == COLOR_WHITE) ? 6 : 1;
                int promoRow = (color == COLOR_WHITE) ? 0 : 7;

                int oneR = fr + forward;
                if (oneR >= 0 && oneR < 8 && boardPieces[oneR][fc] == ChessPiece.NONE) {
                    if (oneR == promoRow) {
                        moves.add(new ChessMove(fc, fr, fc, oneR, p, ChessPiece.NONE, ChessPiece.QUEEN, false, false));
                        moves.add(new ChessMove(fc, fr, fc, oneR, p, ChessPiece.NONE, ChessPiece.ROOK, false, false));
                        moves.add(new ChessMove(fc, fr, fc, oneR, p, ChessPiece.NONE, ChessPiece.BISHOP, false, false));
                        moves.add(new ChessMove(fc, fr, fc, oneR, p, ChessPiece.NONE, ChessPiece.KNIGHT, false, false));
                    } else {
                        moves.add(new ChessMove(fc, fr, fc, oneR, p, ChessPiece.NONE, ChessPiece.NONE, false, false));
                        if (fr == startRow) {
                            int twoR = fr + forward * 2;
                            if (boardPieces[twoR][fc] == ChessPiece.NONE) {
                                moves.add(new ChessMove(fc, fr, fc, twoR, p, ChessPiece.NONE, ChessPiece.NONE, false, false));
                            }
                        }
                    }
                }

                for (int dc : new int[]{-1, 1}) {
                    int tc = fc + dc;
                    int tr = fr + forward;
                    if (tc >= 0 && tc < 8 && tr >= 0 && tr < 8) {
                        if (boardColors[tr][tc] == enemy) {
                            if (tr == promoRow) {
                                moves.add(new ChessMove(fc, fr, tc, tr, p, boardPieces[tr][tc], ChessPiece.QUEEN, false, false));
                                moves.add(new ChessMove(fc, fr, tc, tr, p, boardPieces[tr][tc], ChessPiece.ROOK, false, false));
                                moves.add(new ChessMove(fc, fr, tc, tr, p, boardPieces[tr][tc], ChessPiece.BISHOP, false, false));
                                moves.add(new ChessMove(fc, fr, tc, tr, p, boardPieces[tr][tc], ChessPiece.KNIGHT, false, false));
                            } else {
                                moves.add(new ChessMove(fc, fr, tc, tr, p, boardPieces[tr][tc], ChessPiece.NONE, false, false));
                            }
                        } else if (tc == enPassantCol && tr == enPassantRow) {
                            moves.add(new ChessMove(fc, fr, tc, tr, p, ChessPiece.PAWN, ChessPiece.NONE, false, true));
                        }
                    }
                }
            }
            case KNIGHT -> {
                int[][] jumps = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
                for (int[] j : jumps) {
                    int tc = fc + j[0];
                    int tr = fr + j[1];
                    if (tc >= 0 && tc < 8 && tr >= 0 && tr < 8 && boardColors[tr][tc] != color) {
                        moves.add(new ChessMove(fc, fr, tc, tr, p, boardPieces[tr][tc], ChessPiece.NONE, false, false));
                    }
                }
            }
            case BISHOP -> addRayMoves(moves, fc, fr, p, color, new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}});
            case ROOK -> addRayMoves(moves, fc, fr, p, color, new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}});
            case QUEEN -> addRayMoves(moves, fc, fr, p, color, new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}});
            case KING -> {
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dc == 0 && dr == 0) continue;
                        int tc = fc + dc;
                        int tr = fr + dr;
                        if (tc >= 0 && tc < 8 && tr >= 0 && tr < 8 && boardColors[tr][tc] != color) {
                            moves.add(new ChessMove(fc, fr, tc, tr, p, boardPieces[tr][tc], ChessPiece.NONE, false, false));
                        }
                    }
                }

                if (!isKingInCheck(color)) {
                    if (color == COLOR_WHITE && !whiteKingMoved && fr == 7 && fc == 4) {
                        if (!whiteRookH1Moved && boardPieces[7][5] == ChessPiece.NONE && boardPieces[7][6] == ChessPiece.NONE && boardPieces[7][7] == ChessPiece.ROOK) {
                            if (!isSquareAttacked(5, 7, enemy) && !isSquareAttacked(6, 7, enemy)) {
                                moves.add(new ChessMove(4, 7, 6, 7, p, ChessPiece.NONE, ChessPiece.NONE, true, false));
                            }
                        }
                        if (!whiteRookA1Moved && boardPieces[7][1] == ChessPiece.NONE && boardPieces[7][2] == ChessPiece.NONE && boardPieces[7][3] == ChessPiece.NONE && boardPieces[7][0] == ChessPiece.ROOK) {
                            if (!isSquareAttacked(3, 7, enemy) && !isSquareAttacked(2, 7, enemy)) {
                                moves.add(new ChessMove(4, 7, 2, 7, p, ChessPiece.NONE, ChessPiece.NONE, true, false));
                            }
                        }
                    } else if (color == COLOR_BLACK && !blackKingMoved && fr == 0 && fc == 4) {
                        if (!blackRookH8Moved && boardPieces[0][5] == ChessPiece.NONE && boardPieces[0][6] == ChessPiece.NONE && boardPieces[0][7] == ChessPiece.ROOK) {
                            if (!isSquareAttacked(5, 0, enemy) && !isSquareAttacked(6, 0, enemy)) {
                                moves.add(new ChessMove(4, 0, 6, 0, p, ChessPiece.NONE, ChessPiece.NONE, true, false));
                            }
                        }
                        if (!blackRookA8Moved && boardPieces[0][1] == ChessPiece.NONE && boardPieces[0][2] == ChessPiece.NONE && boardPieces[0][3] == ChessPiece.NONE && boardPieces[0][0] == ChessPiece.ROOK) {
                            if (!isSquareAttacked(3, 0, enemy) && !isSquareAttacked(2, 0, enemy)) {
                                moves.add(new ChessMove(4, 0, 2, 0, p, ChessPiece.NONE, ChessPiece.NONE, true, false));
                            }
                        }
                    }
                }
            }
        }
        return moves;
    }

    private void addRayMoves(List<ChessMove> moves, int fc, int fr, ChessPiece p, int color, int[][] dirs) {
        for (int[] d : dirs) {
            int tc = fc + d[0];
            int tr = fr + d[1];
            while (tc >= 0 && tc < 8 && tr >= 0 && tr < 8) {
                int targetCol = boardColors[tr][tc];
                if (targetCol == color) {
                    break;
                }
                moves.add(new ChessMove(fc, fr, tc, tr, p, boardPieces[tr][tc], ChessPiece.NONE, false, false));
                if (targetCol != COLOR_NONE) {
                    break;
                }
                tc += d[0];
                tr += d[1];
            }
        }
    }

    private boolean simulateAndVerifyLegal(ChessMove move) {
        int fc = move.getFromCol();
        int fr = move.getFromRow();
        int tc = move.getToCol();
        int tr = move.getToRow();

        ChessPiece origFromPiece = boardPieces[fr][fc];
        int origFromColor = boardColors[fr][fc];
        ChessPiece origToPiece = boardPieces[tr][tc];
        int origToColor = boardColors[tr][tc];

        ChessPiece origEpPawn = ChessPiece.NONE;
        int origEpColor = COLOR_NONE;
        if (move.isEnPassant()) {
            origEpPawn = boardPieces[fr][tc];
            origEpColor = boardColors[fr][tc];
            boardPieces[fr][tc] = ChessPiece.NONE;
            boardColors[fr][tc] = COLOR_NONE;
        }

        boardPieces[tr][tc] = origFromPiece;
        boardColors[tr][tc] = origFromColor;
        boardPieces[fr][fc] = ChessPiece.NONE;
        boardColors[fr][fc] = COLOR_NONE;

        boolean kingInCheck = isKingInCheck(origFromColor);

        boardPieces[fr][fc] = origFromPiece;
        boardColors[fr][fc] = origFromColor;
        boardPieces[tr][tc] = origToPiece;
        boardColors[tr][tc] = origToColor;

        if (move.isEnPassant()) {
            boardPieces[fr][tc] = origEpPawn;
            boardColors[fr][tc] = origEpColor;
        }

        return !kingInCheck;
    }

    public boolean makeMove(int fc, int fr, int tc, int tr, ChessPiece promo) {
        List<ChessMove> legal = getLegalMoves(fc, fr);
        ChessMove chosen = null;
        for (ChessMove m : legal) {
            if (m.getToCol() == tc && m.getToRow() == tr) {
                if (promo != null && promo != ChessPiece.NONE) {
                    if (m.getPromoPiece() == promo) {
                        chosen = m;
                        break;
                    }
                } else {
                    if (m.getPromoPiece() == ChessPiece.NONE) {
                        chosen = m;
                        break;
                    } else if (chosen == null) {
                        chosen = m;
                    }
                }
            }
        }

        if (chosen == null) return false;

        executeValidatedMove(chosen);
        return true;
    }

    public void forceMove(int fc, int fr, int tc, int tr, ChessPiece promo) {
        List<ChessMove> candidates = getPseudoMoves(fc, fr);
        ChessMove chosen = null;
        for (ChessMove m : candidates) {
            if (m.getToCol() == tc && m.getToRow() == tr) {
                if (promo != null && promo != ChessPiece.NONE) {
                    if (m.getPromoPiece() == promo) {
                        chosen = m;
                        break;
                    }
                } else {
                    if (m.getPromoPiece() == ChessPiece.NONE) {
                        chosen = m;
                        break;
                    } else if (chosen == null) {
                        chosen = m;
                    }
                }
            }
        }
        if (chosen == null) {
            ChessPiece p = boardPieces[fr][fc];
            ChessPiece captured = boardPieces[tr][tc];
            boolean isCastle = (p == ChessPiece.KING && Math.abs(tc - fc) == 2);
            boolean isEp = (p == ChessPiece.PAWN && tc == enPassantCol && tr == enPassantRow && captured == ChessPiece.NONE);
            chosen = new ChessMove(fc, fr, tc, tr, p, isEp ? ChessPiece.PAWN : captured, promo, isCastle, isEp);
        }
        executeValidatedMove(chosen);
    }

    private void executeValidatedMove(ChessMove m) {
        int fc = m.getFromCol();
        int fr = m.getFromRow();
        int tc = m.getToCol();
        int tr = m.getToRow();
        ChessPiece p = boardPieces[fr][fc];
        int movingColor = boardColors[fr][fc];

        if (m.isEnPassant()) {
            boardPieces[fr][tc] = ChessPiece.NONE;
            boardColors[fr][tc] = COLOR_NONE;
            if (movingColor == COLOR_WHITE) {
                capturedByWhite.add(ChessPiece.PAWN);
            } else {
                capturedByBlack.add(ChessPiece.PAWN);
            }
        } else if (boardPieces[tr][tc] != ChessPiece.NONE) {
            if (movingColor == COLOR_WHITE) {
                capturedByWhite.add(boardPieces[tr][tc]);
            } else {
                capturedByBlack.add(boardPieces[tr][tc]);
            }
        }

        boardPieces[tr][tc] = (m.getPromoPiece() != ChessPiece.NONE && m.getPromoPiece() != null) ? m.getPromoPiece() : p;
        boardColors[tr][tc] = movingColor;
        boardPieces[fr][fc] = ChessPiece.NONE;
        boardColors[fr][fc] = COLOR_NONE;

        if (m.isCastling()) {
            if (tc == 6) {
                boardPieces[tr][5] = ChessPiece.ROOK;
                boardColors[tr][5] = movingColor;
                boardPieces[tr][7] = ChessPiece.NONE;
                boardColors[tr][7] = COLOR_NONE;
            } else if (tc == 2) {
                boardPieces[tr][3] = ChessPiece.ROOK;
                boardColors[tr][3] = movingColor;
                boardPieces[tr][0] = ChessPiece.NONE;
                boardColors[tr][0] = COLOR_NONE;
            }
        }

        if (p == ChessPiece.KING) {
            if (movingColor == COLOR_WHITE) whiteKingMoved = true;
            else blackKingMoved = true;
        } else if (p == ChessPiece.ROOK) {
            if (movingColor == COLOR_WHITE) {
                if (fr == 7 && fc == 0) whiteRookA1Moved = true;
                if (fr == 7 && fc == 7) whiteRookH1Moved = true;
            } else {
                if (fr == 0 && fc == 0) blackRookA8Moved = true;
                if (fr == 0 && fc == 7) blackRookH8Moved = true;
            }
        }

        if (p == ChessPiece.PAWN && Math.abs(tr - fr) == 2) {
            enPassantCol = fc;
            enPassantRow = (fr + tr) / 2;
        } else {
            enPassantCol = -1;
            enPassantRow = -1;
        }

        int nextTurn = (movingColor == COLOR_WHITE) ? COLOR_BLACK : COLOR_WHITE;
        turnColor = nextTurn;

        boolean oppCheck = isKingInCheck(nextTurn);
        m.setCheck(oppCheck);

        boolean hasLegalMoves = false;
        outer:
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (boardColors[r][c] == nextTurn) {
                    if (!getLegalMoves(c, r).isEmpty()) {
                        hasLegalMoves = true;
                        break outer;
                    }
                }
            }
        }

        if (!hasLegalMoves) {
            gameOver = true;
            if (oppCheck) {
                m.setCheckmate(true);
                winnerColor = movingColor;
            } else {
                isStalemate = true;
                winnerColor = COLOR_NONE;
            }
        }

        moveHistory.add(m);
    }

    public void resign(int resigningColor) {
        if (gameOver) return;
        gameOver = true;
        winnerColor = (resigningColor == COLOR_WHITE) ? COLOR_BLACK : COLOR_WHITE;
    }
}
