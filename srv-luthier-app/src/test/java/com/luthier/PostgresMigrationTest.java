package com.luthier;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Sobe a aplicação contra um PostgreSQL real: valida que as migrations Flyway rodam no banco de
 * produção e que o schema bate com as entidades (ddl-auto=validate). Pulado sem Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Import(PostgresMigrationTest.PostgresConfig.class)
class PostgresMigrationTest {

  @TestConfiguration(proxyBeanMethods = false)
  static class PostgresConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
      return new PostgreSQLContainer("postgres:17-alpine");
    }
  }

  @Autowired
  DataSource dataSource;

  @Test
  void migrationsApplyOnPostgres() {
    var count = new JdbcTemplate(dataSource).queryForObject(
        "select count(*) from flyway_schema_history where success = true", Integer.class);
    assertThat(count).isGreaterThanOrEqualTo(5);
  }
}
