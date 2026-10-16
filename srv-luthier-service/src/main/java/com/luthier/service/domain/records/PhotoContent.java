package com.luthier.service.domain.records;

/** Binário de uma foto para download. */
public record PhotoContent(String contentType, byte[] data) {
}
