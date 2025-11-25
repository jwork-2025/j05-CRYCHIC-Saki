package com.gameengine.example;

import com.gameengine.math.Vector2;
import com.gameengine.graphics.IRenderer;

public class Bullet {
    private static int bulletCounter = 0;
    private int bulletId;
    private Vector2 position;
    private Vector2 velocity;
    private float lifetime;
    private float maxLifetime;
    private float radius;
    private IRenderer renderer;
    private boolean alive;
    private float r,g,b,a;
    private int damage;

    public Bullet(Vector2 position, Vector2 velocity, IRenderer renderer, float r, float g, float b, float a, int damage) {
        this.bulletId = bulletCounter++;
        this.position = new Vector2(position);
        this.velocity = new Vector2(velocity);
        this.renderer = renderer;
        this.radius = 3.0f;
        this.lifetime = 0.0f;
        this.maxLifetime = 5.0f;  // 5秒后自动消失
        this.alive = true;
        this.r = r; this.g = g; this.b = b; this.a = a;
        this.damage = damage;
    }

    public void update(float deltaTime) {
        if (!alive) return;

        position = position.add(velocity.multiply(deltaTime));
        lifetime += deltaTime;

        // 检查边界，超出屏幕则消失
        if (renderer != null) {
            if (position.x < -10 || position.x > renderer.getWidth() + 10 ||
                position.y < -10 || position.y > renderer.getHeight() + 10) {
                alive = false;
            }
        }

        // 检查超时
        if (lifetime >= maxLifetime) {
            alive = false;
        }
    }

    public void render() {
        if (!alive || renderer == null) return;
        // 绘制子弹为小球，使用发射者颜色
        renderer.drawCircle(position.x, position.y, radius, 8, r, g, b, a);
    }

    public Vector2 getPosition() {
        return new Vector2(position);
    }

    public Vector2 getVelocity() {
        return new Vector2(velocity);
    }

    public float getRadius() {
        return radius;
    }

    public boolean isAlive() {
        return alive;
    }

    public void kill() {
        alive = false;
    }

    public int getDamage() {
        return damage;
    }

    public float getLifetime() {
        return lifetime;
    }

    public int getBulletId() {
        return bulletId;
    }

    public float getR() { return r; }
    public float getG() { return g; }
    public float getB() { return b; }
    public float getA() { return a; }

    public boolean colorMatches(float rr, float gg, float bb, float tol) {
        return Math.abs(this.r - rr) <= tol && Math.abs(this.g - gg) <= tol && Math.abs(this.b - bb) <= tol;
    }
}
