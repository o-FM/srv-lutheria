package com.luthier.product.services;

import com.luthier.authorization.Access;
import com.luthier.product.domain.enums.ProductCategory;
import com.luthier.product.domain.records.ProductRequest;
import com.luthier.product.domain.records.ProductResponse;
import com.luthier.product.domain.records.StockMovementRequest;
import com.luthier.product.domain.records.StockMovementResponse;
import com.luthier.web.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;

/** Cadastro de produtos e controle de estoque da luthieria (tenant) do usuário logado. */
public interface ProductService {

  @PreAuthorize(Access.STOCK_WRITE)
  ProductResponse create(ProductRequest request, String user);

  @PreAuthorize(Access.ANY_STAFF)
  PageResponse<ProductResponse> search(String text, ProductCategory category, boolean lowStock,
      Pageable pageable);

  @PreAuthorize(Access.ANY_STAFF)
  ProductResponse get(UUID id);

  /** Leitor de código de barras/QR do StockModule. */
  @PreAuthorize(Access.ANY_STAFF)
  ProductResponse getByBarcode(String barcode);

  @PreAuthorize(Access.STOCK_WRITE)
  ProductResponse update(UUID id, ProductRequest request);

  @PreAuthorize(Access.STOCK_WRITE)
  void deactivate(UUID id);

  @PreAuthorize(Access.STOCK_WRITE)
  StockMovementResponse move(UUID productId, StockMovementRequest request, String user);

  @PreAuthorize(Access.ANY_STAFF)
  PageResponse<StockMovementResponse> movements(UUID productId, Pageable pageable);
}
