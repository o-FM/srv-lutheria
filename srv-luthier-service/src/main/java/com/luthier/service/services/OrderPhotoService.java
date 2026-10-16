package com.luthier.service.services;

import com.luthier.service.domain.records.AddPhotoRequest;
import com.luthier.service.domain.records.PhotoContent;
import com.luthier.service.domain.records.PhotoResponse;
import com.luthier.shared.authorization.Access;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/** Fotos das OS da luthieria do usuário logado (balcão e bancada). */
public interface OrderPhotoService {

  @PreAuthorize(Access.CUSTOMER_DESK)
  PhotoResponse add(UUID orderId, AddPhotoRequest request, String user);

  @PreAuthorize(Access.CUSTOMER_DESK)
  List<PhotoResponse> list(UUID orderId);

  @PreAuthorize(Access.CUSTOMER_DESK)
  PhotoContent content(UUID orderId, UUID photoId);

  @PreAuthorize(Access.CUSTOMER_DESK)
  void delete(UUID orderId, UUID photoId);
}
