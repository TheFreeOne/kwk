package org.freeone.k8s.web.knife.config.websocket.ica10888.dto;

public class Pod {

    private String name;
    private String namespace;
    private String container;

    public Pod(String name, String namespace, String container) {
        this.name = name;
        this.namespace = namespace;
        this.container = container;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getContainer() {
        return container;
    }

    public void setContainer(String container) {
        this.container = container;
    }
}
