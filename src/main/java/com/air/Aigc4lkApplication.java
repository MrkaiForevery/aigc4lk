package com.air;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication
public class Aigc4lkApplication {

    public static void main(String[] args) {
        SpringApplication.run(Aigc4lkApplication.class, args);
    }

}
