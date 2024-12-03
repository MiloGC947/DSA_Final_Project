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
    private int waveCounter = 0;

    public GamePanel() {
        map = new MapGrid(15, 20);
        enemies = new ArrayList<>();
        towers = new ArrayList<>();

        setPreferredSize(new Dimension(800, 600));
        setBackground(Color.GRAY);

        gameTimer = new javax.swing.Timer(100, e -> updateGame());
        gameTimer.start();

        new javax.swing.Timer(5000, e -> spawnWave()).start();

        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int x = e.getX() / MapGrid.CELL_SIZE;
                int y = e.getY() / MapGrid.CELL_SIZE;
                if (map.isPlacable(x, y)) {
                    towers.add(new Tower(x, y));
                }
            }
        });
    }

    private void spawnWave() {
        waveCounter++;
        for (int i = 0; i < waveCounter; i++) {
            Point startPoint = map.getPathPoints().get(0);
            enemies.add(new Enemy(startPoint.x, startPoint.y, map));
        }
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

        repaint();
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        map.draw(g);
        for (Enemy enemy : enemies) enemy.draw(g);
        for (Tower tower : towers) tower.draw(g);

        g.setColor(Color.BLACK);
        g.drawString("Health: " + playerHealth, 10, 10);
        g.drawString("Wave: " + waveCounter, 10, 25);
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

        for (int x = 0; x < cols; x++) {
            if (x % 2 == 0) {
                for (int i = 0; i < cols; i++) {
                    int column = movingRight ? i : cols - 1 - i;
                    grid[y][column] = 1;
                    pathPoints.add(new Point(column, y));
                }
                movingRight = !movingRight;
                if (y + 2 < rows) {
                    y += 2;
                }
            }
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
    private int x, y, health;
    private final List<Point> pathPoints;
    private int currentPathIndex;
    private int speed;
    private int moveCounter;

    public Enemy(int startX, int startY, MapGrid map) {
        this.x = startX;
        this.y = startY;
        this.health = 100;
        this.pathPoints = map.getPathPoints();
        this.currentPathIndex = 0;
        this.speed = 5;
        this.moveCounter = 0;
    }

    public void move() {
        moveCounter++;
        if (moveCounter >= speed && currentPathIndex < pathPoints.size()) {
            moveCounter = 0;
            Point next = pathPoints.get(currentPathIndex);
            x = next.x;
            y = next.y;
            currentPathIndex++;
        }
    }

    public boolean isAtEnd() {
        return currentPathIndex >= pathPoints.size();
    }

    public void draw(Graphics g) {
        g.setColor(Color.RED);
        g.fillRect(x * MapGrid.CELL_SIZE, y * MapGrid.CELL_SIZE, MapGrid.CELL_SIZE, MapGrid.CELL_SIZE);

        g.setColor(Color.BLACK);
        g.drawString("HP: " + health, x * MapGrid.CELL_SIZE + 10, y * MapGrid.CELL_SIZE - 5);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getHealth() {
        return health;
    }

    public void reduceHealth(int amount) {
        this.health -= amount;
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
                    enemyIterator.remove();
                }
            }
        }
    }

    public void draw(Graphics g) {
        g.setColor(Color.BLUE);
        g.fillRect(x * MapGrid.CELL_SIZE, y * MapGrid.CELL_SIZE, MapGrid.CELL_SIZE, MapGrid.CELL_SIZE);
    }
}
