-- CREATE 순서는 참조 의존성을 따른다.
--   products    → 참조 없음
--   orders      → 참조 없음
--   order_lines → orders, products
--   inventories → products
--   reservations→ orders, products
-- 참조되는 쪽이 먼저 존재해야 FK를 걸 수 있다.

CREATE table "order".products
(
    id          varchar(100) PRIMARY KEY,
    name        varchar(255) NOT NULL,
    unit_amount bigint       NOT NULL,
    -- 물리 삭제 대신 논리 삭제. 과거 주문이 참조하는 상품은 사라지면 안 된다.
    -- "비활성 상품으로 새 주문을 만들 수 없다"는 규칙은 DB가 아니라 도메인이 강제한다.
    -- 이유: DB로 막으려면 과거 주문이 있는 상품을 영원히 비활성화할 수 없게 된다.
    active      boolean      NOT NULL DEFAULT true
);

CREATE table "order".orders
(
    id           varchar(100) PRIMARY KEY,
    account_id   varchar(100) NOT NULL,
    status       varchar(20)  NOT NULL,
    total_amount bigint       NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL,
    version      integer      NOT NULL DEFAULT 0,

    -- 설계문서 §4.2 주문 상태머신:
    --   CREATED --payment.completed--> PAID --> SHIPPED --> DELIVERED
    --   CREATED/PAID --expired|cancelled|payment.failed--> CANCELLED
    CONSTRAINT ck_orders_status
        CHECK (status IN ('CREATED', 'PAID', 'CANCELLED', 'SHIPPED', 'DELIVERED'))
);

CREATE table "order".order_lines
(
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    varchar(100) NOT NULL,
    product_id  varchar(100) NOT NULL,
    quantity    integer      NOT NULL,
    unit_amount bigint       NOT NULL,

    CONSTRAINT fk_order_lines_order
        FOREIGN KEY (order_id) REFERENCES "order".orders (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_order_lines_product
        FOREIGN KEY (product_id) REFERENCES "order".products (id)
            ON DELETE NO ACTION
);

CREATE table "order".inventories
(
    product_id varchar(100) PRIMARY KEY,
    total      integer NOT NULL,
    reserved   integer NOT NULL,
    version    integer NOT NULL DEFAULT 0,

    CONSTRAINT fk_inventories_product
        FOREIGN KEY (product_id) REFERENCES "order".products (id)
            ON DELETE NO ACTION,

    -- available 컬럼을 두지 않는다. total - reserved 로 계산한다.
    -- 이 CHECK가 오버셀을 막지는 못하지만, 오버셀을 조용한 음수가 아니라 예외로 드러낸다.
    CONSTRAINT inventories_reserved_not_exceeding_total
        CHECK (reserved >= 0 AND reserved <= total)
);

CREATE table "order".reservations
(
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   varchar(100) NOT NULL,
    product_id varchar(100) NOT NULL,
    quantity   integer      NOT NULL,
    status     varchar(20)  NOT NULL,
    expires_at timestamptz  NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_reservation_holds_status
        CHECK (status IN ('HELD', 'CONFIRMED', 'RELEASED', 'EXPIRED')),

    CONSTRAINT fk_reservation_order
        FOREIGN KEY (order_id) REFERENCES "order".orders (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_reservation_product
        FOREIGN KEY (product_id) REFERENCES "order".products (id)
            ON DELETE NO ACTION
);

-- 만료 배치용. 5초마다 도는 WHERE status='HELD' AND expires_at <= :now 가 이 인덱스를 탄다.
-- 부분 인덱스인 이유: 확정·만료된 과거 예약은 이 쿼리의 대상이 아니다.
-- 인덱스가 작을수록 갱신 비용도 작다.
CREATE INDEX idx_reservations_expiring
    ON "order".reservations (expires_at) WHERE status = 'HELD';

-- 성능 장치가 아니라 제약이다.
-- 같은 주문이 같은 상품을 HELD로 두 번 잡는 것을 DB가 거절한다.
-- 애플리케이션 검증만으로는 동시에 들어온 두 요청이 서로를 보지 못한다 —
-- 이는 1단계에서 관측할 오버셀과 정확히 같은 구조의 문제다.
-- 부분 인덱스라 CONFIRMED/EXPIRED가 된 과거 행은 제약에서 빠진다.
CREATE UNIQUE INDEX uq_reservations_active
    ON "order".reservations (order_id, product_id) WHERE status = 'HELD';
