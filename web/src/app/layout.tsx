import type { Metadata } from "next";
export const metadata: Metadata = {
  title: "Identity portal",
  robots: { index: false, follow: false },
  referrer: "origin",
};
export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <main
          style={{
            maxWidth: 800,
            margin: "4rem auto",
            padding: "0 1.5rem",
            fontFamily: "system-ui",
            lineHeight: 1.6,
          }}
        >
          {children}
        </main>
      </body>
    </html>
  );
}
