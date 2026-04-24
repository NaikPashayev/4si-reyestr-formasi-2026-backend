# Innovate Registry Backend + Admin Panel

Java Spring Boot backend for the registry website.

## What it includes

- `POST /api/applications` for public form submissions.
- Hidden admin panel at `/c4ir-admin-4sim`.
- Password-protected admin API at `/api/admin/applications`.
- H2 file database stored in Docker volume `/data`.
- CSV export from the admin panel.
- Dockerfile and docker-compose setup.

## Run locally

```bash
docker compose up --build
```

Open:

- Backend health/basic URL: `http://localhost:8080`
- Admin panel: `http://localhost:8080/c4ir-admin-4sim`

The default admin password is set in `docker-compose.yml` as `ADMIN_PASSWORD`. Change it before deployment.

## Connect the existing website form

On successful final form submit, send the collected form data to:

```ts
await fetch("http://localhost:8080/api/applications", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(formData),
});
```

For production, replace `http://localhost:8080` with your backend domain and set `ALLOWED_ORIGINS` to the public website domain.

## Production notes

1. Change `ADMIN_PASSWORD`.
2. Change `ADMIN_TOKEN_SECRET` to a long random value.
3. Use HTTPS through Nginx, Caddy, Cloudflare, or another reverse proxy.
4. Consider replacing H2 with PostgreSQL if you expect high traffic or long-term multi-admin usage.
