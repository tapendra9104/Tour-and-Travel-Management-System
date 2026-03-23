package com.toursim.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TourismManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(TourismManagementApplication.class, args);
    }
}
