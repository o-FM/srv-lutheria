package com.luthier.notification.domain.entities;

import com.luthier.notification.domain.enums.Channel;
import com.luthier.notification.domain.enums.DeliveryStatus;
import com.luthier.shared.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro de cada notificação (trilha do que foi comunicado ao cliente). Conteúdo com segredos
 * (ex.: link de redefinição de senha) NUNCA é gravado aqui — só um resumo.
 */
@Entity
@Table(name = "NOTIFICATION_TB", indexes =
    @Index(name = "idx_notification_tenant_created", columnList = "tenant_id, created_at"))
public class NotificationEntity extends TenantScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Channel channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private DeliveryStatus status;

  /** E-mail ou telefone (só dígitos) do destinatário; null para alertas internos. */
  @Column(length = 254)
  private String recipient;

  @Column(nullable = false, length = 160)
  private String subject;

  @Column(nullable = false, length = 2000)
  private String body;

  /** Link de ação (ex.: https://wa.me/55...?text=...). */
  @Column(name = "action_url", length = 2500)
  private String actionUrl;

  /** O que originou: "service_order", "product", "tenant"... + ID. */
  @Column(name = "related_type", length = 30)
  private String relatedType;

  @Column(name = "related_id")
  private UUID relatedId;

  /** Cliente destinatário, para atender pedidos de eliminação de dados (LGPD). */
  @Column(name = "customer_id")
  private UUID customerId;

  @Column(length = 500)
  private String error;

  @Column(name = "sent_at")
  private Instant sentAt;

  protected NotificationEntity() {}

  public static NotificationEntity of(Channel channel, String recipient, String subject,
      String body, String actionUrl, String relatedType, UUID relatedId) {
    var n = new NotificationEntity();
    n.channel = channel;
    n.recipient = recipient;
    n.subject = subject;
    n.body = body;
    n.actionUrl = actionUrl;
    n.relatedType = relatedType;
    n.relatedId = relatedId;
    n.status = DeliveryStatus.PENDING;
    return n;
  }

  public NotificationEntity forCustomer(UUID customerId) {
    this.customerId = customerId;
    return this;
  }

  /** LGPD: remove destinatário e conteúdo (que cita o nome); mantém só o fato de ter havido envio. */
  public void scrub() {
    this.recipient = null;
    this.body = "[conteúdo removido a pedido do titular]";
    this.actionUrl = null;
  }

  public void markSent() {
    this.status = DeliveryStatus.SENT;
    this.sentAt = Instant.now();
    this.error = null;
  }

  public void markFailed(String error) {
    this.status = DeliveryStatus.FAILED;
    this.error = error == null ? null : error.substring(0, Math.min(error.length(), 500));
  }

  public UUID getId() { return id; }
  public Channel getChannel() { return channel; }
  public DeliveryStatus getStatus() { return status; }
  public String getRecipient() { return recipient; }
  public String getSubject() { return subject; }
  public String getBody() { return body; }
  public String getActionUrl() { return actionUrl; }
  public String getRelatedType() { return relatedType; }
  public UUID getRelatedId() { return relatedId; }
  public UUID getCustomerId() { return customerId; }
  public String getError() { return error; }
  public Instant getSentAt() { return sentAt; }
}
