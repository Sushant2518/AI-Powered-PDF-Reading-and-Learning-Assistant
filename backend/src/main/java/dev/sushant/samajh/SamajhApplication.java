package dev.sushant.samajh;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
@EnableAsync
public class SamajhApplication implements WebMvcConfigurer {

	@Value("${samajh.frontend-origin}")
	private String frontendOrigin;

	public static void main(String[] args) {
		SpringApplication.run(SamajhApplication.class, args);
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**").allowedOrigins(frontendOrigin).allowedMethods("*");
	}
}
