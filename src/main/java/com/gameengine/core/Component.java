package com.gameengine.core;

public abstract class Component<T extends Component<T>> {
    protected GameObject owner;
    protected boolean enabled;
    protected String name;
    
    public Component() {
        this.enabled = true;
        this.name = this.getClass().getSimpleName();
    }
    
    public abstract void initialize();
    
    public void update(float deltaTime) {
    }
    
    public abstract void render();
    
    public void destroy() {
        this.enabled = false;
    }
    
    @SuppressWarnings("unchecked")
    public Class<T> getComponentType() {
        return (Class<T>) this.getClass();
    }
    
    public GameObject getOwner() {
        return owner;
    }
    
    public void setOwner(GameObject owner) {
        this.owner = owner;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
}
