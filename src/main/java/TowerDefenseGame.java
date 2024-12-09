import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.PriorityQueue;

public class TowerDefenseGame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(GameFrame::new);
    }
}

class GameFrame extends JFrame {
    public GameFrame() {
        setTitle("Tower Defense Game");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 600);
        setLayout(new BorderLayout());

        GamePanel gamePanel = new GamePanel();
        add(gamePanel, BorderLayout.CENTER);

        setVisible(true);
    }
}

class GamePanel extends JPanel {
    private final MapGrid map;
    private final List<Enemy> enemies;
    private final List<Tower> towers;
    private final javax.swing.Timer gameTimer;
    private int playerHealth = 10;
    private static int playerGold = 50;
    private int waveCounter = 0;
    private int enemiesToSpawn = 0;
    private javax.swing.Timer waveSpawner;

    public GamePanel() {
        map = new MapGrid(15, 20);
        enemies = new ArrayList<>();
        towers = new ArrayList<>();

        setPreferredSize(new Dimension(800, 600));
        setBackground(Color.GRAY);

        gameTimer = new javax.swing.Timer(16, e -> updateGame());
        gameTimer.start();

        new javax.swing.Timer(1000, e -> playerGold += 5).start();

        startNextWave();

        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int x = e.getX() / MapGrid.CELL_SIZE;
                int y = e.getY() / MapGrid.CELL_SIZE;
                if (playerGold >= 25 && map.isPlacable(x, y) && !isTowerAtPosition(x, y)) {
                    towers.add(new Tower(x, y));
                    playerGold -= 25;
                }
            }
        });
    }

    private void startNextWave() {
        if (waveSpawner != null && waveSpawner.isRunning()) {
            waveSpawner.stop();
        }

        waveCounter++;
        enemiesToSpawn = 10;
        List<Enemy> waveEnemies = generateWaveEnemies();

        waveSpawner = new javax.swing.Timer(1000, new ActionListener() {
            private final Iterator<Enemy> enemyIterator = waveEnemies.iterator();

            @Override
            public void actionPerformed(ActionEvent e) {
                if (enemyIterator.hasNext()) {
                    enemies.add(enemyIterator.next());
                } else {
                    waveSpawner.stop();
                    enemiesToSpawn = 0;
                }
            }
        });

        waveSpawner.start();
    }

    private List<Enemy> generateWaveEnemies() {
        List<Enemy> waveEnemies = new ArrayList<>();
        int runts = new Random().nextInt(4) + 1;
        int knights = new Random().nextInt(4) + 1;
        int brutes = 10 - runts - knights;

        Point startPoint = map.getPathPoints().get(0);

        for (int i = 0; i < runts; i++) {
            waveEnemies.add(new Runt(startPoint.x, startPoint.y, map));
        }
        for (int i = 0; i < knights; i++) {
            waveEnemies.add(new Knight(startPoint.x, startPoint.y, map));
        }
        for (int i = 0; i < brutes; i++) {
            waveEnemies.add(new Brute(startPoint.x, startPoint.y, map));
        }

        return waveEnemies;
    }

    private void updateGame() {
        Iterator<Enemy> enemyIterator = enemies.iterator();
        while (enemyIterator.hasNext()) {
            Enemy enemy = enemyIterator.next();
            if (enemy.isAtEnd()) {
                playerHealth--;
                enemyIterator.remove();
                if (playerHealth <= 0) {
                    gameTimer.stop();
                    JOptionPane.showMessageDialog(this, "Game Over! You lost all health.");
                    System.exit(0);
                }
            } else {
                enemy.move();
            }
        }

        for (Tower tower : towers) {
            tower.attack(enemies);
        }

        if (enemies.isEmpty() && enemiesToSpawn == 0 && (waveSpawner == null || !waveSpawner.isRunning())) {
            startNextWave();
        }

        repaint();
    }

    private boolean isTowerAtPosition(int x, int y) {
        for (Tower tower : towers) {
            if (tower.getX() == x && tower.getY() == y) {
                return true;
            }
        }
        return false;
    }

    public static void incrementGold(int amount) {
        playerGold += amount;
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        map.draw(g);
        for (Enemy enemy : enemies) enemy.draw(g);
        for (Tower tower : towers) tower.draw(g);

        g.setColor(Color.BLACK);
        g.drawString("Health: " + playerHealth, 10, 10);
        g.drawString("Wave: " + waveCounter, 10, 25);
        g.drawString("Gold: " + playerGold, 10, 40);
    }
}

class MapGrid {
    public static final int CELL_SIZE = 40;
    private final int rows, cols;
    private final int[][] grid;
    private final List<Point> pathPoints;

    public MapGrid(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.grid = new int[rows][cols];
        this.pathPoints = new ArrayList<>();
        setupZigZagPath();
    }

    private void setupZigZagPath() {
        boolean movingRight = true;
        int y = 0;

        while (y < rows - 1) {
            if (movingRight) {
                for (int x = 0; x < cols; x++) {
                    grid[y][x] = 1;
                    pathPoints.add(new Point(x, y));
                }
            } else {
                for (int x = cols - 1; x >= 0; x--) {
                    grid[y][x] = 1;
                    pathPoints.add(new Point(x, y));
                }
            }

            movingRight = !movingRight;

            if (y + 1 < rows) {
                int x = movingRight ? 0 : cols - 1;
                grid[y + 1][x] = 1;
                pathPoints.add(new Point(x, y + 1));
            }

            y += 2;
        }
    }

    public boolean isPlacable(int x, int y) {
        return grid[y][x] == 0;
    }

    public List<Point> getPathPoints() {
        return pathPoints;
    }

    public void draw(Graphics g) {
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (grid[y][x] == 1) g.setColor(Color.YELLOW);
                else g.setColor(Color.GREEN);
                g.fillRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }
        }
    }
}


class Enemy {
    private double x, y;
    private int health, goldDrop;
    private float speed;
    private final List<Point> pathPoints;
    private int currentPathIndex;
    private double progressToNextPoint;

    public Enemy(int startX, int startY, MapGrid map) {
        this.x = startX;
        this.y = startY;
        this.pathPoints = map.getPathPoints();
        this.currentPathIndex = 0;
        this.progressToNextPoint = 0;
    }

    public void setAttributes(int health, float speed, int goldDrop) {
        this.health = health;
        this.speed = speed;
        this.goldDrop = goldDrop;
    }

    public void move() {
        if (currentPathIndex >= pathPoints.size() - 1) {
            return;
        }

        Point currentPoint = pathPoints.get(currentPathIndex);
        Point nextPoint = pathPoints.get(currentPathIndex + 1);

        double targetX = nextPoint.x;
        double targetY = nextPoint.y;

        progressToNextPoint += speed / 100.0;

        x = currentPoint.x + (targetX - currentPoint.x) * progressToNextPoint;
        y = currentPoint.y + (targetY - currentPoint.y) * progressToNextPoint;

        if (progressToNextPoint >= 1.0) {
            currentPathIndex++;
            progressToNextPoint = 0;
        }
    }

    public boolean isAtEnd() {
        return currentPathIndex >= pathPoints.size() - 1;
    }

    public void draw(Graphics g) {
        g.setColor(Color.RED);
        g.fillOval((int) (x * MapGrid.CELL_SIZE), (int) (y * MapGrid.CELL_SIZE), MapGrid.CELL_SIZE, MapGrid.CELL_SIZE);

        g.setColor(Color.BLACK);
        FontMetrics fm = g.getFontMetrics();
        String healthText = String.valueOf(health);
        int textWidth = fm.stringWidth(healthText);
        int textHeight = fm.getAscent();
        int centerX = (int) (x * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2 - textWidth / 2);
        int centerY = (int) (y * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2 + textHeight / 2);
        g.drawString(healthText, centerX, centerY);
    }

    public int getX() {
        return (int) x;
    }

    public int getY() {
        return (int) y;
    }

    public int getHealth() {
        return health;
    }

    public int getGoldDrop() {
        return goldDrop;
    }

    public void reduceHealth(int amount) {
        this.health -= amount;
    }
}


class Runt extends Enemy {
    public Runt(int startX, int startY, MapGrid map) {
        super(startX, startY, map);
        setAttributes(100, 11, 1);
    }
}

class Knight extends Enemy {
    public Knight(int startX, int startY, MapGrid map) {
        super(startX, startY, map);
        setAttributes(200, 7, 5);
    }
}

class Brute extends Enemy {
    public Brute(int startX, int startY, MapGrid map) {
        super(startX, startY, map);
        setAttributes(500, 4, 10);
    }
}

class Tower {
    private final int x, y;
    private final int range = 2;

    public Tower(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void attack(List<Enemy> enemies) {
        Iterator<Enemy> enemyIterator = enemies.iterator();
        while (enemyIterator.hasNext()) {
            Enemy enemy = enemyIterator.next();
            double distance = Math.sqrt(Math.pow(enemy.getX() - x, 2) + Math.pow(enemy.getY() - y, 2));
            if (distance <= range) {
                enemy.reduceHealth(1);
                if (enemy.getHealth() <= 0) {
                    GamePanel.incrementGold(10);
                    enemyIterator.remove();
                }
            }
        }
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void draw(Graphics g) {
        g.setColor(Color.BLUE);
        g.fillRect(x * MapGrid.CELL_SIZE, y * MapGrid.CELL_SIZE, MapGrid.CELL_SIZE, MapGrid.CELL_SIZE);
    }
}
