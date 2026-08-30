/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the standalone DMS service. Run with:
 *  {@code mvn -pl hitorro-dms-spring-boot spring-boot:run} or
 *  {@code java -jar hitorro-dms-spring-boot-*-app.jar}. */
@SpringBootApplication
public class DmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DmsApplication.class, args);
    }
}
