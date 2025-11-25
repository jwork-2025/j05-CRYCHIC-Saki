package com.gameengine.core;

import com.gameengine.graphics.IRenderer;
import com.gameengine.graphics.RenderBackend;
import com.gameengine.graphics.RendererFactory;
import com.gameengine.input.InputManager;
import com.gameengine.scene.Scene;


public class GameEngine {
    private IRenderer renderer;
    private InputManager inputManager;
    private Scene currentScene;
    private PhysicsSystem physicsSystem;
    private boolean running;
    private float targetFPS;
    private float deltaTime;
    private long lastTime;
    @SuppressWarnings("unused")
    private String title;
    // 新录制服务（可选）
    private com.gameengine.recording.RecordingService recordingService;
    
    public GameEngine(int width, int height, String title) {
        this(width, height, title, RenderBackend.GPU);
    }
    
    public GameEngine(int width, int height, String title, RenderBackend backend) {
        this.title = title;
        this.renderer = RendererFactory.createRenderer(backend, width, height, title);
        this.inputManager = InputManager.getInstance();
        this.running = false;
        this.targetFPS = 60.0f;
        this.deltaTime = 0.0f;
        this.lastTime = System.nanoTime();
        
    }
    
    public boolean initialize() {
        return true;
    }
    
    public void run() {
        if (!initialize()) {
            System.err.println("游戏引擎初始化失败");
            return;
        }
        
        running = true;
        
        if (currentScene != null) {
            currentScene.initialize();
            if (currentScene.getName().equals("MainMenu")) {
                physicsSystem = null;
            } else {
                physicsSystem = new PhysicsSystem(currentScene, renderer.getWidth(), renderer.getHeight());
            }
            
        }
        
        long lastFrameTime = System.nanoTime();
        long frameTimeNanos = (long)(1_000_000_000.0 / targetFPS);
        
        while (running) {
            long currentTime = System.nanoTime();
            
            if (currentTime - lastFrameTime >= frameTimeNanos) {
                update();
                if (running) {
                    render();
                }
                lastFrameTime = currentTime;
            }
            
            renderer.pollEvents();
            
            if (renderer.shouldClose()) {
                running = false;
            }
            
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    private void update() {
        long currentTime = System.nanoTime();
        deltaTime = (currentTime - lastTime) / 1_000_000_000.0f;
        lastTime = currentTime;
        
        renderer.pollEvents();
        
        
        
        if (currentScene != null) {
            currentScene.update(deltaTime);
        }
        
        if (physicsSystem != null) {
            physicsSystem.update(deltaTime);
        }
        
        if (recordingService != null && recordingService.isRecording()) {
            recordingService.update(deltaTime, currentScene, inputManager);
        }
        
        inputManager.update();
        
        if (inputManager.isKeyPressed(27)) {
            running = false;
            cleanup();
        }
        
        if (renderer.shouldClose() && running) {
            running = false;
            cleanup();
        }
    }
    
    private void render() {
        if (renderer == null) return;
        
        renderer.beginFrame();
        
        if (currentScene != null) {
            currentScene.render();
        }
        
        renderer.endFrame();
    }
    
    public void setScene(Scene scene) {
        if (currentScene != null) {
            if (physicsSystem != null) {
                physicsSystem.cleanup();
                physicsSystem = null;
            }
            currentScene.clear();
        }
        this.currentScene = scene;
        if (scene != null) {
            if (running) {
                scene.initialize();
                if (!scene.getName().equals("MainMenu") && !scene.getName().equals("Replay")) {
                    physicsSystem = new PhysicsSystem(scene, renderer.getWidth(), renderer.getHeight());
                }
            }
        }
    }
    
    public Scene getCurrentScene() {
        return currentScene;
    }
    
    public void stop() {
        running = false;
    }
    
    public void cleanup() {
        if (recordingService != null && recordingService.isRecording()) {
            try { recordingService.stop(); } catch (Exception ignored) {}
        }
        if (physicsSystem != null) {
            physicsSystem.cleanup();
        }
        if (currentScene != null) {
            currentScene.clear();
        }
        renderer.cleanup();
    }

    // 可选：外部启用录制（按需调用）
    public void enableRecording(com.gameengine.recording.RecordingService service) {
        this.recordingService = service;
        try {
            if (service != null && currentScene != null) {
                service.start(currentScene, renderer.getWidth(), renderer.getHeight());
            }
        } catch (Exception e) {
            System.err.println("录制启动失败: " + e.getMessage());
        }
    }

    public com.gameengine.recording.RecordingService getRecordingService() {
        return this.recordingService;
    }

    public void disableRecording() {
        if (recordingService != null && recordingService.isRecording()) {
            try { recordingService.stop(); } catch (Exception ignored) {}
        }
        recordingService = null;
    }
    
    
    
    public IRenderer getRenderer() {
        return renderer;
    }
    
    public InputManager getInputManager() {
        return inputManager;
    }
    
    public float getDeltaTime() {
        return deltaTime;
    }
    
    public void setTargetFPS(float fps) {
        this.targetFPS = fps;
    }
    
    public float getTargetFPS() {
        return targetFPS;
    }
    
    public boolean isRunning() {
        return running;
    }
}
