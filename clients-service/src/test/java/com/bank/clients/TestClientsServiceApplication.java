package com.bank.clients;

import org.springframework.boot.SpringApplication;

public class TestClientsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(ClientsServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
