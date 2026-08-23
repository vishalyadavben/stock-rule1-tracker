package com.rule1.tracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // needed for the scheduled real-time price refresh job
public class Rule1TrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(Rule1TrackerApplication.class, args);
    }
}
