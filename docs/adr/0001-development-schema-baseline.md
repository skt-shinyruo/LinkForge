# Use a deployment-owned schema baseline during disposable development

While every LinkForge database is disposable, the repository keeps one canonical `database/schema.sql` that deployment and integration tests execute; the application has no runtime migration dependency. Schema changes rewrite this baseline and require database recreation. Before creating any shared or otherwise non-disposable database, introduce versioned migrations from the current baseline.
