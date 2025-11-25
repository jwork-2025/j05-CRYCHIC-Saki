package com.gameengine.components;

import com.gameengine.core.Component;
import com.gameengine.math.Vector2;

public class PhysicsComponent extends Component<PhysicsComponent> {
    private Vector2 velocity;
    private Vector2 acceleration;
    private float mass;
    private float friction;
    private boolean useGravity;
    private Vector2 gravity;
    private Vector2 lastImpulse;
    private float impulseTime;
    private float health;
    private float maxHealth;
    private float damageImmunityTimer;
    
    
    public PhysicsComponent() {
        this.velocity = new Vector2();
        this.acceleration = new Vector2();
        this.mass = 1.0f;
        this.friction = 0.9f;
        this.useGravity = false;
        this.gravity = new Vector2(0, 9.8f);
        this.lastImpulse = new Vector2();
        this.impulseTime = 0f;
        this.health = 100f;
        this.maxHealth = 100f;
        this.damageImmunityTimer = 0f;
        
    }
    
    public PhysicsComponent(float mass) {
        this();
        this.mass = mass;
    }
    
    @Override
    public void initialize() {
    }
    
    @Override
    public void render() {
    }
    
    public void applyForce(Vector2 force) {
        if (mass > 0) {
            acceleration = acceleration.add(force.multiply(1.0f / mass));
        }
    }
    
    public void applyImpulse(Vector2 impulse) {
        if (mass > 0) {
            velocity = velocity.add(impulse.multiply(1.0f / mass));
            this.lastImpulse = new Vector2(impulse);
            this.impulseTime = 0.1f;  // 记录冲量持续0.1秒
        }
    }
    
    public void setVelocity(Vector2 velocity) {
        this.velocity = new Vector2(velocity);
    }
    
    public void setVelocity(float x, float y) {
        this.velocity = new Vector2(x, y);
    }
    
    public void setAcceleration(Vector2 acceleration) {
        this.acceleration = new Vector2(acceleration);
    }
    
    public void addVelocity(Vector2 delta) {
        this.velocity = velocity.add(delta);
    }
    
    public void setGravity(Vector2 gravity) {
        this.gravity = new Vector2(gravity);
    }
    
    public void setUseGravity(boolean useGravity) {
        this.useGravity = useGravity;
    }
    
    public void setFriction(float friction) {
        this.friction = Math.max(0, Math.min(1, friction));
    }
    
    public void setMass(float mass) {
        this.mass = Math.max(0.1f, mass);
    }
    
    public Vector2 getVelocity() {
        return new Vector2(velocity);
    }
    
    public Vector2 getAcceleration() {
        return new Vector2(acceleration);
    }
    
    public float getMass() {
        return mass;
    }
    
    public float getFriction() {
        return friction;
    }
    
    public boolean isUseGravity() {
        return useGravity;
    }
    
    public Vector2 getGravity() {
        return new Vector2(gravity);
    }
    
    public void updateImpulseTimer(float deltaTime) {
        impulseTime -= deltaTime;
        if (impulseTime < 0) {
            impulseTime = 0;
            lastImpulse = new Vector2();
        }
    }
    
    public Vector2 getLastImpulse() {
        return new Vector2(lastImpulse);
    }
    
    public boolean hasRecentImpulse() {
        return impulseTime > 0;
    }
    
    public void updateImmunityTimer(float deltaTime) {
        damageImmunityTimer -= deltaTime;
        if (damageImmunityTimer < 0) {
            damageImmunityTimer = 0;
        }
    }
    
    public void takeDamage(float damage) {
        if (damageImmunityTimer <= 0) {
            health -= damage;
            if (health < 0) health = 0;
            damageImmunityTimer = 0.5f;  // 无敌0.5秒
        }
    }

    public void setHealth(float health) {
        this.health = Math.max(0f, health);
    }

    public void setMaxHealth(float maxHealth) {
        this.maxHealth = Math.max(0f, maxHealth);
        if (this.health > this.maxHealth) this.health = this.maxHealth;
    }
    
    public float getHealth() {
        return health;
    }
    
    public float getMaxHealth() {
        return maxHealth;
    }
    
    public float getHealthPercent() {
        return maxHealth > 0 ? health / maxHealth : 1.0f;
    }
    
    public boolean isDead() {
        return health <= 0;
    }
    
    public boolean isInvincible() {
        return damageImmunityTimer > 0;
    }
}
