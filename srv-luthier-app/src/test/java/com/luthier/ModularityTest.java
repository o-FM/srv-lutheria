package com.luthier;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Quebra o build se alguma fronteira entre módulos for violada (acesso a pacote interno,
 * dependência não declarada em allowedDependencies ou ciclo). Também gera a documentação
 * (diagramas C4/PlantUML) em target/spring-modulith-docs.
 */
class ModularityTest {

  static final ApplicationModules MODULES = ApplicationModules.of(Application.class);

  @Test
  void verifiesModularStructure() {
    MODULES.verify();
  }

  @Test
  void writesDocumentation() {
    new Documenter(MODULES).writeDocumentation();
  }
}
