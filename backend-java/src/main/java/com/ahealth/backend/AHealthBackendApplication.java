package com.ahealth.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AHealthBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(AHealthBackendApplication.class, args);
  }
}
