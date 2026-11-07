package com.luthier;

import static com.luthier.support.ApiClient.auth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Regressão do SEC-02 (docs/auditoria.md): o login era {@code @Transactional}, então segurava uma
 * conexão do pool durante o Argon2 (lento de propósito) e ainda pedia uma segunda para gravar a
 * auditoria em {@code REQUIRES_NEW}. Com N logins simultâneos e N ≥ tamanho do pool, cada thread
 * segurava uma conexão e esperava outra que ninguém devolvia, até o timeout do Hikari.
 *
 * O teste usa um pool de 2 conexões (o mínimo para o Flyway subir) e dois logins simultâneos. Uma
 * barreira dentro do {@code PasswordEncoder.matches} garante que as duas threads estejam no Argon2
 * ao mesmo tempo, o que torna o deadlock determinístico. O cadastro da luthieria, feito antes e
 * sozinho, cabe nas 2 conexões.
 */
@SpringBootTest(properties = {
    "app.security.rate-limit.login.capacity=1000",
    "spring.datasource.hikari.maximum-pool-size=2",
    // o deadlock vira erro em 2 s em vez de travar o teste pelos 30 s padrão
    "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
class LoginConnectionPoolTest {

  private static final int CONCURRENT_LOGINS = 2;
  private static final String PASSWORD = "SenhaForte@2026";

  @Autowired
  MockMvc mvc;

  private String email;
  private String ownerToken;

  @BeforeEach
  void registerWorkshop() throws Exception {
    email = "pool-" + System.nanoTime() + "@teste.com";
    var body = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"workshop":{"name":"Oficina Pool"},
                 "owner":{"name":"Dono","email":"%s","password":"%s"}}"""
                .formatted(email, PASSWORD)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    ownerToken = JsonPath.read(body, "$.accessToken");
    PasswordCheckProbe.TRANSACTION_ACTIVE.clear();
  }

  @AfterEach
  void resetProbe() {
    PasswordCheckProbe.TRANSACTION_ACTIVE.clear();
    PasswordCheckProbe.barrier = null;
  }

  @Test
  void concurrentValidLoginsDoNotExhaustThePoolAndAreAudited() throws Exception {
    assertThat(loginConcurrently(PASSWORD)).containsOnly(200);
    assertThat(auditCount("LOGIN_SUCCESS")).isEqualTo(CONCURRENT_LOGINS);
  }

  @Test
  void concurrentFailedLoginsAreAuditedWithoutExhaustingThePool() throws Exception {
    assertThat(loginConcurrently("senha-errada-123")).containsOnly(401);
    assertThat(auditCount("LOGIN_FAILED")).isEqualTo(CONCURRENT_LOGINS);
  }

  @Test
  void passwordIsCheckedOutsideATransaction() {
    assertThat(login(PASSWORD)).isEqualTo(200);

    assertThat(PasswordCheckProbe.TRANSACTION_ACTIVE)
        .as("transação ativa durante PasswordEncoder.matches (Argon2)")
        .isNotEmpty()
        .doesNotContain(true);
  }

  private List<Integer> loginConcurrently(String password) {
    PasswordCheckProbe.barrier = new CyclicBarrier(CONCURRENT_LOGINS);
    // pool próprio: o commonPool pode ter paralelismo 1 numa máquina de CI com 1 CPU
    var executor = Executors.newFixedThreadPool(CONCURRENT_LOGINS);
    try {
      var calls = IntStream.range(0, CONCURRENT_LOGINS)
          .mapToObj(i -> CompletableFuture.supplyAsync(() -> login(password), executor))
          .toList();
      return calls.stream().map(c -> c.orTimeout(30, TimeUnit.SECONDS).join()).toList();
    } finally {
      executor.shutdownNow();
    }
  }

  private int login(String password) {
    try {
      return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
              .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
          .andReturn().getResponse().getStatus();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private int auditCount(String action) throws Exception {
    var body = mvc.perform(auth(get("/audit-logs"), ownerToken))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    List<?> matches = JsonPath.read(body, "$.content[?(@.action == '%s')]".formatted(action));
    return matches.size();
  }

  /**
   * Embrulha o PasswordEncoder: anota se havia transação aberta em cada matches() e, quando há
   * barreira, segura a thread até todas as chamadas concorrentes chegarem ao Argon2.
   */
  @TestConfiguration
  static class PasswordCheckProbe {

    static final List<Boolean> TRANSACTION_ACTIVE = new CopyOnWriteArrayList<>();
    static volatile CyclicBarrier barrier;

    @Bean
    static BeanPostProcessor passwordCheckProbe() {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
          if (!(bean instanceof PasswordEncoder delegate)) return bean;
          return new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
              return delegate.encode(raw);
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
              TRANSACTION_ACTIVE.add(TransactionSynchronizationManager.isActualTransactionActive());
              awaitOthers();
              return delegate.matches(raw, encoded);
            }

            @Override
            public boolean upgradeEncoding(String encoded) {
              return delegate.upgradeEncoding(encoded);
            }
          };
        }
      };
    }

    private static void awaitOthers() {
      var current = barrier;
      if (current == null) return;
      try {
        current.await(10, TimeUnit.SECONDS);
      } catch (Exception e) {
        throw new IllegalStateException("os logins concorrentes não chegaram juntos ao Argon2", e);
      }
    }
  }
}
