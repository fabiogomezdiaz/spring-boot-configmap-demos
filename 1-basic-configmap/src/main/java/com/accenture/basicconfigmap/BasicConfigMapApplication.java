package com.accenture.basicconfigmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BasicConfigMapApplication {

	public static void main(String[] args) {
		SpringApplication.run(BasicConfigMapApplication.class, args);
	}

}