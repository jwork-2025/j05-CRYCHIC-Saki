package com.gameengine.components;

import com.gameengine.core.Component;
import com.gameengine.math.Vector2;

public class TransformComponent extends Component<TransformComponent> {
    private Vector2 position;
    private Vector2 scale;
    private float rotation;
    
    public TransformComponent() {
        this.position = new Vector2();
        this.scale = new Vector2(1, 1);
        this.rotation = 0;
    }
    
    public TransformComponent(Vector2 position) {
        this();
        this.position = new Vector2(position);
    }
    
    public TransformComponent(Vector2 position, Vector2 scale, float rotation) {
        this.position = new Vector2(position);
        this.scale = new Vector2(scale);
        this.rotation = rotation;
    }
    
    @Override
    public void initialize() {
    }
    
    @Override
    public void render() {
    }
    
    public void moveTo(Vector2 newPosition) {
        this.position = new Vector2(newPosition);
    }
    
    public void translate(Vector2 delta) {
        this.position = position.add(delta);
    }
    
    public void rotate(float angle) {
        this.rotation += angle;
    }
    
    public void setRotation(float angle) {
        this.rotation = angle;
    }
    
    public void scale(Vector2 scaleFactor) {
        this.scale = new Vector2(this.scale.x * scaleFactor.x, this.scale.y * scaleFactor.y);
    }
    
    public void setScale(Vector2 newScale) {
        this.scale = new Vector2(newScale);
    }
    
    public Vector2 getPosition() {
        return new Vector2(position);
    }
    
    public void setPosition(Vector2 position) {
        this.position = new Vector2(position);
    }
    
    public Vector2 getScale() {
        return new Vector2(scale);
    }
    
    public float getRotation() {
        return rotation;
    }
}
