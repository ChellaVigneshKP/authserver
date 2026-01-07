import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Auth Server Admin Portal",
  description: "Administrative portal for Auth Server management",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
