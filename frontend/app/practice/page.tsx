import { Suspense } from "react";
import Practice from "./practice";

export default function PracticePage({ searchParams }: PageProps<"/practice">) {
  return (
    <Suspense fallback={<p className="p-6 text-muted">Loading…</p>}>
      {searchParams.then((sp) => {
        const one = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v);
        const ids = one(sp.conceptIds)?.split(",").map(Number).filter(Boolean) ?? [];
        return <Practice documentId={one(sp.documentId)} conceptIds={ids} />;
      })}
    </Suspense>
  );
}
