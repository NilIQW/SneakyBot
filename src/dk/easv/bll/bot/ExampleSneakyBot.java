package dk.easv.bll.bot;

import dk.easv.bll.field.IField;
import dk.easv.bll.game.GameState;
import dk.easv.bll.game.IGameState;
import dk.easv.bll.move.IMove;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static dk.easv.bll.game.GameManager.isWin;

public class ExampleSneakyBot implements IBot {
    final int moveTimeMs = 1000;
    private String BOT_NAME = getClass().getSimpleName();

    private GameSimulator createSimulator(IGameState state) {
        GameSimulator simulator = new GameSimulator(new GameState());
        simulator.setGameOver(GameOverState.Active);
        simulator.setCurrentPlayer(state.getMoveNumber() % 2);
        simulator.getCurrentState().setRoundNumber(state.getRoundNumber());
        simulator.getCurrentState().setMoveNumber(state.getMoveNumber());
        simulator.getCurrentState().getField().setBoard(state.getField().getBoard());
        simulator.getCurrentState().getField().setMacroboard(state.getField().getMacroboard());
        return simulator;
    }

    @Override
    public IMove doMove(IGameState state) {
        if (state.getMoveNumber() == 0) {
            // Select a random initial move if it's the first move of the game
            return selectInitialMove(state);
        } else {
            // Otherwise, calculate the winning move
            return calculateWinningMove(state, moveTimeMs);
        }
    }

    private IMove selectInitialMove(IGameState state) {
        List<IMove> availableMoves = state.getField().getAvailableMoves();

        // Prioritize the center of the board as the initial move
        for (IMove move : availableMoves) {
            if (move.getX() == 4 && move.getY() == 4) {
                return move;
            }
        }

        // If the center is not available, choose a random move
        Random rand = new Random();
        return availableMoves.get(rand.nextInt(availableMoves.size()));
    }


    private IMove calculateWinningMove(IGameState state, int maxTimeMs) {
        long startTime = System.currentTimeMillis();
        Random rand = new Random();

        // Get the opponent's player ID
        int opponentPlayer = (state.getMoveNumber() + 1) % 2;

        List<IMove> availableMoves = state.getField().getAvailableMoves();
        IMove bestMove = null;
        int bestScore = Integer.MIN_VALUE;

        // Iterate through all available moves
        for (IMove move : availableMoves) {
            GameSimulator simulator = createSimulator(state);
            simulator.updateGame(move); // Make the move

            // Check if the opponent wins after this move
            if (isWinForPlayer(simulator.getCurrentState(), opponentPlayer)) {
                return move; // Return the move to block the opponent's win
            }

            // Check if the opponent has a winning move next
            boolean opponentHasWinningMove = hasPotentialWin(simulator.getCurrentState(), opponentPlayer);
            int score = evaluateGameState(simulator.getCurrentState(), state.getMoveNumber() % 2);

            // Prioritize moves that block the opponent's potential winning moves
            if (!opponentHasWinningMove) {
                score += 10; // Give a bonus score to moves that don't allow the opponent to win next turn
            }

            // Update the best move if necessary
            if (score > bestScore) {
                bestMove = move;
                bestScore = score;
            }

            // Check if the time limit has been reached
            if (System.currentTimeMillis() - startTime >= maxTimeMs) {
                break; // Exit the loop if time limit exceeded
            }
        }

        // If no winning move by the opponent is found, return the best move based on evaluation function
        if (bestMove != null) {
            return bestMove;
        }

        // If no moves were evaluated, return a random move
        return availableMoves.get(rand.nextInt(availableMoves.size()));
    }

    private boolean hasPotentialWin(IGameState state, int player) {
        List<IMove> availableMoves = state.getField().getAvailableMoves();
        for (IMove move : availableMoves) {
            GameSimulator simulator = createSimulator(state);
            simulator.updateGame(move);
            if (isWinForPlayer(simulator.getCurrentState(), player)) {
                return true;
            }
        }
        return false;
    }


    private boolean isWinForPlayer(IGameState state, int player) {
        String[][] board = state.getField().getBoard();
        String[][] macroboard = state.getField().getMacroboard();

        // Check for wins in the microboards
        for (int i = 0; i < 9; i += 3) {
            for (int j = 0; j < 9; j += 3) {
                // Check each microboard for a win
                if (isMicroboardWin(board, i, j, player)) {
                    int macroX = i / 3;
                    int macroY = j / 3;
                    // Check if the macroboard itself leads to a win
                    if (isWin(macroboard, new Move(macroX, macroY), Integer.toString(player))) {
                        return true;
                    }
                }
            }
        }

        return false; // No win found
    }

    private boolean isMicroboardWin(String[][] board, int startX, int startY, int player) {
        // Check horizontal, vertical, and diagonal wins within a microboard
        for (int i = startX; i < startX + 3; i++) {
            for (int j = startY; j < startY + 3; j++) {
                if (board[i][startY].equals(Integer.toString(player)) &&
                        board[i][startY + 1].equals(Integer.toString(player)) &&
                        board[i][startY + 2].equals(Integer.toString(player))) {
                    return true; // Horizontal win
                }

                if (board[startX][j].equals(Integer.toString(player)) &&
                        board[startX + 1][j].equals(Integer.toString(player)) &&
                        board[startX + 2][j].equals(Integer.toString(player))) {
                    return true; // Vertical win
                }
            }
        }

        // Check diagonals
        if (board[startX][startY].equals(Integer.toString(player)) &&
                board[startX + 1][startY + 1].equals(Integer.toString(player)) &&
                board[startX + 2][startY + 2].equals(Integer.toString(player))) {
            return true; // Diagonal win
        }

        if (board[startX][startY + 2].equals(Integer.toString(player)) &&
                board[startX + 1][startY + 1].equals(Integer.toString(player)) &&
                board[startX + 2][startY].equals(Integer.toString(player))) {
            return true; // Diagonal win
        }

        return false; // No win found within the microboard
    }
    private int evaluateGameState(IGameState state, int player) {
        int score = 0;

        // Evaluate the control of microboards
        score += evaluateMicroboards(state, player);

        // Evaluate the proximity to winning moves
        score += evaluateWinningMoves(state, player);

        // Evaluate the blocking of opponent's moves
        score += evaluateBlockingOpponentMoves(state, player);

        return score;
    }

    private int evaluateMicroboards(IGameState state, int player) {
        int playerScore = 0;
        int opponentScore = 0;

        String[][] macroboard = state.getField().getMacroboard();

        // Check control of microboards
        for (int i = 0; i < 9; i += 3) {
            for (int j = 0; j < 9; j += 3) {
                int microboardScore = evaluateMicroboard(state, player, i, j);
                if (macroboard[i / 3][j / 3].equals(Integer.toString(player))) {
                    playerScore += microboardScore;
                } else {
                    opponentScore += microboardScore;
                }
            }
        }

        return playerScore - opponentScore;
    }

    private int evaluateMicroboard(IGameState state, int player, int startX, int startY) {
        String[][] board = state.getField().getBoard();

        // Evaluate control of a single microboard
        int playerCount = 0;
        int opponentCount = 0;

        for (int i = startX; i < startX + 3; i++) {
            for (int j = startY; j < startY + 3; j++) {
                if (board[i][j].equals(Integer.toString(player))) {
                    playerCount++;
                } else if (!board[i][j].equals(IField.EMPTY_FIELD)) {
                    opponentCount++;
                }
            }
        }

        // Assign scores based on control
        if (playerCount > opponentCount) {
            return 1;
        } else if (playerCount < opponentCount) {
            return -1;
        } else {
            return 0;
        }
    }

    private int evaluateWinningMoves(IGameState state, int player) {
        int winningMoves = 0;

        // Evaluate rows
        for (int i = 0; i < 9; i++) {
            if (hasPotentialWin(state, player, i, 0, 0, 1)) {
                winningMoves++;
            }
        }

        // Evaluate columns
        for (int j = 0; j < 9; j++) {
            if (hasPotentialWin(state, player, 0, j, 1, 0)) {
                winningMoves++;
            }
        }

        // Evaluate diagonals
        if (hasPotentialWin(state, player, 0, 0, 1, 1)) {
            winningMoves++;
        }
        if (hasPotentialWin(state, player, 0, 2, 1, -1)) {
            winningMoves++;
        }

        return winningMoves;
    }

    private boolean hasPotentialWin(IGameState state, int player, int startX, int startY, int dx, int dy) {
        String[][] board = state.getField().getBoard();
        int count = 0;
        for (int i = 0; i < 3; i++) {
            int x = startX + i * dx;
            int y = startY + i * dy;
            if (board[x][y].equals(Integer.toString(player)) || board[x][y].equals(IField.EMPTY_FIELD)) {
                count++;
            }
        }
        return count == 2;
    }


    private int evaluateBlockingOpponentMoves(IGameState state, int player) {
        int blockingMoves = 0;

        // Evaluate rows
        for (int i = 0; i < 9; i++) {
            if (hasPotentialWin(state, 1 - player, i, 0, 0, 1)) {
                blockingMoves++;
            }
        }

        // Evaluate columns
        for (int j = 0; j < 9; j++) {
            if (hasPotentialWin(state, 1 - player, 0, j, 1, 0)) {
                blockingMoves++;
            }
        }

        // Evaluate diagonals
        if (hasPotentialWin(state, 1 - player, 0, 0, 1, 1)) {
            blockingMoves++;
        }
        if (hasPotentialWin(state, 1 - player, 0, 2, 1, -1)) {
            blockingMoves++;
        }

        return blockingMoves;
    }


    @Override
    public String getBotName() {
        return BOT_NAME;
    }

    public enum GameOverState {
        Active,
        Win,
        Tie
    }

    public class Move implements IMove {
        int x = 0;
        int y = 0;

        public Move(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Move move = (Move) o;
            return x == move.x && y == move.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    class GameSimulator {
        private final IGameState currentState;
        private int currentPlayer = 0; //player0 == 0 && player1 == 1
        private volatile GameOverState gameOver = GameOverState.Active;

        public void setGameOver(GameOverState state) {
            gameOver = state;
        }

        public GameOverState getGameOver() {
            return gameOver;
        }

        public void setCurrentPlayer(int player) {
            currentPlayer = player;
        }

        public IGameState getCurrentState() {
            return currentState;
        }

        public GameSimulator(IGameState currentState) {
            this.currentState = currentState;
        }

        public Boolean updateGame(IMove move) {
            if (!verifyMoveLegality(move))
                return false;

            updateBoard(move);
            currentPlayer = (currentPlayer + 1) % 2;

            return true;
        }

        private Boolean verifyMoveLegality(IMove move) {
                IField field = currentState.getField();
            boolean isValid = field.isInActiveMicroboard(move.getX(), move.getY());

            if (isValid && (move.getX() < 0 || 9 <= move.getX())) isValid = false;
            if (isValid && (move.getY() < 0 || 9 <= move.getY())) isValid = false;

            if (isValid && !field.getBoard()[move.getX()][move.getY()].equals(IField.EMPTY_FIELD))
                isValid = false;

            return isValid;
        }

        private void updateBoard(IMove move) {
            String[][] board = currentState.getField().getBoard();
            board[move.getX()][move.getY()] = currentPlayer + "";
            currentState.setMoveNumber(currentState.getMoveNumber() + 1);
            if (currentState.getMoveNumber() % 2 == 0) {
                currentState.setRoundNumber(currentState.getRoundNumber() + 1);
            }
            checkAndUpdateIfWin(move);
            updateMacroboard(move);

        }

        private void checkAndUpdateIfWin(IMove move) {
            String[][] macroBoard = currentState.getField().getMacroboard();
            int macroX = move.getX() / 3;
            int macroY = move.getY() / 3;

            if (macroBoard[macroX][macroY].equals(IField.EMPTY_FIELD) ||
                    macroBoard[macroX][macroY].equals(IField.AVAILABLE_FIELD)) {

                String[][] board = getCurrentState().getField().getBoard();

                if (isWin(board, move, "" + currentPlayer))
                    macroBoard[macroX][macroY] = currentPlayer + "";
                else if (isTie(board, move))
                    macroBoard[macroX][macroY] = "TIE";

                //Check macro win
                if (isWin(macroBoard, new Move(macroX, macroY), "" + currentPlayer))
                    gameOver = GameOverState.Win;
                else if (isTie(macroBoard, new Move(macroX, macroY)))
                    gameOver = GameOverState.Tie;
            }

        }

        private boolean isTie(String[][] board, IMove move) {
            int localX = move.getX() % 3;
            int localY = move.getY() % 3;
            int startX = move.getX() - (localX);
            int startY = move.getY() - (localY);

            for (int i = startX; i < startX + 3; i++) {
                for (int k = startY; k < startY + 3; k++) {
                    if (board[i][k].equals(IField.AVAILABLE_FIELD) ||
                            board[i][k].equals(IField.EMPTY_FIELD))
                        return false;
                }
            }
            return true;
        }


        public boolean isWin(String[][] board, IMove move, String currentPlayer) {
            int localX = move.getX() % 3;
            int localY = move.getY() % 3;
            int startX = move.getX() - (localX);
            int startY = move.getY() - (localY);

            //check col
            for (int i = startY; i < startY + 3; i++) {
                if (!board[move.getX()][i].equals(currentPlayer))
                    break;
                if (i == startY + 3 - 1) return true;
            }

            //check row
            for (int i = startX; i < startX + 3; i++) {
                if (!board[i][move.getY()].equals(currentPlayer))
                    break;
                if (i == startX + 3 - 1) return true;
            }

            //check diagonal
            if (localX == localY) {
                //we're on a diagonal
                int y = startY;
                for (int i = startX; i < startX + 3; i++) {
                    if (!board[i][y++].equals(currentPlayer))
                        break;
                    if (i == startX + 3 - 1) return true;
                }
            }

            //check anti diagonal
            if (localX + localY == 3 - 1) {
                int less = 0;
                for (int i = startX; i < startX + 3; i++) {
                    if (!board[i][(startY + 2) - less++].equals(currentPlayer))
                        break;
                    if (i == startX + 3 - 1) return true;
                }
            }
            return false;
        }

        private void updateMacroboard(IMove move) {
            String[][] macroBoard = currentState.getField().getMacroboard();
            for (int i = 0; i < macroBoard.length; i++)
                for (int k = 0; k < macroBoard[i].length; k++) {
                    if (macroBoard[i][k].equals(IField.AVAILABLE_FIELD))
                        macroBoard[i][k] = IField.EMPTY_FIELD;
                }

            int xTrans = move.getX() % 3;
            int yTrans = move.getY() % 3;

            if (macroBoard[xTrans][yTrans].equals(IField.EMPTY_FIELD))
                macroBoard[xTrans][yTrans] = IField.AVAILABLE_FIELD;
            else {
                // Field is already won, set all fields not won to avail.
                for (int i = 0; i < macroBoard.length; i++)
                    for (int k = 0; k < macroBoard[i].length; k++) {
                        if (macroBoard[i][k].equals(IField.EMPTY_FIELD))
                            macroBoard[i][k] = IField.AVAILABLE_FIELD;
                    }
            }
        }
    }

}

