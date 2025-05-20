package com.olehprukhnytskyi.macrotrackeruserservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class MacroTrackerUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MacroTrackerUserServiceApplication.class, args);
    }

}
