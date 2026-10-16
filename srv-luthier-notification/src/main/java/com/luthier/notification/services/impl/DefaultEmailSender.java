package com.luthier.notification.services.impl;

import com.luthier.notification.services.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envia por SMTP quando spring.mail.host está configurado (o Boot cria o JavaMailSender);
 * senão apenas registra no log que o e-mail "seria enviado" — sem o corpo, que pode ter links
 * sensíveis.
 */
@Component
public class DefaultEmailSender implements EmailSender {

  private static final Logger log = LoggerFactory.getLogger(DefaultEmailSender.class);

  private final ObjectProvider<JavaMailSender> mailSender;
  private final String from;

  public DefaultEmailSender(ObjectProvider<JavaMailSender> mailSender,
      @Value("${luthieria.mail.from:Luthieria <nao-responda@luthieria.com.br>}") String from) {
    this.mailSender = mailSender;
    this.from = from;
  }

  @Override
  public boolean send(String to, String subject, String body) {
    var sender = mailSender.getIfAvailable();
    if (sender == null) {
      log.info("E-mail não enviado (SMTP não configurado): para={} assunto=\"{}\"",
          maskEmail(to), subject);
      return false;
    }
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject(subject);
    message.setText(body);
    sender.send(message);
    return true;
  }

  static String maskEmail(String email) {
    var at = email.indexOf('@');
    return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
  }
}
