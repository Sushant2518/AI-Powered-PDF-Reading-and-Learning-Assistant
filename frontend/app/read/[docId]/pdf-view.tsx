"use client";

import { Document, Page, pdfjs } from "react-pdf";
import "react-pdf/dist/Page/TextLayer.css";
import "react-pdf/dist/Page/AnnotationLayer.css";
import type { Rect } from "@/lib/api";

// Must live in the same module that renders react-pdf components.
pdfjs.GlobalWorkerOptions.workerSrc = new URL("pdfjs-dist/build/pdf.worker.min.mjs", import.meta.url).toString();

export type Overlay = { key: string; rects: Rect[]; color: string; flash?: boolean };

export default function PdfView({ url, page, width, overlays }: { url: string; page: number; width: number; overlays: Overlay[] }) {
  return (
    <Document file={url} loading={<p className="text-muted">Loading PDF…</p>}
              error={<p className="text-red-600">Could not load the PDF.</p>}>
      {/* data-page-box: the reader measures this box to turn selections into page-relative anchors. */}
      <div data-page-box className="relative mx-auto w-fit shadow">
        <Page pageNumber={page} width={width} />
        {overlays.flatMap((o) =>
          o.rects.map((r, i) => (
            <div key={`${o.key}-${i}`}
                 className={`pointer-events-none absolute rounded-sm ${o.flash ? "animate-pulse" : ""}`}
                 style={{ left: `${r.x * 100}%`, top: `${r.y * 100}%`, width: `${r.w * 100}%`, height: `${r.h * 100}%`,
                          background: o.color, mixBlendMode: "multiply" }} />
          )),
        )}
      </div>
    </Document>
  );
}
