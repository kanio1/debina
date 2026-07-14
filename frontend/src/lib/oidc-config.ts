function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

// Every field is a getter — env vars are validated lazily, on first access from
// a request handler, not eagerly at module import time. Next.js evaluates route
// modules during `next build`'s page-data-collection step even for fully
// dynamic routes, so eager top-level env reads break CI builds that don't (and
// shouldn't need to) provide real OIDC secrets just to compile.
export const oidcConfig = {
  get issuer() {
    return requireEnv("KEYCLOAK_ISSUER");
  },
  get clientId() {
    return requireEnv("KEYCLOAK_CLIENT_ID");
  },
  get clientSecret() {
    return requireEnv("KEYCLOAK_CLIENT_SECRET");
  },
  get authorizationEndpoint() {
    return `${this.issuer}/protocol/openid-connect/auth`;
  },
  get tokenEndpoint() {
    return `${this.issuer}/protocol/openid-connect/token`;
  },
  get endSessionEndpoint() {
    return `${this.issuer}/protocol/openid-connect/logout`;
  },
  get jwksUri() {
    return `${this.issuer}/protocol/openid-connect/certs`;
  },
};

export const bffConfig = {
  get baseUrl() {
    return requireEnv("BFF_BASE_URL");
  },
  get redirectUri() {
    return `${this.baseUrl}/api/auth/callback`;
  },
  get postLogoutRedirectUri() {
    return `${this.baseUrl}/`;
  },
};

export const backendConfig = {
  get baseUrl() {
    return requireEnv("BACKEND_API_BASE_URL");
  },
};
