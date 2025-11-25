package com.gameengine.example;

import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.RenderComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameLogic;
import com.gameengine.core.GameObject;
import com.gameengine.core.ParticleSystem;
import com.gameengine.graphics.IRenderer;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;
import java.util.*;

public class GameScene extends Scene {
    private final GameEngine engine;
    private IRenderer renderer;
    private Random random;
    private float time;
    private GameLogic gameLogic;
    private List<float[]> palette;
    private ParticleSystem playerParticles;
    private List<ParticleSystem> collisionParticles;
    private Map<GameObject, ParticleSystem> aiPlayerParticles;
    private boolean waitingReturn;
    private float waitInputTimer;
    private float freezeTimer;
    private final float inputCooldown = 0.25f;
    private final float freezeDelay = 0.20f;
    private List<Bullet> bullets;
    private float shootCooldown;
    private final float shootInterval = 0.15f;

    public GameScene(GameEngine engine) {
        super("GameScene");
        this.engine = engine;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.renderer = engine.getRenderer();
        this.random = new Random();
        this.time = 0;
        this.gameLogic = new GameLogic(this);
        this.gameLogic.setGameEngine(engine);
        // 统一七色调色板（含 alpha）
        this.palette = List.of(
            new float[]{1.0f, 0.0f, 0.0f, 1.0f}, // 红
            new float[]{1.0f, 0.3f, 0.0f, 1.0f}, // 橙
            new float[]{1.0f, 1.0f, 0.0f, 1.0f}, // 黄
            new float[]{0.0f, 1.0f, 0.0f, 1.0f}, // 绿
            new float[]{0.0f, 0.0f, 1.0f, 1.0f}, // 蓝
            new float[]{0.1f, 0.1f, 0.4f, 1.0f}, // 靛
            new float[]{0.6f, 0.1f, 0.9f, 1.0f}  // 紫
        );
        this.waitingReturn = false;
        this.waitInputTimer = 0f;
        this.freezeTimer = 0f;
        this.bullets = new ArrayList<>();
        this.shootCooldown = 0f;

        createPlayer();
        createAIPlayers();
        createDecorations();

        collisionParticles = new ArrayList<>();
        aiPlayerParticles = new HashMap<>();

        playerParticles = new ParticleSystem(renderer, new Vector2(renderer.getWidth() / 2.0f, renderer.getHeight() / 2.0f));
        playerParticles.setActive(true);
        
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        time += deltaTime;

        gameLogic.handlePlayerInput(deltaTime);
        gameLogic.handleAIPlayerMovement(deltaTime);
        gameLogic.handleAIPlayerAvoidance(deltaTime);

        boolean wasGameOver = gameLogic.isGameOver();
        gameLogic.checkCollisions(deltaTime);

        if (gameLogic.isGameOver() && !wasGameOver) {
            GameObject player = gameLogic.getUserPlayer();
            if (player != null) {
                TransformComponent transform = player.getComponent(TransformComponent.class);
                if (transform != null) {
                    ParticleSystem.Config cfg = new ParticleSystem.Config();
                    cfg.initialCount = 0;
                    cfg.spawnRate = 9999f;
                    cfg.opacityMultiplier = 1.0f;
                    cfg.minRenderSize = 3.0f;
                    cfg.burstSpeedMin = 250f;
                    cfg.burstSpeedMax = 520f;
                    cfg.burstLifeMin = 0.5f;
                    cfg.burstLifeMax = 1.2f;
                    cfg.burstSizeMin = 18f;
                    cfg.burstSizeMax = 42f;
                    cfg.burstR = 1.0f;
                    cfg.burstGMin = 0.0f;
                    cfg.burstGMax = 0.05f;
                    cfg.burstB = 0.0f;
                    ParticleSystem explosion = new ParticleSystem(renderer, transform.getPosition(), cfg);
                    explosion.burst(180);
                    collisionParticles.add(explosion);
                    waitingReturn = true;
                    waitInputTimer = 0f;
                    freezeTimer = 0f;
                }
            }
        }

        updateParticles(deltaTime);

        // 更新和检测子弹碰撞
        updateBullets(deltaTime);
        checkBulletCollisions(deltaTime);

        if (waitingReturn) {
            waitInputTimer += deltaTime;
            freezeTimer += deltaTime;
        }

        if (waitingReturn && waitInputTimer >= inputCooldown && (engine.getInputManager().isAnyKeyJustPressed() || engine.getInputManager().isMouseButtonJustPressed(0))) {
            MenuScene menu = new MenuScene(engine, "MainMenu");
            engine.setScene(menu);
            return;
        }

        if (time >= 1.0f) {
            createAIPlayer();
            time = 0;
        }
    }

    private void updateParticles(float deltaTime) {
        boolean freeze = waitingReturn && freezeTimer >= freezeDelay;

        if (playerParticles != null && !freeze) {
            GameObject player = gameLogic.getUserPlayer();
            if (player != null) {
                TransformComponent transform = player.getComponent(TransformComponent.class);
                if (transform != null) {
                    Vector2 playerPos = transform.getPosition();
                    playerParticles.setPosition(playerPos);
                }
            }
            playerParticles.update(deltaTime);
        }

        List<GameObject> aiPlayers = gameLogic.getAIPlayers();
        if (!freeze) {
            for (GameObject aiPlayer : aiPlayers) {
                if (aiPlayer != null && aiPlayer.isActive()) {
                    ParticleSystem particles = aiPlayerParticles.get(aiPlayer);
                    if (particles == null) {
                        TransformComponent transform = aiPlayer.getComponent(TransformComponent.class);
                        if (transform != null) {
                            particles = new ParticleSystem(renderer, transform.getPosition(), ParticleSystem.Config.light());
                            particles.setActive(true);
                            aiPlayerParticles.put(aiPlayer, particles);
                        }
                    }
                    if (particles != null) {
                        TransformComponent transform = aiPlayer.getComponent(TransformComponent.class);
                        if (transform != null) {
                            particles.setPosition(transform.getPosition());
                        }
                        particles.update(deltaTime);
                    }
                }
            }
        }

        List<GameObject> toRemove = new ArrayList<>();
        for (Map.Entry<GameObject, ParticleSystem> entry : aiPlayerParticles.entrySet()) {
            if (!entry.getKey().isActive() || !aiPlayers.contains(entry.getKey())) {
                toRemove.add(entry.getKey());
            }
        }
        for (GameObject removed : toRemove) {
            aiPlayerParticles.remove(removed);
        }

        for (int i = collisionParticles.size() - 1; i >= 0; i--) {
            ParticleSystem ps = collisionParticles.get(i);
            if (ps != null) {
                if (!freeze) {
                    ps.update(deltaTime);
                }
            }
        }
    }

    @Override
    public void render() {
        // 新 UI 风格：深色背景 + 顶部半透明信息栏
        renderer.drawRect(0, 0, renderer.getWidth(), renderer.getHeight(), 0.04f, 0.04f, 0.06f, 1.0f);
        // 顶部信息栏
        renderer.drawRect(0, 0, renderer.getWidth(), 56, 0.06f, 0.06f, 0.08f, 0.9f);
        // 底部轻微分隔线
        renderer.drawRect(0, 56, renderer.getWidth(), 2, 0.15f, 0.15f, 0.18f, 0.6f);

        super.render();

        renderParticles();

        // 渲染子弹
        for (Bullet bullet : bullets) {
            if (bullet.isAlive()) {
                bullet.render();
            }
        }

        // 渲染敌人头顶血条
        renderEnemyHealthBars();

        // 渲染玩家心形血量（右上角）
        renderPlayerHearts();

        if (gameLogic.isGameOver()) {
            float cx = renderer.getWidth() / 2.0f;
            float cy = renderer.getHeight() / 2.0f;
            renderer.drawRect(0, 0, renderer.getWidth(), renderer.getHeight(), 0.0f, 0.0f, 0.0f, 0.35f);
            renderer.drawRect(cx - 200, cy - 60, 400, 120, 0.0f, 0.0f, 0.0f, 0.7f);
            renderer.drawText(cx - 100, cy - 10, "GAME OVER", 1.0f, 1.0f, 1.0f, 1.0f);
            renderer.drawText(cx - 180, cy + 30, "PRESS ANY KEY TO RETURN", 0.8f, 0.8f, 0.8f, 1.0f);
        }
    }

    private void renderParticles() {
        if (playerParticles != null) {
            int count = playerParticles.getParticleCount();
            if (count > 0) {
                playerParticles.render();
            }
        }

        for (ParticleSystem ps : aiPlayerParticles.values()) {
            if (ps != null && ps.getParticleCount() > 0) {
                ps.render();
            }
        }

        for (ParticleSystem ps : collisionParticles) {
            if (ps != null && ps.getParticleCount() > 0) {
                ps.render();
            }
        }
    }

    private void renderEnemyHealthBars() {
        List<GameObject> aiPlayers = gameLogic.getAIPlayers();
        for (GameObject ai : aiPlayers) {
            if (ai == null || !ai.isActive()) continue;
            TransformComponent t = ai.getComponent(TransformComponent.class);
            PhysicsComponent p = ai.getComponent(PhysicsComponent.class);
            RenderComponent rc = ai.getComponent(RenderComponent.class);
            if (t == null || p == null) continue;

            Vector2 pos = t.getPosition();
            float barWidth = (rc != null && rc.getSize() != null) ? rc.getSize().x : 24.0f;
            float barHeight = 6.0f;
            float x = pos.x - barWidth / 2.0f;
            // 将血条放在敌人顶部正中（距离顶部一定间隙）
            float topOffset = (rc != null && rc.getSize() != null) ? (rc.getSize().y / 2.0f) : 12.0f;
            float y = pos.y - topOffset - barHeight - 6.0f;

            // 背景
            renderer.drawRect(x, y, barWidth, barHeight, 0.15f, 0.15f, 0.15f, 0.9f);
            // 血量填充（绿->红）
            float hpPercent = p.getHealthPercent();
            float fillW = barWidth * hpPercent;
            float rr = 1.0f - hpPercent;
            float gg = hpPercent;
            if (fillW > 0.0f) renderer.drawRect(x, y, fillW, barHeight, rr, gg, 0.0f, 0.95f);
            // 边框
            renderer.drawRect(x, y, barWidth, barHeight, 1.0f, 1.0f, 1.0f, 0.35f);
        }
    }

    private void renderPlayerHearts() {
        GameObject player = gameLogic.getUserPlayer();
        if (player == null) return;
        PhysicsComponent physics = player.getComponent(PhysicsComponent.class);
        if (physics == null) return;

        int hp = Math.max(0, (int)physics.getHealth());
        int maxHp = Math.max(1, (int)physics.getMaxHealth());

        // 在右上角显示心形血量，每个心代表 1 点血
        float heartSize = 18f;
        float gap = 6f;
        float startX = renderer.getWidth() - 20f - (heartSize + gap) * maxHp + gap;
        float y = 14f; // 在顶部信息栏内

        for (int i = 0; i < maxHp; i++) {
            float x = startX + i * (heartSize + gap);
            if (i < hp) {
                // 实心心（红） — 两个圆 + 下方矩形近似
                renderer.drawCircle(x + heartSize*0.25f, y + heartSize*0.35f, heartSize*0.22f, 12, 1.0f, 0.15f, 0.2f, 1.0f);
                renderer.drawCircle(x + heartSize*0.75f, y + heartSize*0.35f, heartSize*0.22f, 12, 1.0f, 0.15f, 0.2f, 1.0f);
                renderer.drawRect(x + heartSize*0.15f, y + heartSize*0.55f, heartSize*0.7f, heartSize*0.38f, 1.0f, 0.15f, 0.2f, 1.0f);
            } else {
                // 空心心（灰色边框）
                renderer.drawCircle(x + heartSize*0.25f, y + heartSize*0.35f, heartSize*0.22f, 12, 0.45f, 0.45f, 0.48f, 0.8f);
                renderer.drawCircle(x + heartSize*0.75f, y + heartSize*0.35f, heartSize*0.22f, 12, 0.45f, 0.45f, 0.48f, 0.8f);
                renderer.drawRect(x + heartSize*0.15f, y + heartSize*0.55f, heartSize*0.7f, heartSize*0.38f, 0.45f, 0.45f, 0.48f, 0.8f);
            }
        }
    }

    private void createPlayer() {
        GameObject player = new GameObject("Player") {
            private Vector2 basePosition;
            
            private com.gameengine.components.RenderComponent rc;

            @Override
            public void update(float deltaTime) {
                super.update(deltaTime);
                updateComponents(deltaTime);
                updateBodyParts();
            }

            @Override
            public void render() {
                renderBodyParts();
            }

            private void updateBodyParts() {
                TransformComponent transform = getComponent(TransformComponent.class);
                if (transform != null) {
                    basePosition = transform.getPosition();
                }
            }

            private void renderBodyParts() {
                if (basePosition == null) return;

                // 颜色不再受隐身影响，始终全不透明
                float alpha = 1.0f;

                // 使用 RenderComponent 的颜色来表示玩家基础配色（方便录制回放）
                com.gameengine.components.RenderComponent.Color color = rc != null ? rc.getColor() : new com.gameengine.components.RenderComponent.Color(1f,0f,0f,1f);
                float r = color.r * alpha;
                float g = color.g * alpha;
                float b = color.b * alpha;

                renderer.drawRect(
                    basePosition.x - 8, basePosition.y - 10, 16, 20,
                    r, g, b, 0.9f * alpha
                );

                renderer.drawRect(
                    basePosition.x - 6, basePosition.y - 22, 12, 12,
                    1.0f, 0.8f, 0.7f, 1.0f  // 肉色
                );

                renderer.drawRect(
                    basePosition.x - 13, basePosition.y - 5, 6, 12,
                    1.0f, 0.8f, 0.7f, 1.0f  // 肉色
                );

                renderer.drawRect(
                    basePosition.x + 7, basePosition.y - 5, 6, 12,
                    1.0f, 0.8f, 0.7f, 1.0f  // 肉色
                );

                renderer.drawRect(
                    basePosition.x - 7, basePosition.y + 10, 6, 10,
                    1.0f, 0.8f, 0.7f, 1.0f  // 肉色
                );  

                renderer.drawRect(
                    basePosition.x, basePosition.y + 10, 6, 10,
                    1.0f, 0.8f, 0.7f, 1.0f  // 肉色
                );

                 renderer.drawCircle(
                    basePosition.x + 3, basePosition.y - 17, 2, 2,
                    0.0f, 0.0f, 0.0f, 1.0f  // 黑色
                );

                renderer.drawCircle(
                    basePosition.x - 3, basePosition.y - 17, 2, 2,
                    0.0f, 0.0f, 0.0f, 1.0f  // 黑色
                );

                 renderer.drawRect(
                    basePosition.x - 3, basePosition.y - 13, 6, 2,
                    0.0f, 0.0f, 0.0f, 1.0f  // 黑色
                );
            }

            
        };

        player.addComponent(new TransformComponent(new Vector2(renderer.getWidth() / 2.0f, renderer.getHeight() / 2.0f)));

        // 添加 RenderComponent 用于记录颜色与尺寸，便于回放还原
        com.gameengine.components.RenderComponent prc = player.addComponent(new com.gameengine.components.RenderComponent(
            com.gameengine.components.RenderComponent.RenderType.RECTANGLE,
            new com.gameengine.math.Vector2(16,20),
            new com.gameengine.components.RenderComponent.Color(1.0f, 0.0f, 0.0f, 1.0f)
        ));
        prc.setRenderer(renderer);
        // 玩家初始颜色设为纯红（不再随机）
        prc.setColor(new com.gameengine.components.RenderComponent.Color(1.0f, 0.0f, 0.0f, 1.0f));

        PhysicsComponent physics = player.addComponent(new PhysicsComponent(1.0f));
        physics.setFriction(0.95f);
        // 将玩家生命值设为 3（最大生命与当前生命）并保持摩擦设置
        physics.setMaxHealth(3f);
        physics.setHealth(3f);

        // 将 RenderComponent 引用保存在匿名类实例中，便于切换颜色
        try {
            java.lang.reflect.Field f = player.getClass().getDeclaredField("rc");
            f.setAccessible(true);
            f.set(player, prc);
        } catch (Exception ignored) {}

        addGameObject(player);
    }

    private void createAIPlayers() {
        for (int i = 0; i < 30; i++) {
            createAIPlayer();
        }
    }

    private void createAIPlayer() {
        GameObject aiPlayer = new GameObject("AIPlayer") {
            @Override
            public void update(float deltaTime) {
                super.update(deltaTime);
                updateComponents(deltaTime);
            }
        };

        // 在屏幕边缘生成
        Vector2 position;
        int w = renderer.getWidth();
        int h = renderer.getHeight();
        int edge = random.nextInt(4); // 0=top,1=bottom,2=left,3=right
        switch (edge) {
            case 0: position = new Vector2(random.nextFloat() * w, 0); break;
            case 1: position = new Vector2(random.nextFloat() * w, h); break;
            case 2: position = new Vector2(0, random.nextFloat() * h); break;
            default: position = new Vector2(w, random.nextFloat() * h); break;
        }

        aiPlayer.addComponent(new TransformComponent(position));

        // 尺寸：25% 概率出现较大尺寸
        boolean large = random.nextFloat() < 0.25f;
        float size = large ? 36.0f : 20.0f;

        // 颜色随机挑选七色（使用场景的 palette）
        float[] col4 = (this.palette != null && !this.palette.isEmpty())
                ? this.palette.get(random.nextInt(this.palette.size()))
                : new float[]{1f, 0f, 0f, 1f};

        RenderComponent rc = aiPlayer.addComponent(new RenderComponent(
            RenderComponent.RenderType.RECTANGLE,
            new Vector2(size, size),
            new RenderComponent.Color(col4[0], col4[1], col4[2], col4[3])
        ));
        rc.setRenderer(renderer);
        rc.setVisible(true);

        float mass = large ? 1.2f : 0.6f;
        PhysicsComponent physics = aiPlayer.addComponent(new PhysicsComponent(mass));
        // 降低摩擦以避免速度迅速衰减导致卡住
        physics.setFriction(0.92f);

        // 设置生命值：小型 = 1， 大型 = 3
        if (large) {
            physics.setMaxHealth(3f);
            physics.setHealth(3f);
        } else {
            physics.setMaxHealth(1f);
            physics.setHealth(1f);
        }

        // 初始速度朝向屏幕中心（或玩家）略微移动
        GameObject player = gameLogic.getUserPlayer();
        Vector2 target = (player != null && player.getComponent(TransformComponent.class) != null)
            ? player.getComponent(TransformComponent.class).getPosition()
            : new Vector2(renderer.getWidth()/2f, renderer.getHeight()/2f);
        Vector2 dir = target.subtract(position);
        if (dir.magnitude() > 0) dir = dir.normalize().multiply(80 + random.nextFloat()*80);
        physics.setVelocity(dir);

        addGameObject(aiPlayer);
    }

    private void createDecorations() {
        for (int i = 0; i < 5; i++) {
            createDecoration();
        }
    }

    private void createDecoration() {
        GameObject decoration = new GameObject("Decoration") {
            @Override
            public void update(float deltaTime) {
                super.update(deltaTime);
                updateComponents(deltaTime);
            }

            @Override
            public void render() {
                renderComponents();
            }
        };

        Vector2 position = new Vector2(
            random.nextFloat() * renderer.getWidth(),
            random.nextFloat() * renderer.getHeight()
        );

        decoration.addComponent(new TransformComponent(position));

        RenderComponent render = decoration.addComponent(new RenderComponent(
            RenderComponent.RenderType.CIRCLE,
            new Vector2(5, 5),
            new RenderComponent.Color(0.5f, 0.5f, 1.0f, 0.8f)
        ));
        render.setRenderer(renderer);

        addGameObject(decoration);
    }

    public List<Bullet> getBullets() {
        return bullets;
    }

    private void updateBullets(float deltaTime) {
        // 处理玩家射击（鼠标左键朝鼠标方向发射）
        shootCooldown -= deltaTime;
        if (shootCooldown <= 0 && engine.getInputManager().isMouseButtonJustPressed(0)) {
            shootCooldown = shootInterval;
            GameObject player = gameLogic.getUserPlayer();
            if (player != null) {
                TransformComponent playerTransform = player.getComponent(TransformComponent.class);
                if (playerTransform != null) {
                    Vector2 playerPos = playerTransform.getPosition();
                    Vector2 mouse = engine.getInputManager().getMousePosition();
                    Vector2 dir = mouse.subtract(playerPos);
                    if (dir.magnitude() <= 0) dir = new Vector2(0, -1);
                    else dir = dir.normalize();
                    Vector2 bulletVel = dir.multiply(600.0f); // 更快的子弹速度
                    // 子弹继承玩家颜色并造成固定伤害（1）
                    com.gameengine.components.RenderComponent prc = player.getComponent(com.gameengine.components.RenderComponent.class);
                    float br = 1f, bg = 1f, bb = 0f, ba = 1f;
                    if (prc != null) {
                        com.gameengine.components.RenderComponent.Color c = prc.getColor();
                        br = c.r; bg = c.g; bb = c.b; ba = c.a;
                    }
                    int damage = 1;
                    Bullet bullet = new Bullet(playerPos, bulletVel, renderer, br, bg, bb, ba, damage);
                    bullets.add(bullet);
                }
            }
        }

        // 更新所有活子弹
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.update(deltaTime);
            if (!b.isAlive()) {
                bullets.remove(i);
            }
        }
    }

    private void checkBulletCollisions(float deltaTime) {
        List<GameObject> aiPlayers = gameLogic.getAIPlayers();

        // 更新每个 AI 的受伤无敌计时器，确保连续多次命中可以计入
        for (GameObject ai : aiPlayers) {
            PhysicsComponent p = ai.getComponent(PhysicsComponent.class);
            if (p != null) {
                p.updateImmunityTimer(deltaTime);
            }
        }

        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet bullet = bullets.get(i);
            if (!bullet.isAlive()) continue;

            Vector2 bulletPos = bullet.getPosition();
            float bulletRadius = bullet.getRadius();

            for (GameObject aiPlayer : aiPlayers) {
                TransformComponent aiTransform = aiPlayer.getComponent(TransformComponent.class);
                PhysicsComponent aiPhysics = aiPlayer.getComponent(PhysicsComponent.class);
                
                if (aiTransform == null || aiPhysics == null) continue;

                Vector2 aiPos = aiTransform.getPosition();
                float distance = bulletPos.distance(aiPos);

                // 使用 RenderComponent 的尺寸确定 AI 命中半径（矩形取较大边的一半作为近似半径）
                float aiRadius = 10.0f;
                RenderComponent aiRCForHit = aiPlayer.getComponent(RenderComponent.class);
                if (aiRCForHit != null && aiRCForHit.getSize() != null) {
                    aiRadius = Math.max(aiRCForHit.getSize().x, aiRCForHit.getSize().y) / 2.0f;
                }
                if (distance < aiRadius + bulletRadius) {
                        // 只有当子弹颜色与敌人颜色相同时才造成伤害并施加冲量
                        RenderComponent aiRC = aiPlayer.getComponent(RenderComponent.class);
                        boolean colorMatch = false;
                        if (aiRC != null) {
                            RenderComponent.Color cc = aiRC.getColor();
                            colorMatch = bullet.colorMatches(cc.r, cc.g, cc.b, 0.06f);
                        }

                        if (colorMatch) {
                            // 子弹击中 AI，对其施加冲量（向后推送）
                            Vector2 normal = aiPos.subtract(bulletPos);
                            if (normal.magnitude() > 0) {
                                normal = normal.normalize();
                            } else {
                                normal = new Vector2(0, -1);
                            }

                            // 计算冲量（子弹速度 + 质量因子）增强冲量使弹开更明显
                            Vector2 bulletVel = bullet.getVelocity();
                            float impactForce = Math.max(150, bulletVel.magnitude() * 1.5f);
                            Vector2 impulse = normal.multiply(impactForce);

                            aiPhysics.applyImpulse(impulse);

                            // 对 AI 造成伤害
                            aiPhysics.takeDamage(bullet.getDamage());
                            // 若死亡则移除并触发粒子特效
                            if (aiPhysics.isDead()) {
                                // 记录（之前回放中会处理），现在恢复为实时行为：移除并播放粒子特效
                                aiPlayer.setActive(false);
                                // 粒子爆炸
                                TransformComponent t = aiPlayer.getComponent(TransformComponent.class);
                                if (t != null) {
                                    ParticleSystem.Config cfg = new ParticleSystem.Config();
                                    cfg.initialCount = 0;
                                    cfg.spawnRate = 9999f;
                                    cfg.opacityMultiplier = 1.0f;
                                    cfg.minRenderSize = 2.0f;
                                    cfg.burstSpeedMin = 60f;
                                    cfg.burstSpeedMax = 220f;
                                    cfg.burstLifeMin = 0.3f;
                                    cfg.burstLifeMax = 0.9f;
                                    cfg.burstSizeMin = 4f;
                                    cfg.burstSizeMax = 12f;
                                    cfg.burstR = 1.0f; cfg.burstGMin = 0.0f; cfg.burstGMax = 0.5f; cfg.burstB = 0.0f;
                                    ParticleSystem explosion = new ParticleSystem(renderer, t.getPosition(), cfg);
                                    explosion.burst(40);
                                    collisionParticles.add(explosion);
                                }
                            }
                        }

                        // 无论命中与否都让子弹消失（但非同色不会改变敌人）
                        bullet.kill();
                        bullets.remove(i);
                        break;
                }
            }
        }
    }

    @Override
    public void clear() {
        if (gameLogic != null) {
            gameLogic.cleanup();
        }
        if (playerParticles != null) {
            playerParticles.clear();
        }
        if (collisionParticles != null) {
            for (ParticleSystem ps : collisionParticles) {
                if (ps != null) ps.clear();
            }
            collisionParticles.clear();
        }
        if (bullets != null) {
            bullets.clear();
        }
        super.clear();
    }
}


