package com.az.notifications;

import org.springframework.boot.SpringApplication;

public class TestNotificationsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(NotificationsServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
