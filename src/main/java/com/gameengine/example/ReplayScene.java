package com.gameengine.example;

import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameObject;
import com.gameengine.graphics.IRenderer;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;
import java.io.File;
import java.util.*;

public class ReplayScene extends Scene {
    private final GameEngine engine;
    private String recordingPath;
    private IRenderer renderer;
    private InputManager input;
    private float time;
    private boolean DEBUG_REPLAY = true;
    private float debugAccumulator = 0f;

    private static class Keyframe {
        static class EntityInfo {
            Vector2 pos;
            String rt; // RECTANGLE/CIRCLE/LINE/CUSTOM/null
            float w, h;
            float r=0.9f,g=0.9f,b=0.2f,a=1.0f; // 默认颜色
            boolean hasColor = false;
            String id;
        }
        double t;
        java.util.List<EntityInfo> entities = new ArrayList<>();
    }

    private final List<Keyframe> keyframes = new ArrayList<>();
    private final java.util.List<GameObject> objectList = new ArrayList<>();
    private final java.util.Map<String, GameObject> objectMap = new java.util.HashMap<>(); // ID -> GameObject 映射
    // 回放中记录的鼠标事件（用于重建子弹）
    private static class MouseEvent {
        double t;
        float x, y;
        int[] buttons;
    }
    private final java.util.List<MouseEvent> mouseEvents = new java.util.ArrayList<>();
    private int nextMouseEventIndex = 0;
    private int replayBulletCounter = 0;
    
    // 如果录制文件中未包含 player 的颜色，则在回放开始时要求用户选择
    private boolean playerColorMissing = false;

    // 如果 path 为 null，则先展示 recordings 目录下的文件列表，供用户选择
    public ReplayScene(GameEngine engine, String path) {
        super("Replay");
        this.engine = engine;
        this.recordingPath = path;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.renderer = engine.getRenderer();
        this.input = engine.getInputManager();
        // 重置状态，防止从列表进入后残留
        this.time = 0f;
        this.keyframes.clear();
        this.objectList.clear();
        if (recordingPath != null) {
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("replay_debug.log", true))) {
                pw.println("[" + System.currentTimeMillis() + "] ReplayScene.initialize: 开始加载 " + recordingPath);
                pw.flush();
            } catch (Exception e) {}
            
            loadRecording(recordingPath);
            
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("replay_debug.log", true))) {
                pw.println("[" + System.currentTimeMillis() + "] 加载完成, 关键帧数: " + keyframes.size());
                pw.flush();
            } catch (Exception e) {}
            
            buildObjectsFromFirstKeyframe();
            
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("replay_debug.log", true))) {
                pw.println("[" + System.currentTimeMillis() + "] 首帧对象数: " + objectList.size());
                pw.flush();
            } catch (Exception e) {}


            
        } else {
            // 仅进入文件选择模式
            this.recordingFiles = null;
            this.selectedIndex = 0;
        }
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        if (input.isKeyJustPressed(27) || input.isKeyJustPressed(8)) { // ESC/BACK
            engine.setScene(new MenuScene(engine, "MainMenu"));
            return;
        }
        // 文件选择模式
        if (recordingPath == null) {
            handleFileSelection();
            return;
        }

        // 直接播放回放（若录制缺少玩家颜色，已在初始化时应用默认颜色）

        if (keyframes.size() < 1) return;
        time += deltaTime;
        // 限制在最后关键帧处停止（也可选择循环播放）
        double lastT = keyframes.get(keyframes.size() - 1).t;
        if (time > lastT) {
            time = (float)lastT;
        }

        // 查找区间
        Keyframe a = keyframes.get(0);
        Keyframe b = keyframes.get(keyframes.size() - 1);
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe k1 = keyframes.get(i);
            Keyframe k2 = keyframes.get(i + 1);
            if (time >= k1.t && time <= k2.t) { a = k1; b = k2; break; }
        }
        double span = Math.max(1e-6, b.t - a.t);
        double u = Math.min(1.0, Math.max(0.0, (time - a.t) / span));
        // 调试输出节流
        

        updateInterpolatedPositions(a, b, (float)u);
        // 处理鼠标事件，生成回放子弹（基于当前玩家位置）
        while (nextMouseEventIndex < mouseEvents.size() && mouseEvents.get(nextMouseEventIndex).t <= time) {
            MouseEvent me = mouseEvents.get(nextMouseEventIndex);
            nextMouseEventIndex++;

            // 找到回放场景中的 Player 对象及其位置
            Vector2 playerPos = null;
            for (GameObject obj : objectMap.values()) {
                if (obj != null && "Player".equals(obj.getName())) {
                    TransformComponent tc = obj.getComponent(TransformComponent.class);
                    if (tc != null) { playerPos = tc.getPosition(); break; }
                }
            }
            if (playerPos == null) continue;

            Vector2 target = new Vector2(me.x, me.y);
            Vector2 dir = target.subtract(playerPos);
            if (dir.magnitude() == 0) dir = new Vector2(0, -1);
            dir = dir.normalize();
            final Vector2 velocity = dir.multiply(600.0f);

            // 创建一个简单的回放子弹对象，仅用于视觉效果
            GameObject bullet = new GameObject("ReplayBullet_" + (replayBulletCounter++)) {
                private float life = 0f;
                @Override
                public void update(float deltaTime) {
                    super.update(deltaTime);
                    TransformComponent tc = getComponent(TransformComponent.class);
                    if (tc == null) return;
                    tc.setPosition(tc.getPosition().add(velocity.multiply(deltaTime)));
                    life += deltaTime;
                    int w = renderer.getWidth();
                    int h = renderer.getHeight();
                    Vector2 p = tc.getPosition();
                    if (p.x < -20 || p.x > w + 20 || p.y < -20 || p.y > h + 20 || life > 6.0f) {
                        setActive(false);
                    }
                }

                @Override
                public void render() {
                    TransformComponent tc = getComponent(TransformComponent.class);
                    if (tc == null) return;
                    Vector2 p = tc.getPosition();
                    renderer.drawCircle(p.x, p.y, 3.0f, 8, 1.0f, 1.0f, 0.0f, 1.0f);
                }
            };
            bullet.addComponent(new TransformComponent(new Vector2(playerPos)));
            addGameObject(bullet);
        }

        
    }

    @Override
    public void render() {
        renderer.drawRect(0, 0, renderer.getWidth(), renderer.getHeight(), 0.06f, 0.06f, 0.08f, 1.0f);
        if (recordingPath == null) {
            renderFileList();
            return;
        }
        // 基于 Transform 手动绘制（回放对象没有附带 RenderComponent）
        super.render();
        
        // 直接播放回放；已在初始化时处理缺失的颜色

        String hint = "REPLAY: ESC to return";
        float w = hint.length() * 12.0f;
        renderer.drawText(renderer.getWidth()/2.0f - w/2.0f, 30, hint, 0.8f, 0.8f, 0.8f, 1.0f);
    }

    private void applyPlayerColor(int paletteIndex) {
        if (paletteIndex < 0 || paletteIndex >= REPLAY_PALETTE.length) paletteIndex = 0;
        float[] c = REPLAY_PALETTE[paletteIndex];
        for (java.util.Map.Entry<String, GameObject> entry : objectMap.entrySet()) {
            GameObject obj = entry.getValue();
            if (obj == null) continue;
            String nm = obj.getName();
            if (nm != null && nm.equalsIgnoreCase("Player")) {
                com.gameengine.components.RenderComponent rc = obj.getComponent(com.gameengine.components.RenderComponent.class);
                if (rc != null) {
                    com.gameengine.components.RenderComponent.Color col = rc.getColor();
                    if (col != null) {
                        col.r = c[0]; col.g = c[1]; col.b = c[2]; col.a = 1.0f;
                    }
                }
            }
        }
    }

    private void loadRecording(String path) {
        keyframes.clear();
        mouseEvents.clear();
        com.gameengine.recording.RecordingStorage storage = new com.gameengine.recording.FileRecordingStorage();
        try {
            for (String line : storage.readLines(path)) {
                if (line.contains("\"type\":\"keyframe\"")) {
                    Keyframe kf = new Keyframe();
                    kf.t = com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(line, "t"));
                    // 解析 entities 列表中的若干 {"id":"name","x":num,"y":num}
                    int idx = line.indexOf("\"entities\":[");
                    if (idx >= 0) {
                        int bracket = line.indexOf('[', idx);
                        String arr = bracket >= 0 ? com.gameengine.recording.RecordingJson.extractArray(line, bracket) : "";
                        String[] parts = com.gameengine.recording.RecordingJson.splitTopLevel(arr);
                        for (String p : parts) {
                            Keyframe.EntityInfo ei = new Keyframe.EntityInfo();
                            ei.id = com.gameengine.recording.RecordingJson.stripQuotes(com.gameengine.recording.RecordingJson.field(p, "id"));
                            double x = com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(p, "x"));
                            double y = com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(p, "y"));
                            ei.pos = new Vector2((float)x, (float)y);
                            String rt = com.gameengine.recording.RecordingJson.stripQuotes(com.gameengine.recording.RecordingJson.field(p, "rt"));
                            ei.rt = rt;
                            ei.w = (float)com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(p, "w"));
                            ei.h = (float)com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(p, "h"));
                            String colorArr = com.gameengine.recording.RecordingJson.field(p, "color");
                            if (colorArr != null && colorArr.startsWith("[")) {
                                String c = colorArr.substring(1, Math.max(1, colorArr.indexOf(']', 1)));
                                String[] cs = c.split(",");
                                if (cs.length >= 3) {
                                    try {
                                        ei.r = Float.parseFloat(cs[0].trim());
                                        ei.g = Float.parseFloat(cs[1].trim());
                                        ei.b = Float.parseFloat(cs[2].trim());
                                        if (cs.length >= 4) ei.a = Float.parseFloat(cs[3].trim());
                                        ei.hasColor = true;
                                    } catch (Exception ignored) {}
                                }
                            }
                            kf.entities.add(ei);
                        }
                    }
                    keyframes.add(kf);
                } else if (line.contains("\"type\":\"mouse\"")) {
                    try {
                        MouseEvent me = new MouseEvent();
                        me.t = com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(line, "t"));
                        me.x = (float)com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(line, "x"));
                        me.y = (float)com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(line, "y"));
                        String btnField = com.gameengine.recording.RecordingJson.field(line, "buttons");
                        if (btnField != null && btnField.startsWith("[")) {
                            String inner = btnField.substring(1, Math.max(1, btnField.indexOf(']',1)));
                            String[] parts = inner.split(",");
                            java.util.List<Integer> list = new java.util.ArrayList<>();
                            for (String p : parts) {
                                p = p.trim();
                                if (p.isEmpty()) continue;
                                try { list.add(Integer.parseInt(p)); } catch (Exception ignored) {}
                            }
                            me.buttons = new int[list.size()];
                            for (int i=0;i<list.size();i++) me.buttons[i]=list.get(i);
                        } else {
                            me.buttons = new int[0];
                        }
                        mouseEvents.add(me);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            
        }
        keyframes.sort(Comparator.comparingDouble(k -> k.t));
        mouseEvents.sort(Comparator.comparingDouble(m -> m.t));
        
    }

    private void buildObjectsFromFirstKeyframe() {
        if (keyframes.isEmpty()) return;
        Keyframe kf0 = keyframes.get(0);
        objectList.clear();
        objectMap.clear();
        clear();
        
        // 从第一帧的实体创建对象
        for (int i = 0; i < kf0.entities.size(); i++) {
            Keyframe.EntityInfo ei = kf0.entities.get(i);
            GameObject obj = buildObjectFromEntity(ei, i);
            addGameObject(obj);
            objectList.add(obj);
            objectMap.put(ei.id, obj);
            // 根据尺寸推断 HP（>28 视为大型）
            String nm = ei.id != null && ei.id.contains("_") ? ei.id.substring(0, ei.id.lastIndexOf("_")) : ei.id;
            if ("AIPlayer".equalsIgnoreCase(nm)) {
                // 不在回放中维护 HP（恢复为仅视觉回放）
            }
        }
        // 检查 Player 是否在录制时没有记录颜色；若缺失则直接应用默认红色（不在回放中要求手动选择）
        playerColorMissing = false;
        for (Keyframe.EntityInfo ei : kf0.entities) {
            String objName = ei.id != null && ei.id.contains("_") ? ei.id.substring(0, ei.id.lastIndexOf("_")) : ei.id;
            if ("Player".equalsIgnoreCase(objName) && !ei.hasColor) {
                playerColorMissing = true;
                break;
            }
        }
        if (playerColorMissing) {
            // 应用默认颜色（Palette index 0 = 红色）
            applyPlayerColor(0);
            playerColorMissing = false;
        }
        time = 0f;
    }

    private static final float[][] REPLAY_PALETTE = new float[][]{
        {1f,0f,0f}, // 红
        {1f,0.5f,0f}, // 橙
        {1f,1f,0f}, // 黄
        {0f,1f,0f}, // 绿
        {0f,0f,1f}, // 蓝
        {0.29f,0f,0.51f}, // 靛
        {0.58f,0f,0.83f} // 紫
    };

    private void updateInterpolatedPositions(Keyframe a, Keyframe b, float u) {
        // 跟踪当前帧中应该活跃的对象ID
        java.util.Set<String> activeIds = new java.util.HashSet<>();
        
        // 处理 b 帧中的所有实体
        for (int i = 0; i < b.entities.size(); i++) {
            Keyframe.EntityInfo eb = b.entities.get(i);
            activeIds.add(eb.id);
            
            // 从映射中获取或创建对象
            GameObject obj = objectMap.get(eb.id);
            if (obj == null) {
                // 新对象，创建并添加到场景
                obj = buildObjectFromEntity(eb, i);
                addGameObject(obj);
                objectMap.put(eb.id, obj);
                try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("replay_debug.log", true))) {
                    pw.println("[" + System.currentTimeMillis() + "] 创建新对象: " + eb.id);
                    pw.flush();
                } catch (Exception e) {}
            }
            
            // 更新位置
            Keyframe.EntityInfo ea = (i < a.entities.size()) ? a.entities.get(i) : null;
            Vector2 newPos = null;
            
            if (ea != null) {
                // 两帧都有数据，插值
                Vector2 pa = ea.pos;
                Vector2 pb = eb.pos;
                float x = (float)((1.0 - u) * pa.x + u * pb.x);
                float y = (float)((1.0 - u) * pa.y + u * pb.y);
                newPos = new Vector2(x, y);
            } else {
                // 第一帧没有，使用第二帧位置
                newPos = new Vector2(eb.pos);
            }
            
            // 应用位置
            TransformComponent tc = obj.getComponent(TransformComponent.class);
            if (tc != null) {
                tc.setPosition(newPos);
            } else {
                obj.addComponent(new TransformComponent(newPos));
            }
            // 如果实体带有 RenderComponent，则还原/插值颜色和尺寸信息
            com.gameengine.components.RenderComponent rc = obj.getComponent(com.gameengine.components.RenderComponent.class);
            if (rc != null) {
                float rr = eb.r;
                float gg = eb.g;
                float bb = eb.b;
                float aa = eb.a;
                float ww = eb.w;
                float hh = eb.h;
                if (ea != null) {
                    // 对颜色和尺寸做线性插值以获得平滑过渡（如果发生了切换）
                    rr = (float)((1.0 - u) * ea.r + u * eb.r);
                    gg = (float)((1.0 - u) * ea.g + u * eb.g);
                    bb = (float)((1.0 - u) * ea.b + u * eb.b);
                    aa = (float)((1.0 - u) * ea.a + u * eb.a);
                    ww = (float)((1.0 - u) * ea.w + u * eb.w);
                    hh = (float)((1.0 - u) * ea.h + u * eb.h);
                }
                com.gameengine.components.RenderComponent.Color col = rc.getColor();
                if (col != null) {
                    col.r = rr; col.g = gg; col.b = bb; col.a = aa;
                }
                com.gameengine.math.Vector2 sz = rc.getSize();
                if (sz != null) {
                    sz.x = Math.max(1, ww); sz.y = Math.max(1, hh);
                }
            }
        }
        
        // 停用不再活跃的对象
        java.util.List<String> toRemove = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, GameObject> entry : objectMap.entrySet()) {
            if (!activeIds.contains(entry.getKey())) {
                entry.getValue().setActive(false);
                toRemove.add(entry.getKey());
            }
        }
        for (String id : toRemove) {
            objectMap.remove(id);
        }
    }

    private GameObject buildObjectFromEntity(Keyframe.EntityInfo ei, int index) {
        GameObject obj;
        
        // 从 ID 中提取对象名称（格式: Name_UniqueId）
        String objName = ei.id;
        if (ei.id != null && ei.id.contains("_")) {
            objName = ei.id.substring(0, ei.id.lastIndexOf("_"));
        }
        
        if ("Player".equalsIgnoreCase(objName)) {
            obj = com.gameengine.example.EntityFactory.createPlayerVisual(renderer, ei.r, ei.g, ei.b, ei.a);
        } else if ("AIPlayer".equalsIgnoreCase(objName)) {
            float w2 = (ei.w > 0 ? ei.w : 20);
            float h2 = (ei.h > 0 ? ei.h : 20);
            obj = com.gameengine.example.EntityFactory.createAIVisual(renderer, w2, h2, ei.r, ei.g, ei.b, ei.a);
        } else if ("Bullet".equalsIgnoreCase(objName)) {
            // 子弹：为了便于跟踪位置，创建一个占位符 GameObject，但在 render() 中单独绘制子弹
            obj = new GameObject("Bullet");
            obj.addComponent(new TransformComponent(new Vector2(ei.pos)));
        } else {
            if ("CIRCLE".equals(ei.rt)) {
                GameObject tmp = new GameObject(ei.id == null ? ("Obj#"+index) : ei.id);
                tmp.addComponent(new TransformComponent(new Vector2(0,0)));
                com.gameengine.components.RenderComponent rc = tmp.addComponent(
                    new com.gameengine.components.RenderComponent(
                        com.gameengine.components.RenderComponent.RenderType.CIRCLE,
                        new Vector2(Math.max(1, ei.w), Math.max(1, ei.h)),
                        new com.gameengine.components.RenderComponent.Color(ei.r, ei.g, ei.b, ei.a)
                    )
                );
                rc.setRenderer(renderer);
                obj = tmp;
            } else {
                obj = com.gameengine.example.EntityFactory.createAIVisual(renderer, Math.max(1, ei.w>0?ei.w:10), Math.max(1, ei.h>0?ei.h:10), ei.r, ei.g, ei.b, ei.a);
            }
            obj.setName(ei.id == null ? ("Obj#"+index) : ei.id);
        }
        TransformComponent tc = obj.getComponent(TransformComponent.class);
        if (tc == null) obj.addComponent(new TransformComponent(new Vector2(ei.pos)));
        else tc.setPosition(new Vector2(ei.pos));
        return obj;
    }

    // ========== 文件列表模式 ==========
    private List<File> recordingFiles;
    private int selectedIndex = 0;

    private void ensureFilesListed() {
        if (recordingFiles != null) return;
        com.gameengine.recording.RecordingStorage storage = new com.gameengine.recording.FileRecordingStorage();
        recordingFiles = storage.listRecordings();
    }

    private void handleFileSelection() {
        ensureFilesListed();
        if (input.isKeyJustPressed(38) || input.isKeyJustPressed(265)) { // up (AWT 38 / GLFW 265)
            selectedIndex = (selectedIndex - 1 + Math.max(1, recordingFiles.size())) % Math.max(1, recordingFiles.size());
        } else if (input.isKeyJustPressed(40) || input.isKeyJustPressed(264)) { // down (AWT 40 / GLFW 264)
            selectedIndex = (selectedIndex + 1) % Math.max(1, recordingFiles.size());
        } else if (input.isKeyJustPressed(10) || input.isKeyJustPressed(32) || input.isKeyJustPressed(257) || input.isKeyJustPressed(335)) { // enter/space (AWT 10/32, GLFW 257/335)
            if (recordingFiles.size() > 0) {
                String path = recordingFiles.get(selectedIndex).getAbsolutePath();
                this.recordingPath = path;
                clear();
                initialize();
            }
        } else if (input.isKeyJustPressed(27)) { // esc
            engine.setScene(new MenuScene(engine, "MainMenu"));
        }
    }

    private void renderFileList() {
        ensureFilesListed();
        int w = renderer.getWidth();
        int h = renderer.getHeight();
        String title = "SELECT RECORDING";
        float tw = title.length() * 16f;
        renderer.drawText(w/2f - tw/2f, 80, title, 1f,1f,1f,1f);

        if (recordingFiles.isEmpty()) {
            String none = "NO RECORDINGS FOUND";
            float nw = none.length() * 14f;
            renderer.drawText(w/2f - nw/2f, h/2f, none, 0.9f,0.8f,0.2f,1f);
            String back = "ESC TO RETURN";
            float bw = back.length() * 12f;
            renderer.drawText(w/2f - bw/2f, h - 60, back, 0.7f,0.7f,0.7f,1f);
            return;
        }

        float startY = 140f;
        float itemH = 32f;
        float leftX = 100f;
        for (int i = 0; i < recordingFiles.size(); i++) {
            String name = recordingFiles.get(i).getName();
            float textX = leftX;
            float textY = startY + i * itemH;

            // 基于文本宽度计算高亮矩形，确保对齐
            float textWidth = name.length() * 14f;
            float paddingX = 18f;
            float rectW = Math.max(640f, textWidth + paddingX * 2); // 更长的高亮矩形
            float rectH = 36f; // 更高一点
            float rectX = textX - paddingX;
            // 向下移动矩形，使高亮看起来更靠近文本基线
            float rectY = textY - 8f;

            if (i == selectedIndex) {
                renderer.drawRect(rectX, rectY, rectW, rectH, 0.3f,0.3f,0.4f,0.8f);
            }
            renderer.drawText(textX, textY, name, 0.9f,0.9f,0.9f,1f);
        }

        String hint = "UP/DOWN SELECT, ENTER PLAY, ESC RETURN";
        float hw = hint.length() * 12f;
        renderer.drawText(w/2f - hw/2f, h - 60, hint, 0.7f,0.7f,0.7f,1f);
    }

    // 解析相关逻辑已移至 RecordingJson
}


