package com.app.eventmanagmentsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = "com.app.eventmanagmentsystem.model")
@EnableJpaRepositories(basePackages = "com.app.eventmanagmentsystem.repository")
public class EventManagmnetSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventManagmnetSystemApplication.class, args);
	}

}
