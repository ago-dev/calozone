# infra

Everything needed to run Calozone outside the code itself.

## Contents

- `docker-compose.yml` — local PostgreSQL 16 for development (added in step 0.02).
- Deployment config for staging and production (added in step 0.10).
- CI workflows live in `/.github/workflows/` at the repo root, as GitHub requires.

## Local database

```bash
docker compose up -d        # start
docker compose down         # stop, keep data
docker compose down -v      # stop and wipe data
```

Connect: `psql -h localhost -U calozone calozone`

## Environments

| Name    | Purpose                       | Region |
|---------|-------------------------------|--------|
| local   | Your machine                  | —      |
| staging | Test copy, deployed from main | EU     |
| prod    | Real users                    | EU     |
