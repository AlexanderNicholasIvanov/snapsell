# Hosting the SnapSell backend

The phone apps never call Anthropic or eBay themselves; they talk to one backend
over HTTPS. This directory runs that backend as **two processes on one host**:

| Process | Image | Job |
|---|---|---|
| `backend` | built from `../backend/Dockerfile` | The FastAPI service (`/health`, `/me`, `/identify`, `/price`, `/bundle`). Listens on `127.0.0.1:8100` only. |
| `caddy` | `caddy:2.11.4` | Public entry point. Automatic HTTPS for `SNAPSELL_DOMAIN`, proxies to the backend. Optional (`--profile caddy`); a box that already runs its own reverse proxy uses `caddy-site.example` instead. |

## One-time setup on the host

1. Install Docker (with the compose plugin). Point DNS for your API hostname at the host and open ports 80 and 443.
2. Clone the repo, then:

   ```bash
   cd deploy
   cp .env.example .env            # fill in SNAPSELL_DOMAIN, Anthropic + eBay keys, allowed emails
   # Firebase console -> Project settings -> Service accounts -> Generate new private key
   cp ~/Downloads/<your-firebase-key>.json firebase-service-account.json
   docker compose --profile caddy up -d --build
   curl https://$SNAPSELL_DOMAIN/health   # {"status":"ok"}
   ```

   Box that already runs Caddy (or another reverse proxy) on 80/443: run `docker compose up -d --build`
   (backend only, on `127.0.0.1:8100`) and append `caddy-site.example` to `/etc/caddy/Caddyfile` with your hostname.

3. Give the apps the URL (must end in `/`):
   - iOS TestFlight builds: set the repository variable `SNAPSELL_BACKEND_URL` (`.github/workflows/testflight.yml`) or `SNAPSELL_BACKEND_URL` in `ios/Config/Local.xcconfig` for local `ios/scripts/testflight.sh` runs.
   - Android release builds: same variable in `.github/workflows/release.yml`.
   - Any installed build: Settings -> Backend URL overrides the baked-in default.

`.env` and `firebase-service-account.json` are git-ignored. Never set `SNAPSELL_AUTH_DISABLED=1` on a host that is reachable from the internet.

## Updating

```bash
git pull && docker compose up -d --build            # add --profile caddy if you run the caddy container
```

Caddy keeps its certificates in the `caddy_data` volume, so restarts and rebuilds do not re-issue them.

## Local smoke test (no keys)

```bash
printf 'SNAPSELL_DOMAIN=:80\nSNAPSELL_HTTP_PORT=8088\nSNAPSELL_HTTPS_PORT=8443\nSNAPSELL_AUTH_DISABLED=1\nANTHROPIC_API_KEY=x\nSNAPSELL_EBAY_CLIENT_ID=x\nSNAPSELL_EBAY_CLIENT_SECRET=x\n' > .env
: > firebase-service-account.json
docker compose up -d --build
curl http://localhost:8088/health
```

Point a simulator build at `http://localhost:8088/` in Settings. `/health` and `/me` work; `/identify` and `/price` return 502 until real keys are in `.env`.

## Railway instead of your own box

`backend/railway.toml` describes the same service for Railway, which terminates HTTPS itself (no Caddy needed):

```bash
cd backend && railway login && railway init && railway up
```

Then add the variables from `backend/.env.example` to the service, upload the Firebase service-account JSON as a file variable (or paste it and point `GOOGLE_APPLICATION_CREDENTIALS` at it), and generate a public domain under Settings -> Networking.
