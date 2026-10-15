package com.luthier.customer.controllers;

import com.luthier.customer.domain.records.CustomerRequest;
import com.luthier.customer.domain.records.CustomerResponse;
import com.luthier.customer.domain.records.InstrumentRequest;
import com.luthier.customer.domain.records.InstrumentResponse;
import com.luthier.customer.services.CustomerService;
import com.luthier.web.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
@Validated
public class CustomerController {

  private static final int MAX_PAGE_SIZE = 100;

  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
    return customerService.create(request);
  }

  /** ?search= busca por nome, telefone ou CPF (como a barra de busca do ClientsModule). */
  @GetMapping
  public PageResponse<CustomerResponse> search(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
        Sort.by("name"));
    return customerService.search(search, pageable);
  }

  @GetMapping("/{id}")
  public CustomerResponse get(@PathVariable UUID id) {
    return customerService.get(id);
  }

  @PutMapping("/{id}")
  public CustomerResponse update(@PathVariable UUID id,
      @Valid @RequestBody CustomerRequest request) {
    return customerService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable UUID id) {
    customerService.deactivate(id);
  }

  @PostMapping("/{id}/instruments")
  @ResponseStatus(HttpStatus.CREATED)
  public InstrumentResponse addInstrument(@PathVariable UUID id,
      @Valid @RequestBody InstrumentRequest request) {
    return customerService.addInstrument(id, request);
  }

  @GetMapping("/{id}/instruments")
  public List<InstrumentResponse> listInstruments(@PathVariable UUID id) {
    return customerService.listInstruments(id);
  }

  @PutMapping("/{id}/instruments/{instrumentId}")
  public InstrumentResponse updateInstrument(@PathVariable UUID id, @PathVariable UUID instrumentId,
      @Valid @RequestBody InstrumentRequest request) {
    return customerService.updateInstrument(id, instrumentId, request);
  }
}
