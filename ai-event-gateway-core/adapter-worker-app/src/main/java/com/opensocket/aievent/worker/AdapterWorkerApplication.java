package com.opensocket.aievent.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(AdapterWorkerProperties.class)
public class AdapterWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdapterWorkerApplication.class, args);
    }
}
