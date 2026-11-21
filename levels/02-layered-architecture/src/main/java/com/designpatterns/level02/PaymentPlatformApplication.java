package com.designpatterns.level02;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FLOW S.1: the program starts here.
 *
 * <p>{@code @SpringBootApplication} is three annotations in one:
 * <ul>
 *   <li>{@code @Configuration}: this class may hold {@code @Bean} recipes;</li>
 *   <li>{@code @EnableAutoConfiguration}: Spring Boot sets up what it finds on the classpath (web server,
 *       H2 DataSource, JPA/Hibernate, Jackson, Spring Data repositories);</li>
 *   <li>{@code @ComponentScan}: every {@code @Component}/{@code @Service}/{@code @Repository}/
 *       {@code @RestController} in this package and below becomes a bean.</li>
 * </ul>
 * Spring then creates every bean, passing each one its constructor arguments (DEPENDENCY INJECTION), in
 * the order the dependencies require. Next: {@code config.PaymentProperties} (FLOW S.2).
 */
@SpringBootApplication
public class PaymentPlatformApplication {

    public static void main(String[] args){
        SpringApplication.run(PaymentPlatformApplication.class, args);
    }
}
