# Deploy backend

## Facts

| Key | Value |
|---|---|
| Host | `srv1343782.hstgr.cloud` (Hostinger VPS, Ubuntu 24.04, `187.77.1.90`) |
| User | `root` |
| Checkout on host | `/opt/snapsell` (branch `main`) |
| Compose dir | `/opt/snapsell/deploy` |
| Backend port on host | `127.0.0.1:8100` (container port 8000) |
| Public URL | `https://srv1343782.hstgr.cloud/` |
| Reverse proxy | System Caddy, site block in `/etc/caddy/Caddyfile`, backup at `/etc/caddy/Caddyfile.bak-snapsell` |
| Console fallback | hPanel -> VPS -> Web console (when SSH is unavailable) |
| Secrets on host | `/opt/snapsell/deploy/.env`, `/opt/snapsell/deploy/firebase-service-account.json` (both git-ignored) |
| Workflow | `.github/workflows/deploy-backend.yml` (`workflow_dispatch` only) |

## Goal

The backend container on the VPS runs the code at the requested git ref and
`https://srv1343782.hstgr.cloud/health` returns `{"status":"ok"}`.

## Preconditions

- The ref to deploy is on `origin` (normally `main` after a squash-merge).
- `backend` CI is green for that ref: `gh run list --workflow backend.yml --branch main --limit 1`.
- If the change needs a new env var, it has been added to `deploy/.env.example` and the operator knows the value.

## Steps

### Path A: GitHub Actions (preferred)

1. `[HUMAN]` one-time: add a deploy key to the VPS and the repo. On the VPS:
   `ssh-keygen -t ed25519 -f /root/.ssh/snapsell-deploy -N "" && cat /root/.ssh/snapsell-deploy.pub >> /root/.ssh/authorized_keys && cat /root/.ssh/snapsell-deploy`.
   Then in the repo: secret `VPS_SSH_KEY` = the private key, variable `VPS_HOST` = `srv1343782.hstgr.cloud`, variable `VPS_USER` = `root`.
   Check: `gh secret list | grep VPS_SSH_KEY && gh variable list | grep VPS_HOST`.
2. If a new env var is needed: `[HUMAN]` edit `/opt/snapsell/deploy/.env` on the host first.
3. Trigger: `gh workflow run deploy-backend.yml -f ref=main` (any SHA or branch works for `ref`).
4. Watch: `gh run watch $(gh run list --workflow deploy-backend.yml --limit 1 --json databaseId -q '.[0].databaseId')`.

### Path B: hPanel web console (when SSH is unavailable)

1. `[HUMAN]` open hPanel -> VPS -> Web console, log in as root.
2. Run, one line at a time (the console drops long pasted lines):
   ```bash
   cd /opt/snapsell && git fetch --all && git checkout main && git pull --ff-only
   cd /opt/snapsell/deploy && docker compose up -d --build
   docker compose ps
   ```

## Verification

```bash
curl -fsS https://srv1343782.hstgr.cloud/health            # {"status":"ok"}
curl -s -o /dev/null -w '%{http_code}\n' https://srv1343782.hstgr.cloud/me   # 401 (auth on)
```

On the host: `docker compose -f /opt/snapsell/deploy/docker-compose.yml ps` shows `backend` healthy, and
`git -C /opt/snapsell rev-parse --short HEAD` equals the deployed ref.

## Rollback

Deploy the previous ref: `gh workflow run deploy-backend.yml -f ref=<previous-sha>`
(find it with `git log --oneline -5 main`). Path B: `git checkout <previous-sha>` then `docker compose up -d --build`.
The Caddy site block is unchanged by deploys; if it must be restored: `cp /etc/caddy/Caddyfile.bak-snapsell /etc/caddy/Caddyfile && systemctl reload caddy`.
