package com.vidacruz.events_contract;

import org.springframework.boot.SpringApplication;

public class TestEventsContractApplication {

	public static void main(String[] args) {
		SpringApplication.from(EventsContractApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
