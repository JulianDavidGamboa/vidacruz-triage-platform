package com.vidacruz.assistant_service;

import org.springframework.boot.SpringApplication;

public class TestAssistantServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(AssistantServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
