package com.olehprukhnytskyi.macrotrackeruserservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
public abstract class AbstractRedisTest {
    @Container
    private static final CustomRedisContainer redis = CustomRedisContainer.getInstance();

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanDatabase() {
        if (redisTemplate.getConnectionFactory() != null) {
            redisTemplate.getConnectionFactory()
                    .getConnection()
                    .serverCommands()
                    .flushDb();
        }
    }
}
