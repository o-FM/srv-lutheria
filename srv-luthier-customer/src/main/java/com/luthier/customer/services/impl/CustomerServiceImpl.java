package com.luthier.customer.services.impl;

import com.luthier.customer.CustomerQueries;
import com.luthier.customer.CustomerAnonymized;
import com.luthier.customer.CustomerRegistered;
import com.luthier.customer.domain.entities.CustomerEntity;
import com.luthier.customer.domain.entities.InstrumentEntity;
import com.luthier.customer.domain.entities.InstrumentEntity.InstrumentData;
import com.luthier.customer.domain.records.CustomerRequest;
import com.luthier.customer.domain.records.CustomerResponse;
import com.luthier.customer.domain.records.InstrumentRequest;
import com.luthier.customer.domain.records.InstrumentResponse;
import com.luthier.customer.domain.repositories.CustomerRepository;
import com.luthier.customer.domain.repositories.InstrumentRepository;
import com.luthier.customer.services.CustomerService;
import com.luthier.shared.audit.AuditEvent;
import com.luthier.shared.errors.ConflictException;
import com.luthier.shared.errors.NotFoundException;
import com.luthier.shared.tenancy.TenantContext;
import com.luthier.shared.validation.Cpf;
import com.luthier.shared.validation.Phone;
import com.luthier.shared.web.PageResponse;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Não há filtro manual por tenant aqui: CustomerEntity/InstrumentEntity têm @TenantId, então o
 * Hibernate restringe toda consulta à luthieria do token e preenche o tenant no INSERT.
 * Registro de outra luthieria simplesmente "não existe" (404).
 */
@Service
@Transactional
public class CustomerServiceImpl implements CustomerService, CustomerQueries {

  private final CustomerRepository customerRepository;
  private final InstrumentRepository instrumentRepository;
  private final ApplicationEventPublisher events;

  public CustomerServiceImpl(CustomerRepository customerRepository,
      InstrumentRepository instrumentRepository, ApplicationEventPublisher events) {
    this.customerRepository = customerRepository;
    this.instrumentRepository = instrumentRepository;
    this.events = events;
  }

  @Override
  public CustomerResponse create(CustomerRequest request) {
    var cpf = Cpf.digits(request.cpf());
    if (cpf != null && customerRepository.existsByCpf(cpf)) {
      throw new ConflictException("CPF already registered");
    }

    var customer = customerRepository.save(CustomerEntity.newCustomer(
        request.name().strip(),
        Phone.digits(request.phone()),
        normalizeEmail(request.email()),
        cpf,
        blankToNull(request.address()),
        blankToNull(request.notes())));

    events.publishEvent(
        new CustomerRegistered(TenantContext.require(), customer.getId(), customer.getName()));
    return CustomerResponse.from(customer);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<CustomerResponse> search(String query, Pageable pageable) {
    if (query == null || query.isBlank()) {
      return PageResponse.of(customerRepository.findAllByActiveTrue(pageable),
          CustomerResponse::from);
    }
    var text = query.strip();
    var digits = text.replaceAll("\\D", "");
    return PageResponse.of(customerRepository.search(text, digits, pageable),
        CustomerResponse::from);
  }

  @Override
  @Transactional(readOnly = true)
  public CustomerResponse get(UUID id) {
    var customer = find(id);
    return CustomerResponse.from(customer, instruments(id));
  }

  @Override
  public CustomerResponse update(UUID id, CustomerRequest request) {
    var customer = find(id);
    var cpf = Cpf.digits(request.cpf());
    if (cpf != null && customerRepository.existsByCpfAndIdNot(cpf, id)) {
      throw new ConflictException("CPF already registered");
    }

    customer.update(
        request.name().strip(),
        Phone.digits(request.phone()),
        normalizeEmail(request.email()),
        cpf,
        blankToNull(request.address()),
        blankToNull(request.notes()));
    return CustomerResponse.from(customer, instruments(id));
  }

  @Override
  public void deactivate(UUID id) {
    find(id).deactivate();
    events.publishEvent(AuditEvent.of("CUSTOMER_DEACTIVATED", "customer", id, null));
  }

  @Override
  public void anonymize(UUID id) {
    var customer = customerRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Customer not found!"));
    customer.anonymize();
    events.publishEvent(new CustomerAnonymized(TenantContext.require(), id));
    events.publishEvent(AuditEvent.of("CUSTOMER_ANONYMIZED", "customer", id, null));
  }

  @Override
  public InstrumentResponse addInstrument(UUID customerId, InstrumentRequest request) {
    find(customerId);
    var instrument = instrumentRepository.save(
        InstrumentEntity.newInstrument(customerId, toData(request)));
    return InstrumentResponse.from(instrument);
  }

  @Override
  @Transactional(readOnly = true)
  public List<InstrumentResponse> listInstruments(UUID customerId) {
    find(customerId);
    return instruments(customerId);
  }

  @Override
  public InstrumentResponse updateInstrument(UUID customerId, UUID instrumentId,
      InstrumentRequest request) {
    var instrument = instrumentRepository.findByIdAndCustomerId(instrumentId, customerId)
        .orElseThrow(() -> new NotFoundException("Instrument not found!"));
    instrument.update(toData(request));
    return InstrumentResponse.from(instrument);
  }

  // ---- CustomerQueries (API pública para outros módulos) ----

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID customerId) {
    return customerRepository.existsByIdAndActiveTrue(customerId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CustomerSummary> findCustomer(UUID customerId) {
    return customerRepository.findByIdAndActiveTrue(customerId)
        .map(c -> new CustomerSummary(c.getId(), c.getName(), c.getPhone(), c.getEmail()));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<InstrumentSummary> findInstrument(UUID instrumentId) {
    return instrumentRepository.findById(instrumentId)
        .map(i -> new InstrumentSummary(i.getId(), i.getCustomerId(), i.getType().getValue(),
            i.getBrand(), i.getModel()));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CustomerExport> exportData(UUID customerId) {
    return customerRepository.findById(customerId).map(c -> new CustomerExport(c.getId(),
        c.getName(), c.getPhone(), c.getEmail(), c.getCpf(), c.getAddress(), c.getNotes(),
        c.isActive(), c.getCreatedAt(),
        instrumentRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId).stream()
            .map(i -> new InstrumentExport(i.getId(), i.getType().getValue(), i.getBrand(),
                i.getModel(), i.getSerialNumber(), i.getYear(), i.getFinishColor(),
                i.getConditionNotes()))
            .toList()));
  }

  @Override
  @Transactional(readOnly = true)
  public long countActive() {
    return customerRepository.countByActiveTrue();
  }

  // ---- helpers ----

  private CustomerEntity find(UUID id) {
    return customerRepository.findByIdAndActiveTrue(id)
        .orElseThrow(() -> new NotFoundException("Customer not found!"));
  }

  private List<InstrumentResponse> instruments(UUID customerId) {
    return instrumentRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId).stream()
        .map(InstrumentResponse::from)
        .toList();
  }

  private static InstrumentData toData(InstrumentRequest r) {
    return new InstrumentData(r.type(), r.brand().strip(), r.model().strip(),
        blankToNull(r.serialNumber()), blankToNull(r.year()), blankToNull(r.finishColor()),
        blankToNull(r.stringsGauge()), blankToNull(r.tuningPreference()),
        blankToNull(r.actionHeight12thFret()), blankToNull(r.conditionNotes()));
  }

  private static String normalizeEmail(String email) {
    var e = blankToNull(email);
    return e == null ? null : e.toLowerCase(Locale.ROOT);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
