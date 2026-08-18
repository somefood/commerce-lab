-- 모듈별 스키마 분리.
-- 같은 DB를 쓰되 스키마를 나누는 이유: 교차 JOIN을 물리적으로 어렵게 만들어
-- M4의 DB 분리 시점에 코드 변경이 최소가 되도록 한다.

CREATE SCHEMA IF NOT EXISTS "order";
CREATE SCHEMA IF NOT EXISTS payment;

-- 각 모듈은 자기 스키마에만 접근한다.
-- (M1 이후 모듈별 DB 사용자를 분리하면 이 규칙을 DB 권한으로도 강제할 수 있다.)
GRANT ALL ON SCHEMA "order" TO commerce;
GRANT ALL ON SCHEMA payment TO commerce;
