package com.luthier.service.services.impl;

import com.luthier.service.domain.entities.OrderPhotoEntity;
import com.luthier.service.domain.records.AddPhotoRequest;
import com.luthier.service.domain.records.PhotoContent;
import com.luthier.service.domain.records.PhotoResponse;
import com.luthier.service.domain.repositories.OrderPhotoRepository;
import com.luthier.service.domain.repositories.ServiceOrderRepository;
import com.luthier.service.services.OrderPhotoService;
import com.luthier.shared.errors.BadRequestException;
import com.luthier.shared.errors.ConflictException;
import com.luthier.shared.errors.NotFoundException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderPhotoServiceImpl implements OrderPhotoService {

  static final int MAX_BYTES = 3 * 1024 * 1024;
  static final int MAX_PER_ORDER = 40;

  private static final Pattern DATA_URL =
      Pattern.compile("^data:(image/[a-z+.-]+);base64,(.+)$", Pattern.DOTALL);

  private final OrderPhotoRepository photos;
  private final ServiceOrderRepository orders;

  public OrderPhotoServiceImpl(OrderPhotoRepository photos, ServiceOrderRepository orders) {
    this.photos = photos;
    this.orders = orders;
  }

  @Override
  public PhotoResponse add(UUID orderId, AddPhotoRequest request, String user) {
    var order = orders.findById(orderId)
        .orElseThrow(() -> new NotFoundException("Service order not found!"));
    if (photos.countByOrderId(orderId) >= MAX_PER_ORDER) {
      throw new ConflictException("Limite de " + MAX_PER_ORDER + " fotos por OS atingido");
    }

    var data = decode(request.dataUrl());
    var caption = request.caption() == null || request.caption().isBlank()
        ? null : request.caption().strip();
    var saved = photos.saveAndFlush(OrderPhotoEntity.of(orderId, request.tag(), caption,
        detectContentType(data), data, user));

    order.recordPhoto(request.tag().getValue(), caption, user);
    orders.saveAndFlush(order);

    return new PhotoResponse(saved.getId(), saved.getTag(), saved.getCaption(),
        saved.getCreatedAt(), saved.getUser());
  }

  @Override
  @Transactional(readOnly = true)
  public List<PhotoResponse> list(UUID orderId) {
    requireOrder(orderId);
    return photos.listByOrder(orderId);
  }

  @Override
  @Transactional(readOnly = true)
  public PhotoContent content(UUID orderId, UUID photoId) {
    var photo = photos.findByIdAndOrderId(photoId, orderId)
        .orElseThrow(() -> new NotFoundException("Photo not found!"));
    return new PhotoContent(photo.getContentType(), photo.getData());
  }

  @Override
  public void delete(UUID orderId, UUID photoId) {
    var photo = photos.findByIdAndOrderId(photoId, orderId)
        .orElseThrow(() -> new NotFoundException("Photo not found!"));
    photos.delete(photo);
  }

  private void requireOrder(UUID orderId) {
    if (!orders.existsById(orderId)) {
      throw new NotFoundException("Service order not found!");
    }
  }

  static byte[] decode(String dataUrl) {
    var matcher = DATA_URL.matcher(dataUrl.strip());
    if (!matcher.matches()) {
      throw new BadRequestException("Envie a foto como data URL base64 (data:image/...)");
    }
    byte[] data;
    try {
      data = Base64.getMimeDecoder().decode(matcher.group(2));
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Foto com base64 inválido");
    }
    if (data.length == 0 || data.length > MAX_BYTES) {
      throw new BadRequestException("A foto deve ter até 3 MB");
    }
    return data;
  }

  /**
   * O tipo vem dos bytes, não do que o cliente declarou: só JPEG, PNG e WebP são aceitos e
   * servidos (evita guardar HTML/SVG disfarçado de imagem).
   */
  static String detectContentType(byte[] d) {
    if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
      return "image/jpeg";
    }
    if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
      return "image/png";
    }
    if (d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
        && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
      return "image/webp";
    }
    throw new BadRequestException("Formato de imagem não suportado (use JPEG, PNG ou WebP)");
  }
}
