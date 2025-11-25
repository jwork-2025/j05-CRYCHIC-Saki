package com.gameengine.components;

import com.gameengine.core.Component;
import com.gameengine.graphics.IRenderer;
import com.gameengine.math.Vector2;

public class RenderComponent extends Component<RenderComponent> {
    private IRenderer renderer;
    private RenderType renderType;
    private Vector2 size;
    private Color color;
    private boolean visible;
    
    public enum RenderType {
        RECTANGLE,
        CIRCLE,
        LINE
    }
    
    public static class Color {
        public float r, g, b, a;
        
        public Color(float r, float g, float b, float a) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }
        
        public Color(float r, float g, float b) {
            this(r, g, b, 1.0f);
        }
    }
    
    public RenderComponent() {
        this.renderType = RenderType.RECTANGLE;
        this.size = new Vector2(20, 20);
        this.color = new Color(1.0f, 1.0f, 1.0f, 1.0f);
        this.visible = true;
    }
    
    public RenderComponent(RenderType renderType, Vector2 size, Color color) {
        this.renderType = renderType;
        this.size = new Vector2(size);
        this.color = color;
        this.visible = true;
    }
    
    @Override
    public void initialize() {
    }
    
    @Override
    public void render() {
        if (!visible || renderer == null) {
            return;
        }
        
        TransformComponent transform = owner.getComponent(TransformComponent.class);
        if (transform == null) {
            return;
        }
        
        Vector2 position = transform.getPosition();
        
        switch (renderType) {
            case RECTANGLE:
                renderer.drawRect(position.x, position.y, size.x, size.y, 
                                color.r, color.g, color.b, color.a);
                break;
            case CIRCLE:
                renderer.drawCircle(position.x + size.x/2, position.y + size.y/2, 
                                  size.x/2, 16, color.r, color.g, color.b, color.a);
                break;
            case LINE:
                renderer.drawLine(position.x, position.y, 
                                position.x + size.x, position.y + size.y,
                                color.r, color.g, color.b, color.a);
                break;
        }
    }
    
    public void setRenderer(IRenderer renderer) {
        this.renderer = renderer;
    }
    
    public void setColor(Color color) {
        this.color = color;
    }
    
    public void setColor(float r, float g, float b, float a) {
        this.color = new Color(r, g, b, a);
    }
    
    public void setSize(Vector2 size) {
        this.size = new Vector2(size);
    }
    
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    public RenderType getRenderType() {
        return renderType;
    }
    
    public Vector2 getSize() {
        return new Vector2(size);
    }
    
    public Color getColor() {
        return color;
    }
    
    public boolean isVisible() {
        return visible;
    }
}
