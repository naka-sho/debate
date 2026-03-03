package com.example.debate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class DebateApplication {

    public static void main(String[] args) {
        SpringApplication.run(DebateApplication.class, args);
    }
}
