package com.luthier.notification.services;

/** Porta de saída de e-mail (SMTP em produção, log em desenvolvimento). */
public interface EmailSender {

  /** @return false quando não há SMTP configurado (nada foi enviado). */
  boolean send(String to, String subject, String body);
}
