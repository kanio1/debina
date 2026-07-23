export const POST_LOGIN_PATH = "/";

// BFF_BASE_URL is the configured browser-visible BFF origin used for the
// registered OIDC callback. Do not derive the browser redirect from request
// transport metadata: a service may listen on 0.0.0.0 while users reach it
// through a distinct public origin.
export function postLoginRedirectUrl(publicBaseUrl: string): URL {
  const baseUrl = new URL(publicBaseUrl);
  if ((baseUrl.protocol !== "http:" && baseUrl.protocol !== "https:") || baseUrl.pathname !== "/" || baseUrl.search || baseUrl.hash) {
    throw new Error("BFF_BASE_URL must be an absolute HTTP(S) origin without path, query, or fragment");
  }
  return new URL(POST_LOGIN_PATH, baseUrl);
}
