package com.luthier.service.domain.entities;

import com.luthier.service.domain.enums.PhotoTag;
import com.luthier.shared.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Foto da OS. Fica fora do agregado ServiceOrderEntity de propósito: carregar a OS não pode
 * trazer megabytes de imagem junto. Listagens usam projeção sem a coluna {@code data}.
 */
@Entity
@Immutable
@Table(name = "SERVICE_ORDER_PHOTO_TB")
public class OrderPhotoEntity extends TenantScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false, updatable = false)
  private UUID orderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  private PhotoTag tag;

  @Column(length = 200, updatable = false)
  private String caption;

  @Column(name = "content_type", nullable = false, length = 30, updatable = false)
  private String contentType;

  @Column(name = "size_bytes", nullable = false, updatable = false)
  private int sizeBytes;

  @Column(nullable = false, updatable = false)
  private byte[] data;

  @Column(name = "actor", nullable = false, length = 254, updatable = false)
  private String user;

  protected OrderPhotoEntity() {}

  public static OrderPhotoEntity of(UUID orderId, PhotoTag tag, String caption,
      String contentType, byte[] data, String user) {
    var p = new OrderPhotoEntity();
    p.orderId = orderId;
    p.tag = tag;
    p.caption = caption;
    p.contentType = contentType;
    p.sizeBytes = data.length;
    p.data = data;
    p.user = user;
    return p;
  }

  public UUID getId() { return id; }
  public UUID getOrderId() { return orderId; }
  public PhotoTag getTag() { return tag; }
  public String getCaption() { return caption; }
  public String getContentType() { return contentType; }
  public int getSizeBytes() { return sizeBytes; }
  public byte[] getData() { return data; }
  public String getUser() { return user; }
}
