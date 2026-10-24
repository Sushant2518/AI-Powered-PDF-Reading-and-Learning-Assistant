import { Suspense } from "react";
import Reader from "./reader";

export default function ReadPage({ params }: PageProps<"/read/[docId]">) {
  return (
    <Suspense fallback={<p className="p-6 text-muted">Loading…</p>}>
      {params.then(({ docId }) => <Reader docId={docId} />)}
    </Suspense>
  );
}
