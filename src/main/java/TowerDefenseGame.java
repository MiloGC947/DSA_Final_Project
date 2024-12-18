import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Supplier;
import java.util.function.Consumer;

public class TowerDefenseGame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(GameFrame::new);
    }
}

class GameFrame extends JFrame {
    public GameFrame() {
        setTitle("Tower Defense Game");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1400, 600);
        setLayout(new BorderLayout());

        GamePanel gamePanel = new GamePanel();
        TowerSelectionPanel selectionPanel = new TowerSelectionPanel(gamePanel);

        add(gamePanel, BorderLayout.CENTER);
        add(selectionPanel, BorderLayout.EAST);

        setVisible(true);
    }
}

class GamePanel extends JPanel {
    private final MapGrid map;
    private final List<Enemy> enemies;
    private final List<Tower> towers;
    private final javax.swing.Timer gameTimer;
    private int playerHealth = 10;
    private static int playerGold = 300;
    private int waveCounter = 0;
    private int enemiesToSpawn = 0;
    private javax.swing.Timer waveSpawner;
    private boolean sellMode = false;
    private final List<Beam> beams = new ArrayList<>();
    private Supplier<Tower> selectedTowerSupplier = () -> new Tower(0, 0);
    private Consumer<Tower> upgradeMode = null;

    private final List<int[]> predefinedWaves;
    private boolean randomWaves = false;

    public GamePanel() {
        map = new MapGrid(15, 20);
        enemies = new ArrayList<>();
        towers = new ArrayList<>();

        predefinedWaves = generatePredefinedWaves();

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

                if (sellMode) {
                    Iterator<Tower> iterator = towers.iterator();
                    while (iterator.hasNext()) {
                        Tower tower = iterator.next();
                        if (tower.getX() == x && tower.getY() == y) {
                            iterator.remove();
                            playerGold += 15;
                            repaint();
                            return;
                        }
                    }
                } else if (upgradeMode != null) {
                    towers.stream()
                            .filter(t -> t.getX() == x && t.getY() == y)
                            .findFirst()
                            .ifPresent(tower -> {
                                upgradeMode.accept(tower);
                                upgradeMode = null;
                            });
                } else {
                    if (selectedTowerSupplier != null && map.isPlacable(x, y) && !isTowerAtPosition(x, y)) {
                        Tower newTower = selectedTowerSupplier.get();
                        newTower.setPosition(x, y);
                        if (playerGold >= newTower.getCost()) {
                            towers.add(newTower);
                            playerGold -= newTower.getCost();
                            repaint();
                        } else {
                            JOptionPane.showMessageDialog(GamePanel.this, "Sell Tower Mode Enabled!");
                        }
                    }
                }
            }
        });
    }

    private void startNextWave() {
        if (waveSpawner != null && waveSpawner.isRunning()) {
            waveSpawner.stop();
        }

        waveCounter++;
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

    private List<int[]> generatePredefinedWaves() {
        List<int[]> waves = new ArrayList<>();

        for (int knights = 0; knights <= 10; knights++) {
            waves.add(new int[]{10 - knights, knights, 0});
        }

        for (int brutes = 1; brutes <= 10; brutes++) {
            waves.add(new int[]{0, 10 - brutes, brutes});
        }

        return waves;
    }

    private List<Enemy> generateWaveEnemies() {
        List<Enemy> waveEnemies = new ArrayList<>();

        if (!randomWaves && waveCounter <= predefinedWaves.size()) {
            int[] waveConfig = predefinedWaves.get(waveCounter - 1);
            int runts = waveConfig[0];
            int knights = waveConfig[1];
            int brutes = waveConfig[2];

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
        } else {
            randomWaves = true;
            Random random = new Random();
            int totalEnemies = 10 + random.nextInt(6);
            Point startPoint = map.getPathPoints().get(0);

            for (int i = 0; i < totalEnemies; i++) {
                int type = random.nextInt(3);
                if (type == 0) {
                    waveEnemies.add(new Runt(startPoint.x, startPoint.y, map));
                } else if (type == 1) {
                    waveEnemies.add(new Knight(startPoint.x, startPoint.y, map));
                } else {
                    waveEnemies.add(new Brute(startPoint.x, startPoint.y, map));
                }
            }
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
            }
            else if (enemy.getHealth() <= 0) {
                GamePanel.incrementGold(enemy.getGoldDrop());
                enemyIterator.remove();
            }
            else {
                enemy.move();
            }
        }

        for (Tower tower : towers) {
            tower.attack(enemies, beams);
        }

        beams.removeIf(beam -> !beam.isActive());

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

    public static int getPlayerGold() {
        return playerGold;
    }

    public void setSellMode(boolean sellMode) {
        this.sellMode = sellMode;
    }

    public void setSelectedTowerSupplier(Supplier<Tower> supplier) {
        this.selectedTowerSupplier = supplier;
    }

    public void setUpgradeMode(Consumer<Tower> mode) {
        this.upgradeMode = mode;
    }

    public void upgradeTower(Tower oldTower, Tower newTower) {
        towers.remove(oldTower);
        newTower.setPosition(oldTower.getX(), oldTower.getY());
        towers.add(newTower);
        repaint();
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        map.draw(g);

        for (Enemy enemy : enemies) {
            enemy.draw(g);
        }

        for (Tower tower : towers) {
            tower.draw(g);
        }

        for (Beam beam : beams) {
            beam.draw(g);
        }

        g.setColor(Color.BLACK);
        g.drawString("Health: " + playerHealth, 10, 10);
        g.drawString("Wave: " + waveCounter, 10, 25);
        g.drawString("Gold: " + playerGold, 10, 40);
    }
}

class TowerSelectionPanel extends JPanel {
    private boolean isSellMode = false;

    public TowerSelectionPanel(GamePanel gamePanel) {
        setPreferredSize(new Dimension(600, 600));
        setBackground(Color.LIGHT_GRAY);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Select Tower");
        title.setFont(new Font("Arial", Font.BOLD, 24));
        title.setAlignmentX(CENTER_ALIGNMENT);

        add(title);
        add(Box.createRigidArea(new Dimension(0, 20)));

        addTowerRow(gamePanel, "Sell Tower", "for 15 Gold", Color.RED, null, e -> {
            isSellMode = true;
            gamePanel.setUpgradeMode(null);
            gamePanel.setSellMode(true);
        });

        addTowerRow(gamePanel, "Normal Tower", "25 Gold", Color.BLUE, () -> new Tower(0, 0), e -> {
            isSellMode = false;
            gamePanel.setUpgradeMode(null);
            gamePanel.setSellMode(false);
            gamePanel.setSelectedTowerSupplier(() -> new Tower(0, 0));
        });

        addTowerRow(gamePanel, "Upgrade to Sniper Tower", "200 Gold", Color.CYAN, null, e -> {
            if (isSellMode) return;
            gamePanel.setUpgradeMode((Tower tower) -> {
                if (tower instanceof SniperTower || tower instanceof AutoTower) {
                    return;
                }
                if (GamePanel.getPlayerGold() >= 200) {
                    gamePanel.upgradeTower(tower, new SniperTower(tower.getX(), tower.getY()));
                    GamePanel.incrementGold(-200);
                } else {
                    JOptionPane.showMessageDialog(this, "Not enough gold!");
                }
            });
        });

        addTowerRow(gamePanel, "Upgrade to Auto Tower", "200 Gold", Color.GRAY, null, e -> {
            if (isSellMode) return;
            gamePanel.setUpgradeMode((Tower tower) -> {
                if (tower instanceof SniperTower || tower instanceof AutoTower) {
                    return;
                }
                if (GamePanel.getPlayerGold() >= 200) {
                    gamePanel.upgradeTower(tower, new AutoTower(tower.getX(), tower.getY()));
                    GamePanel.incrementGold(-200);
                } else {
                    JOptionPane.showMessageDialog(this, "Not enough gold!");
                }
            });
        });

        addTowerRow(gamePanel, "DoT Tower", "50 Gold", Color.MAGENTA, () -> new DoTTower(0, 0), e -> {
            isSellMode = false;
            gamePanel.setUpgradeMode(null);
            gamePanel.setSellMode(false);
            gamePanel.setSelectedTowerSupplier(() -> new DoTTower(0, 0));
        });

        addTowerRow(gamePanel, "Upgrade to DoT Permanent Increase", "300 Gold", Color.ORANGE, null, e -> {
            if (isSellMode) return;
            gamePanel.setUpgradeMode((Tower tower) -> {
                if (tower instanceof PermanentDoTTower || tower instanceof SpreadDoTTower) {
                    return;
                }
                if (GamePanel.getPlayerGold() >= 300) {
                    gamePanel.upgradeTower(tower, new PermanentDoTTower(tower.getX(), tower.getY()));
                    GamePanel.incrementGold(-300);
                } else {
                    JOptionPane.showMessageDialog(this, "Not enough gold!");
                }
            });
        });

        addTowerRow(gamePanel, "Upgrade to DoT Spread", "300 Gold", Color.PINK, null, e -> {
            if (isSellMode) return;
            gamePanel.setUpgradeMode((Tower tower) -> {
                if (tower instanceof PermanentDoTTower || tower instanceof SpreadDoTTower) {
                    return;
                }
                if (GamePanel.getPlayerGold() >= 300) {
                    gamePanel.upgradeTower(tower, new SpreadDoTTower(tower.getX(), tower.getY()));
                    GamePanel.incrementGold(-300);
                } else {
                    JOptionPane.showMessageDialog(this, "Not enough gold!");
                }
            });
        });
    }

    private void addTowerRow(GamePanel gamePanel, String name, String cost, Color previewColor, Supplier<Tower> towerSupplier, ActionListener action) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setPreferredSize(new Dimension(580, 60));
        row.setMaximumSize(new Dimension(580, 60));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBackground(Color.LIGHT_GRAY);

        JPanel preview = new JPanel();
        preview.setPreferredSize(new Dimension(40, 40));
        preview.setMaximumSize(new Dimension(40, 40));
        preview.setBackground(previewColor);

        JLabel towerName = new JLabel(name);
        towerName.setFont(new Font("Arial", Font.PLAIN, 16));
        towerName.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        JLabel towerCost = new JLabel(cost);
        towerCost.setFont(new Font("Arial", Font.PLAIN, 16));
        towerCost.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        JButton selectButton = new JButton("Select");
        selectButton.setFont(new Font("Arial", Font.PLAIN, 14));
        selectButton.setPreferredSize(new Dimension(100, 40));
        selectButton.addActionListener(e -> {
            isSellMode = "Sell Tower".equals(name);
            gamePanel.setSellMode(isSellMode);
            if (!isSellMode && towerSupplier != null) {
                gamePanel.setSelectedTowerSupplier(towerSupplier);
            }
            action.actionPerformed(e);
        });

        row.add(preview);
        row.add(Box.createRigidArea(new Dimension(20, 0)));
        row.add(towerName);
        row.add(Box.createRigidArea(new Dimension(20, 0)));
        row.add(towerCost);
        row.add(Box.createHorizontalGlue());
        row.add(selectButton);

        add(row);
        add(Box.createRigidArea(new Dimension(0, 20)));
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
    private final List<DoT> dotEffects = new ArrayList<>();

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

    public void addDoT(int damage, int duration) {
        dotEffects.add(new DoT(damage, duration));
    }

    public void addPermanentDoT(int initialDamage) {
        dotEffects.add(new PermanentDoT(initialDamage));
    }

    public void move() {
        Iterator<DoT> iterator = dotEffects.iterator();
        while (iterator.hasNext()) {
            DoT dot = iterator.next();
            if (dot.apply(this)) {
                iterator.remove();
            }
        }

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

    private static class DoT {
        protected int damagePerTick;
        protected final long endTime;
        protected long lastTickTime;

        public DoT(int damage, int duration) {
            this.damagePerTick = damage;
            this.endTime = System.currentTimeMillis() + duration;
            this.lastTickTime = System.currentTimeMillis();
        }

        public boolean apply(Enemy enemy) {
            long currentTime = System.currentTimeMillis();
            if (currentTime >= lastTickTime + 1000) {
                enemy.reduceHealth(damagePerTick);
                lastTickTime = currentTime;
            }
            return currentTime >= endTime;
        }
    }

    private static class PermanentDoT extends DoT {
        private int damageIncrease;

        public PermanentDoT(int damage) {
            super(damage, Integer.MAX_VALUE);
            this.damageIncrease = 1;
        }

        @Override
        public boolean apply(Enemy enemy) {
            long currentTime = System.currentTimeMillis();

            if (currentTime >= lastTickTime + 1000) {
                enemy.reduceHealth(damagePerTick + damageIncrease);
                damageIncrease++;
                lastTickTime = currentTime;
            }

            return false;
        }
    }
}


class Runt extends Enemy {
    public Runt(int startX, int startY, MapGrid map) {
        super(startX, startY, map);
        setAttributes(50, 11, 5);
    }
}

class Knight extends Enemy {
    public Knight(int startX, int startY, MapGrid map) {
        super(startX, startY, map);
        setAttributes(200, 6, 10);
    }
}

class Brute extends Enemy {
    public Brute(int startX, int startY, MapGrid map) {
        super(startX, startY, map);
        setAttributes(600, 3, 15);
    }
}

class Beam {
    private final int startX, startY;
    private final int endX, endY;
    private final long endTime;

    public Beam(int startX, int startY, int endX, int endY, long duration) {
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.endTime = System.currentTimeMillis() + duration;
    }

    public boolean isActive() {
        return System.currentTimeMillis() < endTime;
    }

    public void draw(Graphics g) {
        g.setColor(Color.RED);
        g.drawLine(startX, startY, endX, endY);
    }
}

class Tower {
    protected int x, y;
    protected int range;
    protected int damage;
    protected long cooldown;
    protected int cost;
    protected long lastAttackTime = 0;

    public Tower(int x, int y) {
        this.x = x;
        this.y = y;
        this.range = 2;
        this.damage = 10;
        this.cooldown = 500;
        this.cost = 25;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getCost() {
        return cost;
    }

    public void attack(List<Enemy> enemies, List<Beam> beams) {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastAttackTime >= cooldown) {
            Iterator<Enemy> enemyIterator = enemies.iterator();

            while (enemyIterator.hasNext()) {
                Enemy enemy = enemyIterator.next();
                double distance = Math.sqrt(Math.pow(enemy.getX() - x, 2) + Math.pow(enemy.getY() - y, 2));

                if (distance <= range) {
                    enemy.reduceHealth(damage);

                    if (enemy.getHealth() <= 0) {
                        GamePanel.incrementGold(enemy.getGoldDrop());
                        enemyIterator.remove();
                    }

                    lastAttackTime = currentTime;

                    int startX = x * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;
                    int startY = y * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;
                    int endX = enemy.getX() * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;
                    int endY = enemy.getY() * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;

                    beams.add(new Beam(startX, startY, endX, endY, 100));
                    break;
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

class SniperTower extends Tower {
    public SniperTower(int x, int y) {
        super(x, y);
        this.range = 6;
        this.damage = 5000;
        this.cooldown = 8000;
        this.cost = 200;
    }

    @Override
    public void draw(Graphics g) {
        g.setColor(Color.CYAN);
        g.fillRect(x * MapGrid.CELL_SIZE, y * MapGrid.CELL_SIZE, MapGrid.CELL_SIZE, MapGrid.CELL_SIZE);
    }
}

class AutoTower extends Tower {
    public AutoTower(int x, int y) {
        super(x, y);
        this.range = 2;
        this.damage = 10;
        this.cooldown = 50;
        this.cost = 200;
    }

    @Override
    public void draw(Graphics g) {
        g.setColor(Color.GRAY);
        g.fillRect(x * MapGrid.CELL_SIZE, y * MapGrid.CELL_SIZE, MapGrid.CELL_SIZE, MapGrid.CELL_SIZE);
    }
}

class DoTTower extends Tower {
    public DoTTower(int x, int y) {
        super(x, y);
        this.damage = 5;
        this.cooldown = 500;
        this.cost = 50;
    }

    @Override
    public void attack(List<Enemy> enemies, List<Beam> beams) {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastAttackTime >= cooldown) {
            Iterator<Enemy> enemyIterator = enemies.iterator();

            while (enemyIterator.hasNext()) {
                Enemy enemy = enemyIterator.next();
                double distance = Math.sqrt(Math.pow(enemy.getX() - x, 2) + Math.pow(enemy.getY() - y, 2));

                if (distance <= range) {
                    enemy.reduceHealth(damage);

                    enemy.addDoT(damage / 5, 10000);

                    if (enemy.getHealth() <= 0) {
                        GamePanel.incrementGold(enemy.getGoldDrop());
                        enemyIterator.remove();
                    }

                    lastAttackTime = currentTime;

                    int startX = x * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;
                    int startY = y * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;
                    int endX = enemy.getX() * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;
                    int endY = enemy.getY() * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;

                    beams.add(new Beam(startX, startY, endX, endY, 100));
                    break;
                }
            }
        }
    }

    @Override
    public void draw(Graphics g) {
        g.setColor(Color.MAGENTA);
        g.fillRect(x * MapGrid.CELL_SIZE, y * MapGrid.CELL_SIZE, MapGrid.CELL_SIZE, MapGrid.CELL_SIZE);
    }
}

class PermanentDoTTower extends Tower {
    public PermanentDoTTower(int x, int y) {
        super(x, y);
        this.damage = 5;
        this.cooldown = 500;
        this.cost = 300;
    }

    @Override
    public void attack(List<Enemy> enemies, List<Beam> beams) {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastAttackTime >= cooldown) {
            Iterator<Enemy> enemyIterator = enemies.iterator();

            while (enemyIterator.hasNext()) {
                Enemy enemy = enemyIterator.next();
                double distance = Math.sqrt(Math.pow(enemy.getX() - x, 2) + Math.pow(enemy.getY() - y, 2));

                if (distance <= range) {
                    enemy.reduceHealth(damage);

                    enemy.addPermanentDoT(damage / 5);

                    if (enemy.getHealth() <= 0) {
                        GamePanel.incrementGold(enemy.getGoldDrop());
                        enemyIterator.remove();
                    }

                    lastAttackTime = currentTime;

                    int startX = x * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;
                    int startY = y * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;
                    int endX = enemy.getX() * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;
                    int endY = enemy.getY() * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;

                    beams.add(new Beam(startX, startY, endX, endY, 100));
                    break;
                }
            }
        }
    }

    @Override
    public void draw(Graphics g) {
        g.setColor(Color.ORANGE);
        g.fillRect(x * MapGrid.CELL_SIZE, y * MapGrid.CELL_SIZE, MapGrid.CELL_SIZE, MapGrid.CELL_SIZE);
    }
}

class SpreadDoTTower extends Tower {
    public SpreadDoTTower(int x, int y) {
        super(x, y);
        this.damage = 10;
        this.cooldown = 500;
        this.cost = 300;
    }

    @Override
    public void attack(List<Enemy> enemies, List<Beam> beams) {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastAttackTime >= cooldown) {
            Iterator<Enemy> enemyIterator = enemies.iterator();

            while (enemyIterator.hasNext()) {
                Enemy enemy = enemyIterator.next();
                double distance = Math.sqrt(Math.pow(enemy.getX() - x, 2) + Math.pow(enemy.getY() - y, 2));

                if (distance <= range) {
                    enemy.addDoT(10, 10000);

                    for (Enemy e : enemies) {
                        if (e != enemy && e.getY() == enemy.getY()) {
                            e.addDoT(damage / 2, 10000);
                        }
                    }

                    lastAttackTime = currentTime;

                    int startX = x * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;
                    int startY = y * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;
                    int endX = enemy.getX() * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;
                    int endY = enemy.getY() * MapGrid.CELL_SIZE + MapGrid.CELL_SIZE / 2;

                    beams.add(new Beam(startX, startY, endX, endY, 100));
                    break;
                }
            }
        }
    }

    @Override
    public void draw(Graphics g) {
        g.setColor(Color.PINK);
        g.fillRect(x * MapGrid.CELL_SIZE, y * MapGrid.CELL_SIZE, MapGrid.CELL_SIZE, MapGrid.CELL_SIZE);
    }
}



