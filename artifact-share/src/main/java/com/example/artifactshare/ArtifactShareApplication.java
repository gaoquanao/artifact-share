package com.example.artifactshare;

import com.example.artifactshare.config.AgentProperties;
import com.example.artifactshare.config.ShareProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties({ShareProperties.class, AgentProperties.class})
@EnableAsync
public class ArtifactShareApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArtifactShareApplication.class, args);
    }
}
