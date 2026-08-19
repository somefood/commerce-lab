# 마이그레이션

파일명 규칙: `V{번호}__{설명}.sql` (밑줄 두 개)

    V1__create_order_tables.sql
    V2__add_inventory_check_constraint.sql

규칙 세 가지.

1. **테이블은 스키마를 명시한다.** `CREATE TABLE "order".orders (...)`
   `order`는 SQL 예약어라 큰따옴표가 필수다.
2. **이미 적용된 파일은 수정하지 않는다.** Flyway가 체크섬을 비교해서 빌드를 깬다.
   고칠 게 있으면 새 버전 파일을 추가한다.
3. **`public` 스키마에 도메인 테이블을 만들지 않는다.** (CLAUDE.md §5)
   단, Flyway 자신의 이력 테이블 `flyway_schema_history`는 public에 둔다 —
   이건 도메인이 아니라 도구의 메타데이터이고, 어느 한 모듈 스키마에 넣으면
   M4에서 DB를 쪼갤 때 그 모듈에 딸려 가버린다.

이 파일들은 사용자가 작성한다 (스키마 설계 = 도메인 설계).
