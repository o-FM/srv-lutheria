package com.luthier.product.controllers;

import com.luthier.product.domain.enums.ProductCategory;
import com.luthier.product.domain.records.ProductRequest;
import com.luthier.product.domain.records.ProductResponse;
import com.luthier.product.domain.records.StockMovementRequest;
import com.luthier.product.domain.records.StockMovementResponse;
import com.luthier.product.services.ProductService;
import com.luthier.web.PageResponse;
import jakarta.validation.Valid;
import java.security.Principal;
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
@RequestMapping("/products")
@Validated
public class ProductController {

  private static final int MAX_PAGE_SIZE = 100;

  private final ProductService productService;

  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ProductResponse create(@Valid @RequestBody ProductRequest request, Principal principal) {
    return productService.create(request, principal.getName());
  }

  @GetMapping
  public PageResponse<ProductResponse> search(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "false") boolean lowStock,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
        Sort.by("name"));
    return productService.search(search,
        category == null || category.isBlank() ? null : ProductCategory.fromValue(category),
        lowStock, pageable);
  }

  @GetMapping("/{id}")
  public ProductResponse get(@PathVariable UUID id) {
    return productService.get(id);
  }

  @GetMapping("/barcode/{code}")
  public ProductResponse getByBarcode(@PathVariable String code) {
    return productService.getByBarcode(code);
  }

  @PutMapping("/{id}")
  public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
    return productService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable UUID id) {
    productService.deactivate(id);
  }

  @PostMapping("/{id}/movements")
  @ResponseStatus(HttpStatus.CREATED)
  public StockMovementResponse move(@PathVariable UUID id,
      @Valid @RequestBody StockMovementRequest request, Principal principal) {
    return productService.move(id, request, principal.getName());
  }

  @GetMapping("/{id}/movements")
  public PageResponse<StockMovementResponse> movements(@PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return productService.movements(id,
        PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
  }
}
