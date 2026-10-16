package com.luthier.security;

import java.util.UUID;

/**
 * Evento público: alguém pediu um novo código para a área do cliente informando o telefone.
 * O módulo de notificações verifica se o telefone é de um cliente e, se for, envia o código.
 * (A resposta da API é sempre igual, exista o cliente ou não.)
 */
public record CustomerAccessCodeRequested(UUID tenantId, String phoneDigits) {}
