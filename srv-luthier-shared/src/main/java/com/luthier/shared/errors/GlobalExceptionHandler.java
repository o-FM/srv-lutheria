package com.luthier.shared.errors;

import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** SQLState classe 22 (data exception), ex.: 22001 = valor maior que a coluna. */
  private static final String DATA_EXCEPTION_CLASS = "22";

  /**
   * @Valid @RequestBody
   */
  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status,
      WebRequest request) {

    Map<String, String> errors = new LinkedHashMap<>();
    ex.getBindingResult().getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

    return ResponseEntity.badRequest().body(problem("Invalid Data", errors));
  }

  /**
   * @PathVariable / @RequestParam validados (method validation nativa do Spring).
   */
  @Override
  protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status,
      WebRequest request) {
    return ResponseEntity.badRequest().body(problem("Parametros inválidos", Map.of()));
  }

  /**
   * @Validated em services/componentes fora da camada web.
   */
  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
    Map<String, String> errors = new LinkedHashMap<>();
    ex.getConstraintViolations()
        .forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));

    return ResponseEntity.badRequest().body(problem("Dados inválidos", errors));
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage()));
  }

  @ExceptionHandler(BadRequestException.class)
  ResponseEntity<ProblemDetail> handleBadRequest(BadRequestException ex) {
    return ResponseEntity.badRequest()
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
  }

  /**
   * Lock otimista (@Version): outra pessoa alterou o mesmo registro ao mesmo tempo
   * (ex.: duas baixas de estoque simultâneas). O cliente deve recarregar e tentar de novo.
   */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "Registro alterado por outra pessoa. Recarregue e tente novamente."));
  }

  /**
   * Constraint do banco violada (unicidade, FK, CHECK): em geral, duas requisições passaram juntas
   * pela checagem "já existe?" (SH-02). Dado que não cabe na coluna (SQLState 22xxx) é 400. A
   * resposta nunca traz a mensagem do driver, que contém nome de constraint e valores.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
    var sqlState = NestedExceptionUtils.getMostSpecificCause(ex) instanceof SQLException sql
        ? sql.getSQLState() : null;
    // Só o SQLState: a mensagem do driver traz valores (e-mail, CPF) que não podem ir para o log
    log.warn("Violação de integridade no banco (SQLState {})", sqlState);
    if (sqlState != null && sqlState.startsWith(DATA_EXCEPTION_CLASS)) {
      return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(
          HttpStatus.BAD_REQUEST, "Dado inválido ou maior que o permitido."));
    }
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "Registro duplicado ou em conflito. Recarregue e tente novamente."));
  }

  @ExceptionHandler(ConflictException.class)
  ResponseEntity<ProblemDetail> handleConflict(ConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage()));
  }

  private static ProblemDetail problem(String message, Map<String, String> errors) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);

    if (!errors.isEmpty()) {
      problem.setProperty("errors", errors);
    }

    return problem;
  }
}
