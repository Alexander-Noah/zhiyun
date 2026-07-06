package org.example.backend;

import org.example.backend.config.LocalDevelopmentSecretsInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;

@EnableScheduling
@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        LocalDevelopmentSecretsInitializer.ensure(Path.of(""));
        SpringApplication.run(BackendApplication.class, args);
    }

}
