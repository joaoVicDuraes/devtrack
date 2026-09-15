package com.devtrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the DevTrack backend.
 *
 * <p>{@code @SpringBootApplication} is a convenience annotation that bundles three things:
 * <ul>
 *   <li>{@code @Configuration} - marks this class as a source of bean definitions.</li>
 *   <li>{@code @EnableAutoConfiguration} - tells Spring Boot to configure beans based on the
 *       dependencies on the classpath (e.g. an embedded web server because spring-boot-starter-web
 *       is present, and JPA because spring-boot-starter-data-jpa is present).</li>
 *   <li>{@code @ComponentScan} - scans this package and its sub-packages for components
 *       (controllers, services, repositories) so they are picked up automatically.</li>
 * </ul>
 *
 * <p>Because component scanning starts from this class's package ({@code com.devtrack}),
 * later classes (controllers, services, entities) should live in {@code com.devtrack} or a
 * sub-package so Spring can find them.
 */
@SpringBootApplication
public class DevtrackBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevtrackBackendApplication.class, args);
    }
}
