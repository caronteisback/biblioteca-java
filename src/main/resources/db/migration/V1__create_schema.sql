-- Base schema of the library system. See docs/business-rules.md and docs/diagrams/er-diagram.md.
-- Conventions: plain SQL only, explicit constraint names, flags as NUMBER(1), days as DATE.

CREATE TABLE libro (
    id               NUMBER(19)    GENERATED ALWAYS AS IDENTITY,
    isbn             VARCHAR2(13)  NOT NULL,
    titulo           VARCHAR2(200) NOT NULL,
    autor            VARCHAR2(150) NOT NULL,
    anio_publicacion NUMBER(4),
    CONSTRAINT pk_libro PRIMARY KEY (id),
    CONSTRAINT uk_libro_isbn UNIQUE (isbn),
    CONSTRAINT ck_libro_isbn CHECK (REGEXP_LIKE(isbn, '^([0-9]{9}[0-9X]|[0-9]{13})$')),
    CONSTRAINT ck_libro_anio CHECK (anio_publicacion IS NULL OR anio_publicacion BETWEEN 1000 AND 2100)
);

CREATE TABLE ejemplar (
    id       NUMBER(19)   GENERATED ALWAYS AS IDENTITY,
    libro_id NUMBER(19)   NOT NULL,
    codigo   VARCHAR2(30) NOT NULL,
    activo   NUMBER(1)    DEFAULT 1 NOT NULL,
    CONSTRAINT pk_ejemplar PRIMARY KEY (id),
    CONSTRAINT uk_ejemplar_codigo UNIQUE (codigo),
    CONSTRAINT fk_ejemplar_libro FOREIGN KEY (libro_id) REFERENCES libro (id),
    CONSTRAINT ck_ejemplar_activo CHECK (activo IN (0, 1))
);

CREATE INDEX ix_ejemplar_libro ON ejemplar (libro_id);

CREATE TABLE socio (
    id             NUMBER(19)    GENERATED ALWAYS AS IDENTITY,
    nombre         VARCHAR2(120) NOT NULL,
    email          VARCHAR2(150) NOT NULL,
    activo         NUMBER(1)     DEFAULT 1 NOT NULL,
    fecha_registro DATE          NOT NULL,
    CONSTRAINT pk_socio PRIMARY KEY (id),
    CONSTRAINT ck_socio_activo CHECK (activo IN (0, 1))
);

CREATE UNIQUE INDEX ux_socio_email ON socio (LOWER(email));

CREATE TABLE prestamo (
    id                NUMBER(19) GENERATED ALWAYS AS IDENTITY,
    ejemplar_id       NUMBER(19) NOT NULL,
    socio_id          NUMBER(19) NOT NULL,
    fecha_prestamo    DATE       NOT NULL,
    fecha_vencimiento DATE       NOT NULL,
    fecha_devolucion  DATE,
    renovaciones      NUMBER(1)  DEFAULT 0 NOT NULL,
    CONSTRAINT pk_prestamo PRIMARY KEY (id),
    CONSTRAINT fk_prestamo_ejemplar FOREIGN KEY (ejemplar_id) REFERENCES ejemplar (id),
    CONSTRAINT fk_prestamo_socio FOREIGN KEY (socio_id) REFERENCES socio (id),
    CONSTRAINT ck_prestamo_renovaciones CHECK (renovaciones BETWEEN 0 AND 3),
    CONSTRAINT ck_prestamo_vencimiento CHECK (fecha_vencimiento >= fecha_prestamo),
    CONSTRAINT ck_prestamo_devolucion CHECK (fecha_devolucion IS NULL OR fecha_devolucion >= fecha_prestamo)
);

-- At most one active loan per copy. The CASE yields NULL for returned loans, and Oracle
-- does not index entries that are all NULL, so only active loans compete for uniqueness.
CREATE UNIQUE INDEX ux_prestamo_ejemplar_activo
    ON prestamo (CASE WHEN fecha_devolucion IS NULL THEN ejemplar_id END);

-- Oracle does not index foreign keys by itself. These serve the joins, the active-loans
-- lookup per member (the composite also covers fecha_devolucion) and parent-row locking.
CREATE INDEX ix_prestamo_socio ON prestamo (socio_id, fecha_devolucion);
CREATE INDEX ix_prestamo_ejemplar ON prestamo (ejemplar_id);

CREATE TABLE multa (
    id               NUMBER(19)    GENERATED ALWAYS AS IDENTITY,
    prestamo_id      NUMBER(19)    NOT NULL,
    dias_retraso     NUMBER(4)     NOT NULL,
    monto            NUMBER(10, 2) NOT NULL,
    estado           VARCHAR2(10)  NOT NULL,
    fecha_generacion DATE          NOT NULL,
    fecha_pago       DATE,
    CONSTRAINT pk_multa PRIMARY KEY (id),
    CONSTRAINT uk_multa_prestamo UNIQUE (prestamo_id),
    CONSTRAINT fk_multa_prestamo FOREIGN KEY (prestamo_id) REFERENCES prestamo (id),
    CONSTRAINT ck_multa_dias CHECK (dias_retraso > 0),
    CONSTRAINT ck_multa_monto CHECK (monto > 0),
    CONSTRAINT ck_multa_estado CHECK (estado IN ('PENDING', 'PAID')),
    CONSTRAINT ck_multa_pago CHECK (
        (estado = 'PENDING' AND fecha_pago IS NULL) OR (estado = 'PAID' AND fecha_pago IS NOT NULL)
    )
);
