"use client";

import { useQuery } from "@tanstack/react-query";
import { fetchHealth } from "@/lib/api";

export default function Home() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["health"],
    queryFn: fetchHealth,
    refetchInterval: 5_000,
  });

  return (
    <main className="min-h-screen bg-slate-950 text-slate-100 p-10">
      <div className="mx-auto max-w-2xl space-y-8">
        <header className="space-y-2">
          <h1 className="text-3xl font-semibold tracking-tight">commerce-lab</h1>
          <p className="text-slate-400">
            백엔드 학습 협업 프로젝트 — M0 스캐폴딩
          </p>
        </header>

        <section className="rounded-lg border border-slate-800 bg-slate-900 p-6">
          <h2 className="mb-4 text-sm font-medium uppercase tracking-wider text-slate-400">
            백엔드 상태
          </h2>

          {isLoading && <p className="text-slate-400">확인 중…</p>}

          {isError && (
            <div className="space-y-1">
              <p className="text-red-400">연결 실패</p>
              <p className="text-sm text-slate-500">
                {error instanceof Error ? error.message : "알 수 없는 오류"}
              </p>
              <p className="text-sm text-slate-500">
                백엔드가 켜져 있는지 확인하세요: ./gradlew :bootstrap:bootRun
              </p>
            </div>
          )}

          {data && (
            <dl className="grid grid-cols-2 gap-4">
              <div>
                <dt className="text-sm text-slate-500">status</dt>
                <dd className="text-lg font-medium text-emerald-400">{data.status}</dd>
              </div>
              <div>
                <dt className="text-sm text-slate-500">service</dt>
                <dd className="text-lg font-medium">{data.service}</dd>
              </div>
            </dl>
          )}
        </section>

        <section className="rounded-lg border border-slate-800 bg-slate-900 p-6">
          <h2 className="mb-3 text-sm font-medium uppercase tracking-wider text-slate-400">
            다음 마일스톤
          </h2>
          <p className="text-slate-300">
            M1 — 주문 코어와 동시성 제어. 이 화면에 상품 목록과 실시간 재고가 붙는다.
          </p>
        </section>
      </div>
    </main>
  );
}
