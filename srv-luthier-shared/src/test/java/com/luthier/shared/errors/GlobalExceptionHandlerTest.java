package com.luthier.shared.errors;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SH-02 (docs/auditoria.md): uma constraint do banco violada (em geral, duas requisições que
 * passaram juntas pela checagem "já existe?") não pode virar 500. A resposta nunca expõe a
 * mensagem do driver nem o nome da constraint.
 */
class GlobalExceptionHandlerTest {

  private static final String DRIVER_DETAIL = "uk_user_email (email)=(vitima@x.com)";

  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingController())
      .setControllerAdvice(new GlobalExceptionHandler())
      .build();

  @Test
  void uniqueViolationIsAConflict() throws Exception {
    mvc.perform(get("/unique"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail")
            .value("Registro duplicado ou em conflito. Recarregue e tente novamente."))
        .andExpect(content().string(not(containsString("uk_user_email"))))
        .andExpect(content().string(not(containsString("vitima@x.com"))));
  }

  @Test
  void foreignKeyViolationIsAConflict() throws Exception {
    mvc.perform(get("/foreign-key")).andExpect(status().isConflict());
  }

  @Test
  void valueTooLongIsABadRequest() throws Exception {
    mvc.perform(get("/too-long"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(not(containsString("vitima@x.com"))));
  }

  @RestController
  static class FailingController {

    @GetMapping("/unique")
    String unique() {
      throw violation("23505");
    }

    @GetMapping("/foreign-key")
    String foreignKey() {
      throw violation("23503");
    }

    @GetMapping("/too-long")
    String tooLong() {
      throw violation("22001");
    }

    private static DataIntegrityViolationException violation(String sqlState) {
      return new DataIntegrityViolationException("could not execute statement",
          new SQLException(DRIVER_DETAIL, sqlState));
    }
  }
}
