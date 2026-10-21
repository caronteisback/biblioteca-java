-- Accounts that can sign in. A librarian has no member behind it; a member account belongs to exactly one member.
-- The password is stored only as a salted hash (see the security package), never in clear.

CREATE TABLE usuario (
    id            NUMBER(19)    GENERATED ALWAYS AS IDENTITY,
    email         VARCHAR2(150) NOT NULL,
    password_hash VARCHAR2(100) NOT NULL,
    rol           VARCHAR2(10)  NOT NULL,
    socio_id      NUMBER(19),
    activo        NUMBER(1)     DEFAULT 1 NOT NULL,
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT fk_usuario_socio FOREIGN KEY (socio_id) REFERENCES socio (id),
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('LIBRARIAN', 'MEMBER')),
    CONSTRAINT ck_usuario_activo CHECK (activo IN (0, 1)),
    CONSTRAINT ck_usuario_socio CHECK ((rol = 'MEMBER' AND socio_id IS NOT NULL) OR (rol = 'LIBRARIAN' AND socio_id IS NULL))
);

CREATE UNIQUE INDEX ux_usuario_email ON usuario (LOWER(email));
-- One account per member. Oracle does not index rows whose only indexed column is NULL, so librarians do not collide.
CREATE UNIQUE INDEX ux_usuario_socio ON usuario (socio_id);
