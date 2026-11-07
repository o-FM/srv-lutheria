package com.luthier;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/**
 * APP-01 (docs/auditoria.md): a configuração base não pode ativar perfil nenhum por conta
 * própria. Com "spring.profiles.default: dev", um java -jar sem SPRING_PROFILES_ACTIVE subia com
 * o JWT_SECRET de dev, que está no repositório, e qualquer um forjava um token de ADMIN.
 *
 * Os testes ativam o dev por src/test/resources/config/application.yaml, que o Spring Boot
 * carrega JUNTO com o application.yaml principal. Este teste lê só o principal.
 */
class ProfileDefaultsTest {

  @Test
  void baseConfigurationDoesNotActivateAnyProfile() throws IOException {
    var documents = new YamlPropertySourceLoader()
        .load("base", new ClassPathResource("application.yaml"));

    assertThat(documents).isNotEmpty().allSatisfy(document -> {
      assertThat(document.getProperty("spring.profiles.default")).isNull();
      assertThat(document.getProperty("spring.profiles.active")).isNull();
    });
  }
}
