import type { components } from "@/types/api";

/**
 * 백엔드 OpenAPI에서 생성된 타입을 그대로 쓴다.
 *
 * 손으로 인터페이스를 다시 적지 않는 이유: 백엔드가 필드 이름을 바꾸면
 * 이 파일이 컴파일 에러를 낸다. API 계약 위반이 런타임이 아니라
 * 빌드 시점에 잡힌다.
 */
export type HealthResponse = components["schemas"]["HealthResponse"];

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export async function fetchHealth(): Promise<HealthResponse> {
  const response = await fetch(`${BASE_URL}/api/health`, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`헬스 체크 실패: ${response.status}`);
  }
  return response.json();
}
