export function applySecurityHeaders(headers: Headers, nonce: string): void {
  const isDev = process.env.NODE_ENV === "development";
  headers.set(
    "Content-Security-Policy",
    [
      "default-src 'self'",
      // Next.js RSC bootstrap relies on inline <script> tags (self.__next_f/__next_r);
      // 'strict-dynamic' + per-request nonce lets those run without 'unsafe-inline'.
      `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isDev ? " 'unsafe-eval'" : ""}`,
      "style-src 'self' 'unsafe-inline'",
      "img-src 'self' data:",
      "connect-src 'self'",
      "font-src 'self'",
      "frame-ancestors 'none'",
      "base-uri 'self'",
      "form-action 'self'",
    ].join("; "),
  );
  // Inert on plain HTTP (Iteration 0 local dev); required once this sits behind TLS.
  headers.set("Strict-Transport-Security", "max-age=63072000; includeSubDomains");
  headers.set("X-Content-Type-Options", "nosniff");
  headers.set("X-Frame-Options", "DENY");
  headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
}
