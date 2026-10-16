package com.luthier.customer.controllers;

import com.luthier.customer.domain.records.InstrumentResponse;
import com.luthier.customer.services.CustomerService;
import com.luthier.shared.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Listagem de todos os instrumentos da luthieria (o cadastro fica em /customers/{id}/instruments). */
@RestController
@RequestMapping("/instruments")
public class InstrumentController {

  private final CustomerService customerService;

  public InstrumentController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @GetMapping
  public PageResponse<InstrumentResponse> list(@RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "100") int size) {
    return customerService.listAllInstruments(
        PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100)));
  }
}
