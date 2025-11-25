package com.gameengine.example;

import com.gameengine.components.RenderComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameObject;
import com.gameengine.graphics.IRenderer;
import com.gameengine.math.Vector2;

public final class EntityFactory {
    private EntityFactory() {}

    public static GameObject createPlayerVisual(IRenderer renderer) {
        GameObject obj = new GameObject("Player") {
            private Vector2 basePosition;
            @Override
            public void update(float dt) {
                super.update(dt);
                TransformComponent tc = getComponent(TransformComponent.class);
                if (tc != null) basePosition = tc.getPosition();
            }
            @Override
            public void render() {
                if (basePosition == null) return;
                com.gameengine.components.RenderComponent rc = getComponent(com.gameengine.components.RenderComponent.class);
                com.gameengine.components.RenderComponent.Color color = rc != null ? rc.getColor() : new com.gameengine.components.RenderComponent.Color(1f,0f,0f,1f);
                float r = color.r, g = color.g, b = color.b, a = color.a;
                renderer.drawRect(basePosition.x - 8, basePosition.y - 10, 16, 20, r, g, b, a);
                renderer.drawRect(basePosition.x - 6, basePosition.y - 22, 12, 12, Math.min(1f, r+0.2f), Math.min(1f, g+0.2f), Math.min(1f, b+0.0f), a);
                renderer.drawRect(basePosition.x - 13, basePosition.y - 5, 6, 12, Math.min(1f, r+0.4f), Math.min(1f, g+0.6f), Math.min(1f, b+0.0f), a);
                renderer.drawRect(basePosition.x + 7, basePosition.y - 5, 6, 12, Math.min(1f, r-0.5f), Math.min(1f, g+0.6f), Math.min(1f, b+0.6f), a);
            }
        };
        // attach a RenderComponent so color can be modified at runtime (useful for replay)
        com.gameengine.components.RenderComponent prc = obj.addComponent(new com.gameengine.components.RenderComponent(
            com.gameengine.components.RenderComponent.RenderType.RECTANGLE,
            new com.gameengine.math.Vector2(16,20),
            new com.gameengine.components.RenderComponent.Color(1.0f, 0.0f, 0.0f, 1.0f)
        ));
        prc.setRenderer(renderer);
        return obj;
    }

    public static GameObject createPlayerVisual(IRenderer renderer, float r, float g, float b, float a) {
        GameObject obj = new GameObject("Player") {
            private Vector2 basePosition;
            @Override
            public void update(float dt) {
                super.update(dt);
                TransformComponent tc = getComponent(TransformComponent.class);
                if (tc != null) basePosition = tc.getPosition();
            }
            @Override
            public void render() {
                if (basePosition == null) return;
                com.gameengine.components.RenderComponent rc = getComponent(com.gameengine.components.RenderComponent.class);
                com.gameengine.components.RenderComponent.Color color = rc != null ? rc.getColor() : new com.gameengine.components.RenderComponent.Color(r,g,b,a);
                float rr = color.r, gg = color.g, bb = color.b, aa = color.a;
                renderer.drawRect(basePosition.x - 8, basePosition.y - 10, 16, 20, rr, gg, bb, aa);
                renderer.drawRect(basePosition.x - 6, basePosition.y - 22, 12, 12, Math.min(1f, rr+0.2f), Math.min(1f, gg+0.2f), Math.min(1f, bb+0.0f), aa);
                renderer.drawRect(basePosition.x - 13, basePosition.y - 5, 6, 12, Math.min(1f, rr+0.4f), Math.min(1f, gg+0.6f), Math.min(1f, bb+0.0f), aa);
                renderer.drawRect(basePosition.x + 7, basePosition.y - 5, 6, 12, Math.min(1f, rr-0.5f), Math.min(1f, gg+0.6f), Math.min(1f, bb+0.6f), aa);
            }
        };
        com.gameengine.components.RenderComponent prc = obj.addComponent(new com.gameengine.components.RenderComponent(
            com.gameengine.components.RenderComponent.RenderType.RECTANGLE,
            new com.gameengine.math.Vector2(16,20),
            new com.gameengine.components.RenderComponent.Color(r, g, b, a)
        ));
        prc.setRenderer(renderer);
        return obj;
    }

    public static GameObject createAIVisual(IRenderer renderer, float w, float h, float r, float g, float b, float a) {
        GameObject obj = new GameObject("AIPlayer");
        obj.addComponent(new TransformComponent(new Vector2(0, 0)));
        RenderComponent rc = obj.addComponent(new RenderComponent(
            RenderComponent.RenderType.RECTANGLE,
            new Vector2(Math.max(1, w), Math.max(1, h)),
            new RenderComponent.Color(r, g, b, a)
        ));
        rc.setRenderer(renderer);
        return obj;
    }
}


