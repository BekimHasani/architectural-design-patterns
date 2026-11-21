package com.designpatterns.level08.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots with the REAL application.yml, scheduled job included; only the database is in-memory. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:startup;DB_CLOSE_DELAY=-1")
class ApplicationStartsTest {

    @Autowired
    ApplicationContext context;

    @Test
    void startsWithTheDefaultConfiguration() {
        assertThat(context.containsBean("reconciliationJob")).isTrue();
    }
}
