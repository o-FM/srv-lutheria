-- =============================================================================
-- srv-luthieria · PostgreSQL 15+ · 01 — banco e usuário da aplicação
-- Executar como superusuário (ex.: postgres):
--   psql -h <host> -U postgres -v app_password="'TroqueEstaSenha'" -f 01_create_database.sql
-- Em serviços gerenciados (RDS, Cloud SQL, Azure) o banco costuma ser criado pelo console;
-- nesse caso rode apenas o bloco de GRANTs conectado ao banco "luthieria".
-- =============================================================================

-- Usuário da aplicação: dono das tabelas, sem privilégios de superusuário
CREATE ROLE luthieria_app WITH LOGIN PASSWORD :app_password
    NOSUPERUSER NOCREATEDB NOCREATEROLE;

CREATE DATABASE luthieria
    WITH OWNER = luthieria_app
         ENCODING = 'UTF8'
         TEMPLATE = template0
         LC_COLLATE = 'C.UTF-8'
         LC_CTYPE = 'C.UTF-8';

\connect luthieria

-- Nada de objetos criados por outros usuários no schema público
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO luthieria_app;
