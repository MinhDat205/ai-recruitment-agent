package com.recruitment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Nhanh dau tien bat @EnableScheduling (D1, ResumeParsingScheduler) - khong co pattern san.
@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
