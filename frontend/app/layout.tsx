import type { Metadata } from "next";
import Link from "next/link";
import { Geist, Geist_Mono, Noto_Sans_Devanagari } from "next/font/google";
import UsageMeter from "./usage-meter";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

const devanagari = Noto_Sans_Devanagari({
  variable: "--font-devanagari",
  subsets: ["devanagari"],
});

const THEME_SCRIPT = `try{var t=JSON.parse(localStorage.getItem("prefs")||"{}").theme;if(t==="light"||t==="dark")document.documentElement.dataset.theme=t}catch(e){}`;

export const metadata: Metadata = {
  title: "SamajhPDF",
  description: "Master any tech PDF",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} ${devanagari.variable} h-full antialiased`}
      suppressHydrationWarning /* data-theme is set before React loads */
    >
      <head>
        {/* Apply the saved light/dark choice before first paint (no flash). */}
        <script dangerouslySetInnerHTML={{ __html: THEME_SCRIPT }} />
      </head>
      <body className="min-h-full flex flex-col font-sans text-sm">
        <header className="flex items-center gap-4 sm:gap-6 border-b border-border bg-surface px-4 h-12 shrink-0 whitespace-nowrap">
          <Link href="/" className="font-semibold text-base">📖<span className="hidden sm:inline"> SamajhPDF</span></Link>
          <nav className="flex gap-3 sm:gap-4 text-muted">
            <Link href="/" className="hover:text-foreground">Library</Link>
            <Link href="/practice" className="hover:text-foreground">Practice</Link>
            <Link href="/mastery" className="hover:text-foreground">Mastery</Link>
            <Link href="/settings" className="hover:text-foreground">Settings</Link>
          </nav>
          <span className="flex-1" />
          <span className="hidden sm:inline"><UsageMeter /></span>
        </header>
        {children}
      </body>
    </html>
  );
}
