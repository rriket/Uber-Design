package com.uberclone.ratings;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.uberclone.ratings", "com.uberclone.common"})
public class RatingsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RatingsServiceApplication.class, args);
    }
}
