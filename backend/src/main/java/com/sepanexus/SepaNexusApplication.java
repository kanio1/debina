package com.sepanexus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SepaNexusApplication {

    public static void main(String[] args) {
        SpringApplication.run(SepaNexusApplication.class, args);
    }
}
