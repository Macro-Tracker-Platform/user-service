package com.olehprukhnytskyi.macrotrackeruserservice.config;

import org.testcontainers.containers.GenericContainer;

public class CustomRedisContainer extends GenericContainer<CustomRedisContainer> {
    private static final String IMAGE_VERSION = "redis:8";
    private static CustomRedisContainer container;

    private CustomRedisContainer() {
        super(IMAGE_VERSION);
        this.withExposedPorts(6379);
        this.withReuse(true);
    }

    public static synchronized CustomRedisContainer getInstance() {
        if (container == null) {
            container = new CustomRedisContainer();
        }
        return container;
    }

    @Override
    public void start() {
        super.start();
        String address = container.getHost();
        Integer port = container.getMappedPort(6379);

        System.setProperty("spring.data.redis.host", address);
        System.setProperty("spring.data.redis.port", port.toString());
    }

    @Override
    public void stop() {
    }
}
