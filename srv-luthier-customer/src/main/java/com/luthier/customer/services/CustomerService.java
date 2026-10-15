package com.luthier.customer.services;

import com.luthier.authorization.Access;
import com.luthier.customer.domain.records.CustomerRequest;
import com.luthier.customer.domain.records.CustomerResponse;
import com.luthier.customer.domain.records.InstrumentRequest;
import com.luthier.customer.domain.records.InstrumentResponse;
import com.luthier.web.PageResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;

/** Cadastro de clientes e instrumentos da luthieria (tenant) do usuário logado. */
public interface CustomerService {

  @PreAuthorize(Access.CUSTOMER_DESK)
  CustomerResponse create(CustomerRequest request);

  @PreAuthorize(Access.CUSTOMER_DESK)
  PageResponse<CustomerResponse> search(String query, Pageable pageable);

  @PreAuthorize(Access.CUSTOMER_DESK)
  CustomerResponse get(UUID id);

  @PreAuthorize(Access.CUSTOMER_DESK)
  CustomerResponse update(UUID id, CustomerRequest request);

  /** Exclusão lógica: o histórico de OS continua apontando para o cliente. */
  @PreAuthorize(Access.ADMIN)
  void deactivate(UUID id);

  @PreAuthorize(Access.CUSTOMER_DESK)
  InstrumentResponse addInstrument(UUID customerId, InstrumentRequest request);

  @PreAuthorize(Access.CUSTOMER_DESK)
  List<InstrumentResponse> listInstruments(UUID customerId);

  @PreAuthorize(Access.CUSTOMER_DESK)
  InstrumentResponse updateInstrument(UUID customerId, UUID instrumentId, InstrumentRequest request);
}
