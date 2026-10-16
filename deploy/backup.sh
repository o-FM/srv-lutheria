#!/bin/sh
# Backup diário do PostgreSQL (formato custom do pg_dump: compactado e restaurável por tabela).
# Roda em loop no container "backup": um dump ao subir e depois todo dia em BACKUP_HOUR.
# Restaurar:  docker compose exec -T postgres pg_restore -U luthieria -d luthieria --clean < arquivo.dump
set -eu

KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
HOUR="${BACKUP_HOUR:-3}"

dump() {
  file="/backups/luthieria-$(date +%Y%m%d-%H%M%S).dump"
  if pg_dump -Fc -f "$file.tmp"; then
    mv "$file.tmp" "$file"
    echo "$(date -Iseconds) backup ok: $file ($(du -h "$file" | cut -f1))"
  else
    rm -f "$file.tmp"
    echo "$(date -Iseconds) BACKUP FALHOU" >&2
  fi
  find /backups -name 'luthieria-*.dump' -mtime +"$KEEP_DAYS" -delete
}

dump
while true; do
  now=$(date +%s)
  next=$(date -d "$(date +%Y-%m-%d) ${HOUR}:00:00" +%s 2>/dev/null || echo $((now + 86400)))
  [ "$next" -le "$now" ] && next=$((next + 86400))
  sleep $((next - now))
  dump
done
