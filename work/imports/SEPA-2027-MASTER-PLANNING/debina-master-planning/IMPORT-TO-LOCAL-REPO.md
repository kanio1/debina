# Import to Local Debina Repository

## 1. Verify local state

```bash
cd /home/suso/debina
git status --short
git branch --show-current
git rev-parse HEAD
```

Package baseline: `f601089d2f123ff01f41378f47be5dd9ce361fd2` on `main`.

A different SHA is not automatically an error, but every generated claim must be reconciled against local changes.

## 2. Extract package outside the repository

```bash
mkdir -p /home/suso/debina-master-planning-import
unzip debina-master-planning.zip -d /home/suso/debina-master-planning-import
```

## 3. Dry run import only repo-target paths

```bash
cd /home/suso/debina
rsync -avhn \
  /home/suso/debina-master-planning-import/docs/ ./docs/
rsync -avhn \
  /home/suso/debina-master-planning-import/work/ ./work/
```

Do not copy the package root `README.md` over Debina's root README.

## 4. Check collisions

Before the real copy, inspect whether any target file already exists:

```bash
for f in \
  docs/product/debina-product-vision.md \
  docs/product/debina-capability-map.md \
  docs/architecture/current-state.md \
  docs/architecture/target-state.md \
  docs/architecture/context/debina-context.md \
  docs/architecture/containers/debina-containers.md \
  docs/architecture/domain-boundaries.md \
  docs/architecture/lifecycle-model.md \
  docs/product/roadmap.md \
  docs/product/documentation-backlog.md \
  docs/evidence/claim-maps/master-traceability.md \
  work/active/SEPA-2027-MASTER-PLANNING/findings.md \
  work/active/SEPA-2027-MASTER-PLANNING/decisions-required.md \
  work/active/SEPA-2027-MASTER-PLANNING/master-plan.md \
  work/NEXT-WORK.md work/QUEUE.md; do
  test -e "$f" && echo "COLLISION: $f"
done
```

Merge collisions manually; do not overwrite an existing newer document.

## 5. Copy

```bash
rsync -avh /home/suso/debina-master-planning-import/docs/ ./docs/
rsync -avh /home/suso/debina-master-planning-import/work/ ./work/
```

## 6. Review diff

```bash
git status --short
git diff --stat
git diff -- docs work
```

## 7. Mandatory reconciliation before acceptance

1. Check `HANDOFF.md` vs EPIC-26/planning/Wave11 record.
2. Re-run existing documentation/capability validators.
3. Confirm links against local paths.
4. Review every `OPEN_QUESTION`, `DECISION_REQUIRED`, `SOURCE_BLOCKED`.
5. Do not mark `work/QUEUE.md` items READY without human approval.
6. Keep package helper files outside the repo unless intentionally placed under a review/evidence folder.

## 8. Suggested commit boundary

One documentation-only commit after review. Do not mix with production code or migrations.
