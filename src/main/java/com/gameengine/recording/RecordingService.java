package com.gameengine.recording;

import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameObject;
import com.gameengine.input.InputManager;
import com.gameengine.scene.Scene;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class RecordingService {
    private final RecordingConfig config;
    private final BlockingQueue<String> lineQueue;
    private volatile boolean recording;
    private Thread writerThread;
    private RecordingStorage storage = new FileRecordingStorage();
    private double elapsed;
    private double keyframeElapsed;
    private double sampleAccumulator;
    private final double warmupSec = 0.1; // 等待一帧让场景对象完成初始化
    private final DecimalFormat qfmt;
    private Scene lastScene;

    public RecordingService(RecordingConfig config) {
        this.config = config;
        this.lineQueue = new ArrayBlockingQueue<>(config.queueCapacity);
        this.recording = false;
        this.elapsed = 0.0;
        this.keyframeElapsed = 0.0;
        this.sampleAccumulator = 0.0;
        this.qfmt = new DecimalFormat();
        this.qfmt.setMaximumFractionDigits(Math.max(0, config.quantizeDecimals));
        this.qfmt.setGroupingUsed(false);
    }

    public boolean isRecording() {
        return recording;
    }

    public void start(Scene scene, int width, int height) throws IOException {
        if (recording) return;
        storage.openWriter(config.outputPath);
        writerThread = new Thread(() -> {
            try {
                while (recording || !lineQueue.isEmpty()) {
                    String s = lineQueue.poll();
                    if (s == null) {
                        try { Thread.sleep(2); } catch (InterruptedException ignored) {}
                        continue;
                    }
                    storage.writeLine(s);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try { storage.closeWriter(); } catch (Exception ignored) {}
            }
        }, "record-writer");
        recording = true;
        writerThread.start();

        // header
        enqueue("{\"type\":\"header\",\"version\":1,\"w\":" + width + ",\"h\":" + height + "}");
        keyframeElapsed = 0.0;
    }

    public void stop() {
        if (!recording) return;
        try {
            if (lastScene != null) {
                writeKeyframe(lastScene);
            }
        } catch (Exception ignored) {}
        recording = false;
        try { writerThread.join(500); } catch (InterruptedException ignored) {}
    }

    public void update(double deltaTime, Scene scene, InputManager input) {
        if (!recording) return;
        elapsed += deltaTime;
        keyframeElapsed += deltaTime;
        sampleAccumulator += deltaTime;
        lastScene = scene;

        // input events (sample at native frequency, but只写有justPressed)
        Set<Integer> just = input.getJustPressedKeysSnapshot();
        if (!just.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\":\"input\",\"t\":").append(qfmt.format(elapsed)).append(",\"keys\":[");
            boolean first = true;
            for (Integer k : just) {
                if (!first) sb.append(',');
                sb.append(k);
                first = false;
            }
            sb.append("]}");
            enqueue(sb.toString());
        }

        // 鼠标按键事件（记录按下与位置）
        try {
            java.util.List<Integer> mbuttons = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                if (input.isMouseButtonJustPressed(i)) mbuttons.add(i);
            }
            if (!mbuttons.isEmpty()) {
                StringBuilder sbm = new StringBuilder();
                sbm.append("{\"type\":\"mouse\",\"t\":").append(qfmt.format(elapsed)).append(",\"buttons\":[");
                for (int i = 0; i < mbuttons.size(); i++) {
                    if (i > 0) sbm.append(',');
                    sbm.append(mbuttons.get(i));
                }
                sbm.append("],\"x\":").append(qfmt.format(input.getMouseX())).append(",\"y\":").append(qfmt.format(input.getMouseY())).append('}');
                enqueue(sbm.toString());
            }
        } catch (Exception ignored) {}

        // sampled deltas placeholder（可扩展）：此处先跳过，保持最小版本

        // periodic keyframe（跳过开头暖机，避免空关键帧）
        if (elapsed >= warmupSec && keyframeElapsed >= config.keyframeIntervalSec) {
            if (writeKeyframe(scene)) {
                keyframeElapsed = 0.0;
            }
        }
    }

    private boolean writeKeyframe(Scene scene) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"keyframe\",\"t\":").append(qfmt.format(elapsed)).append(",\"entities\":[");
        List<GameObject> objs = scene.getGameObjects();
        boolean first = true;
        int count = 0;
        for (GameObject obj : objs) {
            TransformComponent tc = obj.getComponent(TransformComponent.class);
            if (tc == null) continue;
            float x = tc.getPosition().x;
            float y = tc.getPosition().y;
            if (!first) sb.append(',');
            sb.append('{')
              .append("\"id\":\"").append(obj.getName()).append("_").append(obj.getUniqueId()).append("\",")
              .append("\"x\":").append(qfmt.format(x)).append(',')
              .append("\"y\":").append(qfmt.format(y));

            // 可选渲染信息（若对象带有 RenderComponent，则记录形状、尺寸、颜色）
            com.gameengine.components.RenderComponent rc = obj.getComponent(com.gameengine.components.RenderComponent.class);
            if (rc != null) {
                com.gameengine.components.RenderComponent.RenderType rt = rc.getRenderType();
                com.gameengine.math.Vector2 sz = rc.getSize();
                com.gameengine.components.RenderComponent.Color col = rc.getColor();
                sb.append(',')
                  .append("\"rt\":\"").append(rt.name()).append("\",")
                  .append("\"w\":").append(qfmt.format(sz.x)).append(',')
                  .append("\"h\":").append(qfmt.format(sz.y)).append(',')
                  .append("\"color\":[")
                  .append(qfmt.format(col.r)).append(',')
                  .append(qfmt.format(col.g)).append(',')
                  .append(qfmt.format(col.b)).append(',')
                  .append(qfmt.format(col.a)).append(']');
            } else {
                // 标记自定义渲染（如 Player），方便回放做近似还原
                sb.append(',').append("\"rt\":\"CUSTOM\"");
            }

            sb.append('}');
            first = false;
            count++;
        }
        
        sb.append("]}");
        if (count == 0) return false;
        enqueue(sb.toString());
        return true;
    }

    private void enqueue(String line) {
        if (!lineQueue.offer(line)) {
            // 简单丢弃策略：队列满时丢弃低优先级数据（此处直接丢弃）
        }
    }
}


