package com.app.eventmanagmentsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EventManagmnetSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventManagmnetSystemApplication.class, args);
	}

}
