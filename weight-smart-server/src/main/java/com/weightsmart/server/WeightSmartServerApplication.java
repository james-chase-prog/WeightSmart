package com.weightsmart.server;

import org.springframework.boot.SpringApplication; // Bootstrap launcher for the Spring IoC container
import org.springframework.boot.autoconfigure.SpringBootApplication; // Meta-annotation: @Configuration + @EnableAutoConfiguration + @ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling; // Activates Spring's @Scheduled task executor

/*
 * WeightSmartServerApplication
 * Entry point for the WeightSmart REST API server.
 *
 * Architecture Role:
 * Bootstraps the Spring Boot application context, triggering component scanning across the
 * {@code com.weightsmart.server} package tree. All {@code @Service}, {@code @Repository},
 * {@code @Controller}, and {@code @Configuration} classes are discovered and wired automatically.
 *
 * Key Concepts & Documentation:
 * @SpringBootApplication: Convenience meta-annotation that combines @Configuration (Java-based config),
 * @EnableAutoConfiguration (auto-configures beans based on classpath), and @ComponentScan (scans this package).
 * <a href="https://docs.spring.io/spring-boot/reference/using/using-the-springbootapplication-annotation.html">Reference: @SpringBootApplication</a>
 * @EnableScheduling: Activates the TaskScheduler, enabling @Scheduled methods (e.g., Trie rebuild at 3 AM,
 * rate limiter bucket cleanup every 5 minutes) to execute on background threads.
 * <a href="https://docs.spring.io/spring-framework/reference/integration/scheduling.html">Reference: Spring Task Scheduling</a>
 *
 * @author James Chase
 * @version 1.1
 * @since 2026-01-14
 */

@SpringBootApplication // Combines @Configuration + @EnableAutoConfiguration + @ComponentScan
@EnableScheduling // Activates @Scheduled tasks (Trie rebuild, rate limiter cleanup)
public class WeightSmartServerApplication {

    /**
     * JVM entry point. Launches the embedded Tomcat server and initializes the Spring context.
     *
     * @param args Command-line arguments (currently unused; reserved for profile overrides).
     */
    public static void main(String[] args) {
        SpringApplication.run(WeightSmartServerApplication.class, args);
    }

}
