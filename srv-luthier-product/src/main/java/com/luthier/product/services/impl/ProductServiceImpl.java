package com.luthier.product.services.impl;

import com.luthier.shared.errors.ConflictException;
import com.luthier.shared.errors.NotFoundException;
import com.luthier.product.domain.entities.ProductEntity;
import com.luthier.product.domain.entities.ProductEntity.ProductData;
import com.luthier.product.domain.enums.MovementType;
import com.luthier.product.domain.enums.ProductCategory;
import com.luthier.product.domain.records.ProductRequest;
import com.luthier.product.domain.records.ProductResponse;
import com.luthier.product.domain.records.StockMovementRequest;
import com.luthier.product.domain.records.StockMovementResponse;
import com.luthier.product.domain.repositories.ProductRepository;
import com.luthier.product.domain.repositories.StockMovementRepository;
import com.luthier.product.services.ProductService;
import com.luthier.shared.web.PageResponse;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Isolamento por luthieria feito pelo Hibernate (@TenantId nas entidades). */
@Service
@Transactional
public class ProductServiceImpl implements ProductService {

  private final ProductRepository productRepository;
  private final StockMovementRepository movementRepository;
  private final StockLedger stockLedger;

  public ProductServiceImpl(ProductRepository productRepository,
      StockMovementRepository movementRepository, StockLedger stockLedger) {
    this.productRepository = productRepository;
    this.movementRepository = movementRepository;
    this.stockLedger = stockLedger;
  }

  @Override
  public ProductResponse create(ProductRequest request, String user) {
    var data = toData(request);
    if (productRepository.existsBySku(data.sku())) {
      throw new ConflictException("SKU already registered");
    }
    if (data.barcode() != null && productRepository.existsByBarcode(data.barcode())) {
      throw new ConflictException("Barcode already registered");
    }

    var product = productRepository.save(ProductEntity.newProduct(data));

    var initial = request.initialQuantity();
    if (initial != null && initial > 0) {
      stockLedger.apply(product, MovementType.ENTRADA, initial, "Saldo inicial", null, user);
    }
    return ProductResponse.from(product);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<ProductResponse> search(String text, ProductCategory category,
      boolean lowStock, Pageable pageable) {
    var q = text == null ? "" : text.strip();
    return PageResponse.of(productRepository.search(q, category, lowStock, pageable),
        ProductResponse::from);
  }

  @Override
  @Transactional(readOnly = true)
  public ProductResponse get(UUID id) {
    return ProductResponse.from(find(id));
  }

  @Override
  @Transactional(readOnly = true)
  public ProductResponse getByBarcode(String barcode) {
    return productRepository.findByBarcodeAndActiveTrue(barcode.strip())
        .map(ProductResponse::from)
        .orElseThrow(() -> new NotFoundException("Product not found!"));
  }

  @Override
  public ProductResponse update(UUID id, ProductRequest request) {
    var product = find(id);
    var data = toData(request);
    if (productRepository.existsBySkuAndIdNot(data.sku(), id)) {
      throw new ConflictException("SKU already registered");
    }
    if (data.barcode() != null && productRepository.existsByBarcodeAndIdNot(data.barcode(), id)) {
      throw new ConflictException("Barcode already registered");
    }
    product.update(data);
    return ProductResponse.from(product);
  }

  @Override
  public void deactivate(UUID id) {
    find(id).deactivate();
  }

  @Override
  public StockMovementResponse move(UUID productId, StockMovementRequest request, String user) {
    var product = find(productId);
    var movement = stockLedger.apply(product, request.type(), request.quantity(), request.reason().strip(),
        request.orderId(), user);
    return StockMovementResponse.from(movement);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<StockMovementResponse> movements(UUID productId, Pageable pageable) {
    find(productId);
    return PageResponse.of(
        movementRepository.findAllByProductIdOrderByCreatedAtDesc(productId, pageable),
        StockMovementResponse::from);
  }

  private ProductEntity find(UUID id) {
    return productRepository.findByIdAndActiveTrue(id)
        .orElseThrow(() -> new NotFoundException("Product not found!"));
  }

  private static ProductData toData(ProductRequest r) {
    return new ProductData(r.name().strip(), r.category(), blankToNull(r.brand()),
        r.sku().strip().toUpperCase(Locale.ROOT), blankToNull(r.barcode()), r.unit(),
        r.costPrice(), r.salePrice(), r.minQuantity(), blankToNull(r.location()));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
