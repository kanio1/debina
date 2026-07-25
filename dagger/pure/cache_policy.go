package pure

// ApprovedNamedCacheVolumes are the only SHARED tool-cache volume names
// permitted in production pipeline code (stress fixtures are separate).
var ApprovedNamedCacheVolumes = []string{
	"debina-maven-jdk25",
	"debina-pnpm-node24.18.0-pnpm10.33.0",
	"debina-dagger-go-1.26.5",
}

// ApprovedCacheMountPaths are the only host paths that may receive
// WithMountedCache for dependency acceleration.
var ApprovedCacheMountPaths = []string{
	"/root/.m2/repository",
	"/pnpm/store",
	"/go/pkg/mod",
}

// ForbiddenCacheMountPathFragments must never appear in named cache mounts.
var ForbiddenCacheMountPathFragments = []string{
	"postgres",
	"kafka",
	"keycloak",
	".env",
	"credential",
	"cookie",
	"token",
	"test-results",
	"playwright-report",
	"sepa_nexus",
}

// RequiredSourceExcludes must remain in the module workspace filter.
var RequiredSourceExcludes = []string{
	".git",
	"frontend/node_modules",
	"frontend/.next",
	"backend/target",
	"build",
	"coverage",
	"test-results",
	"playwright-report",
	"tmp",
}
