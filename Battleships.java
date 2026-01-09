import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Battleships {

    private static final int GRID_SIZE = 10;
    private static final int TIMEOUT_MS = 1000;
    private static final int MAX_RETRIES = 3;


    enum Mode { SERVER, CLIENT }
    enum CellState { UNKNOWN, WATER, SHIP, HIT_SHIP, MISS }


    static class Point {
        int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Point point = (Point) o;
            return x == point.x && y == point.y;
        }

        @Override
        public int hashCode() { return Objects.hash(x, y); }

        @Override
        public String toString() {
            return (char)('A' + x) + String.valueOf(y + 1);
        }

        static Point parse(String s) {
            if (s == null || s.length() < 2) return null;
            try {
                int x = Character.toUpperCase(s.charAt(0)) - 'A';
                int y = Integer.parseInt(s.substring(1)) - 1;
                if (x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE) {
                    return new Point(x, y);
                }
            } catch (NumberFormatException e) {
                return null;
            }
            return null;
        }

        List<Point> getNeighbors() {
            List<Point> neighbors = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx >= 0 && nx < GRID_SIZE && ny >= 0 && ny < GRID_SIZE) {
                        neighbors.add(new Point(nx, ny));
                    }
                }
            }
            return neighbors;
        }
    }


    static class Board {

        boolean[][] ships = new boolean[GRID_SIZE][GRID_SIZE];

        CellState[][] view = new CellState[GRID_SIZE][GRID_SIZE];

        List<Set<Point>> shipSegments = new ArrayList<>();

        Board() {
            for (int y = 0; y < GRID_SIZE; y++) {
                Arrays.fill(view[y], CellState.UNKNOWN);
            }
        }

        void loadMap(String path) throws IOException {
            List<String> lines = Files.readAllLines(Paths.get(path));
            if (lines.size() < GRID_SIZE) throw new IOException("Mapa ma za mało wierszy");

            for (int y = 0; y < GRID_SIZE; y++) {
                String line = lines.get(y).trim();
                for (int x = 0; x < GRID_SIZE && x < line.length(); x++) {
                    if (line.charAt(x) == '#') {
                        ships[x][y] = true;
                        view[x][y] = CellState.SHIP;
                    } else {
                        view[x][y] = CellState.WATER;
                    }
                }
            }
            detectShips();
        }


        private void detectShips() {
            boolean[][] visited = new boolean[GRID_SIZE][GRID_SIZE];
            for (int x = 0; x < GRID_SIZE; x++) {
                for (int y = 0; y < GRID_SIZE; y++) {
                    if (ships[x][y] && !visited[x][y]) {
                        Set<Point> segment = new HashSet<>();
                        floodFill(x, y, visited, segment);
                        shipSegments.add(segment);
                    }
                }
            }
        }

        private void floodFill(int x, int y, boolean[][] visited, Set<Point> segment) {
            if (x < 0 || x >= GRID_SIZE || y < 0 || y >= GRID_SIZE) return;
            if (visited[x][y] || !ships[x][y]) return;

            visited[x][y] = true;
            segment.add(new Point(x, y));

            floodFill(x + 1, y, visited, segment);
            floodFill(x - 1, y, visited, segment);
            floodFill(x, y + 1, visited, segment);
            floodFill(x, y - 1, visited, segment);
        }


        String receiveShot(Point p) {
            if (!ships[p.x][p.y]) {
                view[p.x][p.y] = CellState.MISS;
                return "pudło";
            }

            view[p.x][p.y] = CellState.HIT_SHIP;


            Set<Point> hitShip = null;
            for (Set<Point> ship : shipSegments) {
                if (ship.contains(p)) {
                    hitShip = ship;
                    break;
                }
            }

            if (hitShip == null) return "trafiony";


            boolean allHit = true;
            for (Point part : hitShip) {
                if (view[part.x][part.y] != CellState.HIT_SHIP) {
                    allHit = false;
                    break;
                }
            }

            if (!allHit) return "trafiony";


            boolean allShipsSunk = true;
            for (Set<Point> ship : shipSegments) {
                for (Point part : ship) {
                    if (view[part.x][part.y] != CellState.HIT_SHIP) {
                        allShipsSunk = false;
                        break;
                    }
                }
            }

            return allShipsSunk ? "ostatni zatopiony" : "trafiony zatopiony";
        }


        void markEnemyResult(Point p, String result) {
            if (result.startsWith("pudło")) {
                view[p.x][p.y] = CellState.MISS;
            } else if (result.startsWith("trafiony")) {
                view[p.x][p.y] = CellState.HIT_SHIP;

                if (result.contains("zatopiony")) {

                    markSunkNeighbors(p);
                }
            }
        }


        private void markSunkNeighbors(Point start) {
            Set<Point> shipParts = new HashSet<>();
            Queue<Point> queue = new LinkedList<>();
            queue.add(start);
            boolean[][] visited = new boolean[GRID_SIZE][GRID_SIZE];
            visited[start.x][start.y] = true;


            while(!queue.isEmpty()) {
                Point curr = queue.poll();
                shipParts.add(curr);

                for(Point n : curr.getNeighbors()) {

                    if (view[n.x][n.y] == CellState.HIT_SHIP && !visited[n.x][n.y]) {

                        if (curr.x == n.x || curr.y == n.y) {
                            visited[n.x][n.y] = true;
                            queue.add(n);
                        }
                    }
                }
            }


            for (Point part : shipParts) {
                for (Point n : part.getNeighbors()) {
                    if (view[n.x][n.y] == CellState.UNKNOWN) {
                        view[n.x][n.y] = CellState.WATER;
                    }
                }
            }
        }
    }


    private static Mode mode;
    private static int port;
    private static String host;
    private static String mapFile;
    private static Board myBoard = new Board();
    private static Board enemyBoard = new Board();


    private static List<Point> availableShots = new ArrayList<>();

    public static void main(String[] args) {
        parseArgs(args);

        try {
            myBoard.loadMap(mapFile);

            for(int x=0; x<GRID_SIZE; x++)
                for(int y=0; y<GRID_SIZE; y++)
                    availableShots.add(new Point(x,y));
            Collections.shuffle(availableShots);

            System.out.println("Moja mapa:");
            printMyMap(myBoard);

            if (mode == Mode.SERVER) {
                runServer();
            } else {
                runClient();
            }
        } catch (Exception e) {
            System.err.println("Błąd krytyczny: " + e.getMessage());
            e.printStackTrace();
        }
    }



    private static void runServer() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Oczekiwanie na połączenie na porcie " + port + "...");
            try (Socket socket = serverSocket.accept()) {
                System.out.println("Połączono z klientem.");
                handleGameLoop(socket, false);
            }
        }
    }

    private static void runClient() throws IOException {
        System.out.println("Łączenie z " + host + ":" + port + "...");
        try (Socket socket = new Socket(host, port)) {
            System.out.println("Połączono z serwerem.");
            handleGameLoop(socket, true);
        }
    }

    private static void handleGameLoop(Socket socket, boolean amIClient) throws IOException {
        socket.setSoTimeout(TIMEOUT_MS);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        String lastMessageSent = null;
        int retries = 0;


        if (amIClient) {

            Point shot = getNextShot();
            lastMessageSent = "start;" + shot;
            System.out.println("Wysyłanie: " + lastMessageSent.trim());
            out.print(lastMessageSent + "\n");
            out.flush();
        }

        while (true) {
            try {
                String line = in.readLine();
                if (line == null) throw new IOException("Druga strona zamknęła połączenie");


                retries = 0;
                System.out.println("Otrzymano: " + line.trim());


                String[] parts = line.trim().split(";");
                String command = parts[0];


                if (command.equals("ostatni zatopiony")) {

                    Point lastShot = Point.parse(lastMessageSent.split(";")[1]);
                    enemyBoard.markEnemyResult(lastShot, "trafiony zatopiony");

                    finishGame(true);
                    return;
                }


                if (parts.length < 2) {
                    throw new IllegalArgumentException("Nieprawidłowy format wiadomości (brak współrzędnych)");
                }

                Point receivedCoords = Point.parse(parts[1]);
                if (receivedCoords == null) {
                    throw new IllegalArgumentException("Nieprawidłowe współrzędne");
                }


                if (!command.equals("start")) {
                    Point lastTarget = Point.parse(lastMessageSent.split(";")[1]);
                    enemyBoard.markEnemyResult(lastTarget, command);
                }


                String myResult = myBoard.receiveShot(receivedCoords);


                if (myResult.equals("ostatni zatopiony")) {
                    System.out.println("Wysyłanie: " + myResult);
                    out.print(myResult + "\n");
                    out.flush();
                    finishGame(false);
                    return;
                }


                Point nextShot = getNextShot();
                lastMessageSent = myResult + ";" + nextShot;
                System.out.println("Wysyłanie: " + lastMessageSent.trim());
                out.print(lastMessageSent + "\n");
                out.flush();

            } catch (SocketTimeoutException | IllegalArgumentException e) {

                retries++;
                System.out.println("Błąd/Timeout (" + retries + "/3). Ponawiam ostatnią wiadomość...");
                if (retries >= MAX_RETRIES) {
                    System.out.println("Błąd komunikacji");
                    return;
                }
                if (lastMessageSent != null) {
                    out.print(lastMessageSent + "\n");
                    out.flush();
                }
            }
        }
    }

    private static Point getNextShot() {
        if (availableShots.isEmpty()) return new Point(0,0);
        return availableShots.remove(0);
    }

    private static void finishGame(boolean won) {
        if (won) {
            System.out.println("Wygrana");
            printEnemyMap(enemyBoard, true);
        } else {
            System.out.println("Przegrana");

            printEnemyMap(enemyBoard, false);
        }

        System.out.println();


        printMyMapResult(myBoard);
    }




    private static void printMyMap(Board b) {
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                if (b.view[x][y] == CellState.SHIP) System.out.print("#");
                else System.out.print(".");
            }
            System.out.println();
        }
    }


    private static void printMyMapResult(Board b) {
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                CellState s = b.view[x][y];
                switch (s) {
                    case MISS: System.out.print("~"); break;
                    case HIT_SHIP: System.out.print("@"); break;
                    case SHIP: System.out.print("#"); break;
                    default: System.out.print("."); break;
                }
            }
            System.out.println();
        }
    }


    private static void printEnemyMap(Board b, boolean fullReveal) {
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                CellState s = b.view[x][y];
                if (s == CellState.HIT_SHIP) {
                    System.out.print("#");
                } else if (s == CellState.MISS || s == CellState.WATER) {
                    System.out.print(".");
                } else {

                    System.out.print(fullReveal ? "." : "?");
                }
            }
            System.out.println();
        }
    }


    private static void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-mode":
                    if (i + 1 < args.length) mode = Mode.valueOf(args[++i].toUpperCase());
                    break;
                case "-port":
                    if (i + 1 < args.length) port = Integer.parseInt(args[++i]);
                    break;
                case "-map":
                    if (i + 1 < args.length) mapFile = args[++i];
                    break;
                case "-host":
                    if (i + 1 < args.length) host = args[++i];
                    break;
            }
        }

        if (mode == null || port == 0 || mapFile == null) {
            System.out.println("Użycie: java Battleships -mode [server|client] -port N -map file [-host hostName]");
            System.exit(1);
        }
        if (mode == Mode.CLIENT && host == null) {
            System.out.println("Tryb client wymaga parametru -host");
            System.exit(1);
        }
    }
}