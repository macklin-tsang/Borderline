package com.borderline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BorderlineApplication {

    public static void main(String[] args) {
        SpringApplication.run(BorderlineApplication.class, args);
    }

}
