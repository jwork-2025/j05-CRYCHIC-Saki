package com.gameengine.core;

import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GameLogic {
    private Scene scene;
    private InputManager inputManager;
    private Random random;
    private boolean gameOver;
    private GameEngine gameEngine;
    private Map<GameObject, Vector2> aiTargetVelocities;
    private Map<GameObject, Float> aiTargetUpdateTimers;
    private ExecutorService avoidanceExecutor;
    // 参数化常量，便于调试
    private static final float IMPULSE_RESTITUTION = 0.6f;
    private static final float MAX_IMPULSE = 250f;
    private static final float SEPARATION_MULT = 1.2f;
    private static final float COLLISION_COOLDOWN = 0.12f; // seconds
    private static final float UNSTUCK_THRESHOLD = 8f;
    private static final float UNSTUCK_NEAR_SPEED = 40f;
    private static final float UNSTUCK_FAR_MIN_SPEED = 120f;
    private static final float UNSTUCK_FAR_VAR = 40f;
    private static final float UNSTUCK_NEAR_DIST = 12f;
    // 每个 AI 的碰撞冷却计时
    private Map<GameObject, Float> collisionCooldowns;
    
    public GameLogic(Scene scene) {
        this.scene = scene;
        this.inputManager = InputManager.getInstance();
        this.random = new Random();
        this.gameOver = false;
        this.aiTargetVelocities = new HashMap<>();
        this.aiTargetUpdateTimers = new HashMap<>();
        int threadCount = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        this.avoidanceExecutor = Executors.newFixedThreadPool(threadCount);
        this.collisionCooldowns = new HashMap<>();
    }
    
    public void cleanup() {
        if (avoidanceExecutor != null && !avoidanceExecutor.isShutdown()) {
            avoidanceExecutor.shutdown();
            try {
                if (!avoidanceExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    avoidanceExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                avoidanceExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public void setGameEngine(GameEngine engine) {
        this.gameEngine = engine;
    }
    
    public boolean isGameOver() {
        return gameOver;
    }
    
    public GameObject getUserPlayer() {
        for (GameObject obj : scene.getGameObjects()) {
            if (obj.getName().equals("Player") && obj.hasComponent(PhysicsComponent.class)) {
                return obj;
            }
        }
        return null;
    }
    
    public List<GameObject> getAIPlayers() {
        return scene.getGameObjects().stream()
            .filter(obj -> obj.getName().equals("AIPlayer"))
            .filter(obj -> obj.isActive())
            .collect(Collectors.toList());
    }
    
    public void handlePlayerInput(float deltaTime) {
        if (gameOver) return;
        
        GameObject player = getUserPlayer();
        if (player == null) return;
        
        TransformComponent transform = player.getComponent(TransformComponent.class);
        PhysicsComponent physics = player.getComponent(PhysicsComponent.class);
        
        if (transform == null || physics == null) return;
        
        // 隐身功能已移除（原按键 1 激活）

        // 按空格切换玩家颜色（7色轮换）
        if (inputManager.isKeyJustPressed(32)) { // Space
            com.gameengine.components.RenderComponent rc = player.getComponent(com.gameengine.components.RenderComponent.class);
            if (rc != null) {
                com.gameengine.components.RenderComponent.Color col = rc.getColor();
                float[][] palette = new float[][]{
                    {1.0f,0.0f,0.0f}, {1.0f,0.3f,0.0f}, {1.0f,1.0f,0.0f}, {0.0f,1.0f,0.0f}, {0.0f,0.0f,1.0f}, {0.1f,0.1f,0.4f}, {0.6f,0.1f,0.9f}
                };
                // 找到当前色索引，若匹配则下一个，否则设为第一个
                int idx = 0;
                for (int i = 0; i < palette.length; i++) {
                    float[] p = palette[i];
                    if (Math.abs(col.r - p[0]) < 0.01f && Math.abs(col.g - p[1]) < 0.01f && Math.abs(col.b - p[2]) < 0.01f) { idx = i; break; }
                }
                int next = (idx + 1) % palette.length;
                col.r = palette[next][0]; col.g = palette[next][1]; col.b = palette[next][2];
            }
        }
        
        Vector2 movement = new Vector2();
        
        // W / UpArrow (AWT=38, GLFW=265)
        if (inputManager.isKeyPressed(87) || inputManager.isKeyPressed(38) || inputManager.isKeyPressed(265)) {
            movement.y -= 1;
        }
        // S / DownArrow (AWT=40, GLFW=264)
        if (inputManager.isKeyPressed(83) || inputManager.isKeyPressed(40) || inputManager.isKeyPressed(264)) {
            movement.y += 1;
        }
        // A / LeftArrow (AWT=37, GLFW=263)
        if (inputManager.isKeyPressed(65) || inputManager.isKeyPressed(37) || inputManager.isKeyPressed(263)) {
            movement.x -= 1;
        }
        // D / RightArrow (AWT=39, GLFW=262)
        if (inputManager.isKeyPressed(68) || inputManager.isKeyPressed(39) || inputManager.isKeyPressed(262)) {
            movement.x += 1;
        }
        
        if (movement.magnitude() > 0) {
            movement = movement.normalize().multiply(200);
            physics.setVelocity(movement);
        }
        
        Vector2 pos = transform.getPosition();
        int screenW = gameEngine != null && gameEngine.getRenderer() != null ? gameEngine.getRenderer().getWidth() : 1920;
        int screenH = gameEngine != null && gameEngine.getRenderer() != null ? gameEngine.getRenderer().getHeight() : 1080;
        if (pos.x < 0) pos.x = 0;
        if (pos.y < 0) pos.y = 0;
        if (pos.x > screenW - 20) pos.x = screenW - 20;
        if (pos.y > screenH - 20) pos.y = screenH - 20;
        transform.setPosition(pos);
    }
    
    public void handleAIPlayerMovement(float deltaTime) {
        if (gameOver) return;
        
        List<GameObject> aiPlayers = getAIPlayers();
        for (GameObject aiPlayer : aiPlayers) {
            PhysicsComponent physics = aiPlayer.getComponent(PhysicsComponent.class);
            TransformComponent aiTransform = aiPlayer.getComponent(TransformComponent.class);
            if (physics == null || aiTransform == null) continue;

            // 更新冲量计时器
            physics.updateImpulseTimer(deltaTime);

            // 如果最近受到冲量（被弹开），优先处理弹开效果
            if (physics.hasRecentImpulse()) {
                Vector2 impulse = physics.getLastImpulse();
                if (impulse.magnitude() > 0) {
                    Vector2 reverseDirection = impulse.normalize().multiply(-150);
                    physics.setVelocity(reverseDirection);
                }
                continue;
            }

            // 朝玩家移动
            GameObject player = getUserPlayer();
            Vector2 playerPos = (player != null && player.getComponent(TransformComponent.class) != null)
                ? player.getComponent(TransformComponent.class).getPosition()
                : new Vector2(0,0);

            Vector2 dir = playerPos.subtract(aiTransform.getPosition());
            if (dir.magnitude() > 0.1f) {
                dir = dir.normalize();
                float baseSpeed = 100f;
                Vector2 desired = dir.multiply(baseSpeed);

                Vector2 current = physics.getVelocity();
                float lerp = 0.08f;
                Vector2 newVel = new Vector2(
                    current.x + (desired.x - current.x) * lerp,
                    current.y + (desired.y - current.y) * lerp
                );
                float maxSpeed = 180f;
                if (newVel.magnitude() > maxSpeed) newVel = newVel.normalize().multiply(maxSpeed);
                // 如果 AI 速度非常小（可能被卡住），强制一次朝玩家方向的解卡速度
                if (newVel.magnitude() < UNSTUCK_THRESHOLD) {
                    // 只有在距离玩家较远时才强制解卡，避免近距离穿过玩家
                    if (playerPos.distance(aiTransform.getPosition()) > UNSTUCK_NEAR_DIST) {
                        newVel = dir.multiply(UNSTUCK_FAR_MIN_SPEED + (random.nextFloat() * UNSTUCK_FAR_VAR));
                    } else {
                        // 近距离则给一点小速度避免粘连
                        newVel = dir.multiply(UNSTUCK_NEAR_SPEED);
                    }
                }
                physics.setVelocity(newVel);
            }
        }
    }
    
    
    public void handleAIPlayerAvoidance(float deltaTime) {
        if (gameOver) return;
        
        List<GameObject> aiPlayers = getAIPlayers();
        if (aiPlayers.isEmpty()) return;
        
        if (aiPlayers.size() < 10) {
            handleAIPlayerAvoidanceSerial(aiPlayers, deltaTime);
        } else {
            handleAIPlayerAvoidanceParallel(aiPlayers, deltaTime);
        }
    }
    
    private void handleAIPlayerAvoidanceSerial(List<GameObject> aiPlayers, float deltaTime) {
        for (int i = 0; i < aiPlayers.size(); i++) {
            processAvoidanceForPlayer(aiPlayers, i, deltaTime);
        }
    }
    
    private void handleAIPlayerAvoidanceParallel(List<GameObject> aiPlayers, float deltaTime) {
        int threadCount = Runtime.getRuntime().availableProcessors() - 1;
        threadCount = Math.max(2, threadCount);
        int batchSize = Math.max(1, aiPlayers.size() / threadCount + 1);
        
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < aiPlayers.size(); i += batchSize) {
            final int start = i;
            final int end = Math.min(i + batchSize, aiPlayers.size());
            
            Future<?> future = avoidanceExecutor.submit(() -> {
                for (int j = start; j < end; j++) {
                    processAvoidanceForPlayer(aiPlayers, j, deltaTime);
                }
            });
            
            futures.add(future);
        }
        
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private void processAvoidanceForPlayer(List<GameObject> aiPlayers, int index, float deltaTime) {
        GameObject aiPlayer1 = aiPlayers.get(index);
        TransformComponent transform1 = aiPlayer1.getComponent(TransformComponent.class);
        PhysicsComponent physics1 = aiPlayer1.getComponent(PhysicsComponent.class);
        
        if (transform1 == null || physics1 == null) return;
        
        Vector2 pos1 = transform1.getPosition();
        Vector2 avoidance = new Vector2();
        
        for (int j = index + 1; j < aiPlayers.size(); j++) {
            GameObject aiPlayer2 = aiPlayers.get(j);
            TransformComponent transform2 = aiPlayer2.getComponent(TransformComponent.class);
            
            if (transform2 == null) continue;
            
            Vector2 pos2 = transform2.getPosition();
            float distance = pos1.distance(pos2);
            
            if (distance < 80 && distance > 0) {
                Vector2 direction = pos1.subtract(pos2).normalize();
                float strength = (80 - distance) / 80.0f;
                avoidance = avoidance.add(direction.multiply(strength * 50));
            }
        }
        
        if (avoidance.magnitude() > 0) {
            Vector2 currentVelocity = physics1.getVelocity();
            float lerpFactor = 0.15f;
            Vector2 avoidanceDirection = avoidance.normalize();
            float avoidanceStrength = Math.min(avoidance.magnitude(), 50f);
            
            Vector2 targetVelocity = currentVelocity.add(
                avoidanceDirection.multiply(avoidanceStrength * deltaTime * 10)
            );
            
            Vector2 newVelocity = new Vector2(
                currentVelocity.x + (targetVelocity.x - currentVelocity.x) * lerpFactor,
                currentVelocity.y + (targetVelocity.y - currentVelocity.y) * lerpFactor
            );
            
            float maxSpeed = 150f;
            if (newVelocity.magnitude() > maxSpeed) {
                newVelocity = newVelocity.normalize().multiply(maxSpeed);
            }
            
            physics1.setVelocity(newVelocity);
        }
    }
    
    public void checkCollisions(float deltaTime) {
        GameObject userPlayer = getUserPlayer();
        if (userPlayer == null) return;
        
        PhysicsComponent playerPhysics = userPlayer.getComponent(PhysicsComponent.class);
        TransformComponent playerTransform = userPlayer.getComponent(TransformComponent.class);
        if (playerTransform == null) return;
        
        Vector2 playerPos = playerTransform.getPosition();
        
        // 更新玩家无敌计时器
        if (playerPhysics != null) {
            playerPhysics.updateImmunityTimer(deltaTime);
        }
        
        List<GameObject> aiPlayers = getAIPlayers();
        // 更新碰撞冷却计时器
        java.util.List<GameObject> cooldownToRemove = new java.util.ArrayList<>();
        for (java.util.Map.Entry<GameObject, Float> e : collisionCooldowns.entrySet()) {
            float v = e.getValue() - deltaTime;
            if (v <= 0) cooldownToRemove.add(e.getKey());
            else collisionCooldowns.put(e.getKey(), v);
        }
        for (GameObject k : cooldownToRemove) collisionCooldowns.remove(k);
        for (GameObject aiPlayer : aiPlayers) {
            TransformComponent aiTransform = aiPlayer.getComponent(TransformComponent.class);
            if (aiTransform != null) {
                float distance = playerPos.distance(aiTransform.getPosition());
                if (distance < 30) {
                    if (playerPhysics != null) {
                        // 每次与敌人接触扣 1 点血（与心数对应）
                        playerPhysics.takeDamage(1f);
                        if (playerPhysics.isDead()) {
                            gameOver = true;
                            return;
                        }
                    }
                }
            }
        }
        
        // AI vs AI 碰撞处理：弹开（简单弹性冲量），并稍微分离重叠
        int n = aiPlayers.size();
        for (int i = 0; i < n; i++) {
            GameObject a = aiPlayers.get(i);
            TransformComponent ta = a.getComponent(TransformComponent.class);
            PhysicsComponent pa = a.getComponent(PhysicsComponent.class);
            if (ta == null || pa == null) continue;
            for (int j = i + 1; j < n; j++) {
                GameObject b = aiPlayers.get(j);
                TransformComponent tb = b.getComponent(TransformComponent.class);
                PhysicsComponent pb = b.getComponent(PhysicsComponent.class);
                if (tb == null || pb == null) continue;

                Vector2 posA = ta.getPosition();
                Vector2 posB = tb.getPosition();
                float dist = posA.distance(posB);
                float minDist = 26.0f; // 判定半径调大，便于更早触发碰撞

                if (dist > 0 && dist < minDist) {
                    // 法线（从 B 到 A）
                    Vector2 normal = posA.subtract(posB).normalize();

                    // 相对速度
                    Vector2 va = pa.getVelocity();
                    Vector2 vb = pb.getVelocity();
                    Vector2 relVel = va.subtract(vb);

                    float relAlongNormal = relVel.dot(normal);
                    if (relAlongNormal < 0) {
                        // 检查碰撞冷却：若任意一方在冷却中，则跳过施加冲量（仍会做位置分离）
                        float cdA = collisionCooldowns.getOrDefault(a, 0f);
                        float cdB = collisionCooldowns.getOrDefault(b, 0f);
                        if (cdA <= 0f && cdB <= 0f) {
                            // 两物体正相互接近，计算冲量（适度，避免过强弹开导致抖动）
                            float ma = pa.getMass();
                            float mb = pb.getMass();
                            float impulseMag = -(1 + IMPULSE_RESTITUTION) * relAlongNormal / (1.0f / ma + 1.0f / mb);
                            if (impulseMag > MAX_IMPULSE) impulseMag = MAX_IMPULSE;
                            Vector2 impulse = normal.multiply(impulseMag);
                            pa.applyImpulse(impulse);
                            pb.applyImpulse(impulse.multiply(-1));
                            // 设置冷却，避免短时间内重复施加强力
                            collisionCooldowns.put(a, COLLISION_COOLDOWN);
                            collisionCooldowns.put(b, COLLISION_COOLDOWN);
                        }
                    }

                    // 更积极的位置分离，避免粘连并让弹开可见
                    float overlap = minDist - dist;
                    if (overlap > 0) {
                        float totalMass = pa.getMass() + pb.getMass();
                        float moveA = overlap * (pb.getMass() / totalMass) * SEPARATION_MULT;
                        float moveB = overlap * (pa.getMass() / totalMass) * SEPARATION_MULT;
                        ta.setPosition(posA.add(normal.multiply(moveA)));
                        tb.setPosition(posB.subtract(normal.multiply(moveB)));
                        // 碰撞后轻微将速度朝玩家方向修正，帮助实体恢复朝向并减少抖动
                        Vector2 vaAfter = pa.getVelocity();
                        Vector2 desiredA = playerPos.subtract(posA);
                        if (desiredA.magnitude() > 0) desiredA = desiredA.normalize().multiply(100f);
                        Vector2 newVa = new Vector2(
                            vaAfter.x + (desiredA.x - vaAfter.x) * 0.28f,
                            vaAfter.y + (desiredA.y - vaAfter.y) * 0.28f
                        );
                        pa.setVelocity(newVa);

                        Vector2 vbAfter = pb.getVelocity();
                        Vector2 desiredB = playerPos.subtract(posB);
                        if (desiredB.magnitude() > 0) desiredB = desiredB.normalize().multiply(100f);
                        Vector2 newVb = new Vector2(
                            vbAfter.x + (desiredB.x - vbAfter.x) * 0.28f,
                            vbAfter.y + (desiredB.y - vbAfter.y) * 0.28f
                        );
                        pb.setVelocity(newVb);
                    }
                }
            }
        }
    }
}
