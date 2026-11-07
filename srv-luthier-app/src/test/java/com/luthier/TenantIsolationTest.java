package com.luthier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static com.luthier.RetentionJobFlowTest.tenantOf;

import com.luthier.customer.CustomerQueries;
import com.luthier.customer.domain.entities.CustomerEntity;
import com.luthier.customer.domain.repositories.CustomerRepository;
import com.luthier.shared.tenancy.TenantContext;
import com.luthier.support.ApiClient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Isolamento entre luthierias (SH-01 e APP-03 em docs/auditoria.md).
 *
 * SH-01: o código roda aqui na thread do teste, fora de requisição, como um listener assíncrono
 * ou um job que esqueceu o TenantContext.runAs. Sem tenant, nada de negócio é lido nem gravado.
 * A visão de todas as luthierias exige pedir explicitamente o escopo de plataforma.
 *
 * APP-03: o banco é a última defesa. Uma linha de negócio só pode apontar para um tenant que
 * existe em tenant_tb, o que barra o tenant ROOT (visão de plataforma) e qualquer UUID inventado.
 */
@SpringBootTest(properties = "app.security.rate-limit.login.capacity=1000")
@AutoConfigureMockMvc
class TenantIsolationTest {

  /** tenant_id nulo de propósito: login com e-mail desconhecido não tem luthieria. */
  private static final String TABLES_WITHOUT_TENANT_FK = "'audit_log_tb'";

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  MockMvc mvc;

  @Autowired
  CustomerQueries customers;

  @Autowired
  CustomerRepository customerRepository;

  @Autowired
  JwtEncoder jwtEncoder;

  private UUID workshopA;
  private UUID workshopB;
  private UUID customerOfA;

  @BeforeEach
  void twoWorkshopsAndACustomer() throws Exception {
    var api = new ApiClient(mvc);
    var tokenA = api.registerWorkshop("Isolamento A");
    workshopA = tenantOf(tokenA);
    workshopB = tenantOf(api.registerWorkshop("Isolamento B"));
    customerOfA = UUID.fromString(api.create(tokenA, "/customers",
        "{\"name\":\"Cliente A\",\"phone\":\"(11) 98765-4321\"}"));
  }

  @Test
  void withoutTenantBusinessDataIsInvisible() {
    assertThat(TenantContext.current()).isEmpty();

    assertThat(customers.exists(customerOfA)).isFalse();
    assertThat(customers.findCustomer(customerOfA)).isEmpty();
    assertThat(customers.countActive()).isZero();
  }

  @Test
  void eachWorkshopSeesOnlyItsOwnData() {
    assertThat(TenantContext.callAs(workshopA, () -> customers.exists(customerOfA))).isTrue();
    assertThat(TenantContext.callAs(workshopB, () -> customers.exists(customerOfA))).isFalse();
  }

  @Test
  void platformScopeSeesEveryWorkshop() {
    assertThat(TenantContext.callAsPlatform(() -> customers.exists(customerOfA))).isTrue();
  }

  @Test
  void withoutTenantBusinessWritesFailFast() {
    assertThatThrownBy(() -> customerRepository.save(orphanCustomer()))
        .rootCause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenant");
  }

  @Test
  void platformScopeCannotWriteBusinessData() {
    assertThatThrownBy(() -> TenantContext.callAsPlatform(
            () -> customerRepository.save(orphanCustomer())))
        .rootCause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenant");
  }

  /**
   * Um token assinado com tid = ROOT colocaria o próprio ROOT no contexto e ganharia a visão sem
   * filtro, contornando o escopo de plataforma. Para isso basta o segredo de assinatura, por
   * exemplo o de dev.
   */
  @ParameterizedTest(name = "tid = {0}")
  @ValueSource(strings = {
      "00000000-0000-0000-0000-000000000000",   // ROOT
      "00000000-0000-0000-0000-000000000001"})  // NO_TENANT
  void tokenWithAReservedTenantIdIsForbidden(String tid) throws Exception {
    var now = Instant.now();
    var claims = JwtClaimsSet.builder()
        .issuer("luthieria")
        .subject("forjado@teste.com")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .claim("uid", UUID.randomUUID().toString())
        .claim("tid", tid)
        .claim("roles", List.of("ADMIN"))
        .build();
    var token = jwtEncoder.encode(JwtEncoderParameters.from(
        JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

    mvc.perform(ApiClient.auth(get("/customers"), token)).andExpect(status().isForbidden());
  }

  private static CustomerEntity orphanCustomer() {
    return CustomerEntity.newCustomer("Órfão", "11987654321", null, null, null, null);
  }

  @AfterEach
  void noTenantLeaksToTheNextTest() {
    TenantContext.clear();
  }

  @Test
  void everyTableWithATenantColumnReferencesTheTenantTable() {
    // controle positivo: sem ele, um schema ou caixa de nome diferente faria a consulta abaixo
    // voltar vazia e o teste passar sem verificar nada
    assertThat(jdbc.queryForList("""
        select table_name from information_schema.columns
        where table_schema = 'public' and column_name = 'tenant_id'""", String.class))
        .contains("customer_tb", "service_order_tb", "notification_tb");

    var missing = jdbc.queryForList("""
        select c.table_name from information_schema.columns c
        where c.table_schema = 'public' and c.column_name = 'tenant_id'
          and c.table_name not in (%s)
          and c.table_name not in (
            select fk.table_name
            from information_schema.referential_constraints rc
            join information_schema.key_column_usage fk
              on fk.constraint_schema = rc.constraint_schema
             and fk.constraint_name = rc.constraint_name
            join information_schema.key_column_usage pk
              on pk.constraint_schema = rc.unique_constraint_schema
             and pk.constraint_name = rc.unique_constraint_name
            where fk.column_name = 'tenant_id' and pk.table_name = 'tenant_tb')
        order by c.table_name
        """.formatted(TABLES_WITHOUT_TENANT_FK), String.class);

    assertThat(missing).as("tabelas com tenant_id sem FK para tenant_tb").isEmpty();
  }

  @ParameterizedTest(name = "tenant_id = {0}")
  @ValueSource(strings = {
      "00000000-0000-0000-0000-000000000000",   // ROOT: visão de plataforma do Hibernate
      "7d1f6c3e-2b4a-4f0e-9c1d-5a6b7c8d9e0f"})  // UUID que não é de nenhuma luthieria
  void businessRowWithAnUnknownTenantIsRejected(String tenantId) {
    var now = Timestamp.from(Instant.now());

    assertThatThrownBy(() -> jdbc.update("""
            insert into customer_tb (id, tenant_id, name, phone, active, created_at, updated_at,
                                     version)
            values (?, ?, 'Órfão', '11987654321', true, ?, ?, 0)""",
        UUID.randomUUID(), UUID.fromString(tenantId), now, now))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
