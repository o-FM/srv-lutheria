package com.luthier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import com.luthier.support.ApiClient;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * NOT-03 (docs/auditoria.md): o expurgo de retenção é um job de plataforma. Ele roda fora de
 * requisição, sem tenant no contexto, e precisa apagar as notificações antigas de TODAS as
 * luthierias, sem tocar nas recentes.
 *
 * As asserções usam JdbcTemplate direto, que não passa pelo filtro de tenant do Hibernate.
 */
@SpringBootTest(properties = "app.security.rate-limit.login.capacity=1000")
@AutoConfigureMockMvc
class RetentionJobFlowTest {

  /** Além da retenção padrão de notificações (luthieria.retention.notification-days = 365). */
  private static final Duration EXPIRED_AGE = Duration.ofDays(400);

  @Autowired
  MockMvc mvc;

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  ApplicationContext context;

  @Test
  void notificationPurgeRemovesOldRowsOfEveryWorkshopAndKeepsRecentOnes() throws Exception {
    var api = new ApiClient(mvc);
    var oldA = tenantOf(api.registerWorkshop("Retenção A"));
    var oldB = tenantOf(api.registerWorkshop("Retenção B"));
    var recent = tenantOf(api.registerWorkshop("Retenção C"));

    // a notificação de boas-vindas é gravada por um listener assíncrono
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      assertThat(welcomeCount(oldA)).isEqualTo(1);
      assertThat(welcomeCount(oldB)).isEqualTo(1);
      assertThat(welcomeCount(recent)).isEqualTo(1);
    });
    expire(oldA);
    expire(oldB);

    // purge() é o método @Scheduled; o bean é package-private no módulo notification
    ReflectionTestUtils.invokeMethod(context.getBean("notificationRetentionJob"), "purge");

    assertThat(welcomeCount(oldA)).as("notificação antiga da luthieria A").isZero();
    assertThat(welcomeCount(oldB)).as("notificação antiga da luthieria B").isZero();
    assertThat(welcomeCount(recent)).as("notificação recente da luthieria C").isEqualTo(1);
  }

  private int welcomeCount(UUID tenantId) {
    return jdbc.queryForObject(
        "select count(*) from notification_tb where tenant_id = ? and related_type = 'tenant'",
        Integer.class, tenantId);
  }

  private void expire(UUID tenantId) {
    jdbc.update("update notification_tb set created_at = ? where tenant_id = ?",
        Timestamp.from(Instant.now().minus(EXPIRED_AGE)), tenantId);
  }

  /** Lê o claim "tid" do access token (sem validar a assinatura: o token é do próprio teste). */
  static UUID tenantOf(String accessToken) {
    var payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]),
        StandardCharsets.UTF_8);
    return UUID.fromString(JsonPath.read(payload, "$.tid"));
  }
}
