# Traceability Model

`source → rule → business/system use case → slice → architecture realization → epic/story/task → test → runtime evidence`.

Cross-cutting work may use `quality/infrastructure use case → story` instead of a business slice. Links must identify stable IDs and source-registry keys. Traceability records explain relevance; they do not promote an implementation fact over a higher authority. Migration is forward-only: record missing legacy links as `legacy-untraced`, then add evidence when the affected work is revisited.
