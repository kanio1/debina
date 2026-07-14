export const SESSION_COOKIE = "sepa_session";
export const CSRF_COOKIE = "sepa_csrf";

/** `Secure` is required on every cookie except in the local plain-HTTP dev profile. */
export function isSecureRequest(requestUrl: string): boolean {
  return new URL(requestUrl).protocol === "https:";
}
