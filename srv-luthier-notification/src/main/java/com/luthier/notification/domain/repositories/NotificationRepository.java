package com.luthier.notification.domain.repositories;

import com.luthier.notification.domain.entities.NotificationEntity;
import com.luthier.notification.domain.enums.Channel;
import com.luthier.notification.domain.enums.DeliveryStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

  java.util.List<NotificationEntity> findAllByCustomerId(UUID customerId);

  /** Retenção: roda sem tenant (job de plataforma), apaga de todas as luthierias. */
  @org.springframework.data.jpa.repository.Modifying
  @Query("delete from NotificationEntity n where n.createdAt < :cutoff")
  int deleteOlderThan(@Param("cutoff") java.time.Instant cutoff);

  @Query("""
      select n from NotificationEntity n
      where (:channel is null or n.channel = :channel)
        and (:status is null or n.status = :status)
      order by n.createdAt desc
      """)
  Page<NotificationEntity> search(@Param("channel") Channel channel,
      @Param("status") DeliveryStatus status, Pageable pageable);
}
