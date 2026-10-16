# Deploy em produção (VM única)

Tudo roda em **uma máquina** com Docker: o Caddy recebe HTTPS na 443, entrega o front e
encaminha `/api/*` para a API; a API fala com o PostgreSQL pela rede interna do Docker.
Só as portas 80/443 ficam abertas para a internet.

```
Internet ──443──► caddy (front + HTTPS automático) ──/api──► app (Spring Boot) ──► postgres
                                                                   backup (pg_dump diário) ─┘
```

| Serviço | Imagem | Memória | Observação |
|---|---|---|---|
| `caddy` | `ghcr.io/o-fm/srv-lutheria-front` | 128 MB | Let's Encrypt automático, cabeçalhos de segurança |
| `app` | `ghcr.io/o-fm/srv-lutheria` | 1 GB | perfil `prod`, porta 8081 (actuator) só na rede interna |
| `postgres` | `postgres:17-alpine` | 512 MB | volume `pgdata` |
| `backup` | `postgres:17-alpine` | 128 MB | `pg_dump` diário em `./backups`, guarda 14 dias |

**Servidor recomendado:** 2 vCPU / 2 GB de RAM + 2 GB de swap (ex.: AWS Lightsail US$ 12/mês em
`us-east-1`). Com 4 GB (Lightsail US$ 24) sobra folga para crescer.

## 1. Criar o servidor

1. Crie a instância com **Ubuntu 24.04 LTS** (Lightsail: *Linux/Unix → OS Only → Ubuntu*).
2. Associe um **IP estático** (Lightsail: *Networking → Create static IP*; é grátis enquanto
   estiver ligado à instância).
3. Firewall: libere **TCP 80, TCP 443 e UDP 443**; deixe o SSH (22) restrito ao seu IP se puder.
4. No seu provedor de DNS crie um registro **A**: `app.suaoficina.com.br → IP estático`.

## 2. Preparar a máquina (uma vez)

```bash
ssh ubuntu@IP_DO_SERVIDOR

# Docker + Compose
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker

# Swap de 2 GB (evita OOM em picos com 2 GB de RAM)
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Atualizações de segurança automáticas do sistema
sudo apt-get install -y unattended-upgrades
```

## 3. Baixar o projeto e configurar

```bash
mkdir -p ~/luthieria && cd ~/luthieria
git clone https://github.com/o-FM/srv-lutheria.git
git clone https://github.com/o-FM/srv-lutheria-front.git   # só necessário se for compilar no servidor
cd srv-lutheria/deploy

cp .env.example .env
nano .env                 # DOMAIN, ACME_EMAIL, senhas e segredos (openssl rand -base64 48)
cp smtp.env.example smtp.env
nano smtp.env             # opcional, mas sem SMTP não há e-mail de recuperação de senha
chmod 600 .env smtp.env
```

## 4. Subir

**Opção A — imagens prontas do GitHub (recomendado):** a cada push na `develop` (API) e na
`main` (front) o GitHub Actions publica as imagens no GHCR. Se os pacotes forem privados,
faça login uma vez com um token (*GitHub → Settings → Developer settings → Personal access
tokens*, escopo `read:packages`):

```bash
echo SEU_TOKEN | docker login ghcr.io -u SEU_USUARIO --password-stdin
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

**Opção B — compilar no próprio servidor** (precisa dos dois repositórios lado a lado; o build
do Java usa ~1,5 GB, então pare a stack antes se a máquina tiver só 2 GB):

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Acompanhe a subida (a API leva ~1 min na primeira vez, por causa das migrations):

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f app
```

Abra `https://SEU_DOMINIO`, entre com `ADMIN_EMAIL` / `ADMIN_SENHA` e **troque a senha** em
*menu do usuário → Minha Conta*. Depois cadastre a equipe em *Mais → Config → Equipe*.
Outras oficinas podem se cadastrar sozinhas pela tela de login (*Cadastre sua oficina*).

## 5. Atualizar

```bash
cd ~/luthieria/srv-lutheria && git pull
cd deploy
docker compose -f docker-compose.prod.yml pull          # opção A
docker compose -f docker-compose.prod.yml up -d         # (opção B: up -d --build)
docker image prune -f
```

As migrations do banco (Flyway) rodam sozinhas na subida da API.

## 6. Backups

- O serviço `backup` grava `deploy/backups/luthieria-AAAAMMDD-HHMMSS.dump` ao subir e todo dia às
  `BACKUP_HOUR` (padrão 3h), mantendo `BACKUP_KEEP_DAYS` dias. As **fotos das OS ficam no banco**,
  então o dump já leva tudo.
- **Leve uma cópia para fora do servidor.** Opções simples:
  - *Lightsail*: ative os snapshots automáticos diários da instância (US$ 0,05/GB/mês).
  - *Bucket S3/R2/B2*: `rclone sync ~/luthieria/srv-lutheria/deploy/backups remoto:luthieria-backups`
    no `crontab -e` (ex.: `30 3 * * *`).
- Restaurar um dump:

```bash
docker compose -f docker-compose.prod.yml stop app
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_restore -U luthieria -d luthieria --clean --if-exists < backups/luthieria-AAAAMMDD-HHMMSS.dump
docker compose -f docker-compose.prod.yml start app
```

## 7. Operação

| Tarefa | Comando |
|---|---|
| Status / saúde | `docker compose -f docker-compose.prod.yml ps` |
| Logs da API (JSON, com traceId e tenantId) | `docker compose -f docker-compose.prod.yml logs -f app` |
| Logs de acesso | `docker compose -f docker-compose.prod.yml logs -f caddy` |
| Uso de memória | `docker stats --no-stream` |
| Reiniciar a API | `docker compose -f docker-compose.prod.yml restart app` |
| Console do banco | `docker compose -f docker-compose.prod.yml exec postgres psql -U luthieria` |

Monitoramento externo gratuito: cadastre `https://SEU_DOMINIO/` num serviço como UptimeRobot ou
Better Stack para receber alerta se o site cair.

## Notas de segurança

- `.env` e `smtp.env` têm segredos: nunca versione (já estão no `.gitignore`).
- A porta 8081 (actuator/Prometheus) e o PostgreSQL **não** são publicados no host.
- O Caddy renova os certificados sozinho; os dados dele ficam no volume `caddy_data`.
- `JWT_SECRET` trocado invalida todas as sessões (todo mundo entra de novo).
- Rate limit de login/portal é por IP e fica em memória: rode **uma** instância da API.
