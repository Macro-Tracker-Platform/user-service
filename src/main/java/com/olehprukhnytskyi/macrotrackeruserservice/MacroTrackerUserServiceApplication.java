package com.olehprukhnytskyi.macrotrackeruserservice;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@OpenAPIDefinition(
        info = @Info(
                title = "User Service API",
                version = "1.0",
                description = "Microservice for user authentication, "
                        + "profile management and goal tracking"
        )
)
@EnableJpaRepositories(basePackages = {
        "com.olehprukhnytskyi.repository.jpa",
        "com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa"
})
@EntityScan(basePackages = {
        "com.olehprukhnytskyi.macrotrackeruserservice.model",
        "com.olehprukhnytskyi.model"
})
@EnableCaching
@EnableScheduling
@EnableFeignClients
@SpringBootApplication
@ConfigurationPropertiesScan
public class MacroTrackerUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MacroTrackerUserServiceApplication.class, args);
    }

}
