package com.ledgerlens;

import com.ledgerlens.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableCaching
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.ledgerlens")
@EnableConfigurationProperties(JwtProperties.class)
public class LedgerlensApplication {

	public static void main(String[] args) {
		SpringApplication.run(LedgerlensApplication.class, args);
	}

}
