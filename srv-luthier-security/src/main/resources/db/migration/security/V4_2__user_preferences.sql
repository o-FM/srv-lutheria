-- Preferências de aparência por usuário (cada atendente/luthier escolhe a sua cor).
alter table user_tb add column theme_mode varchar(10);
alter table user_tb add column theme_accent varchar(15);
alter table user_tb add column theme_tone varchar(10);
