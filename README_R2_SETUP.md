# Cloudflare R2 setup for uploaded PDFs

This version stores uploaded PDFs in Cloudflare R2 when the R2 environment variables are set. If they are not set, it falls back to local `/data/uploads` storage for development.

## 1. Create Cloudflare R2 bucket

Cloudflare Dashboard -> R2 Object Storage -> Create bucket.

Recommended bucket name example:

```text
4sim-registry-files
```

Keep the bucket private. The admin download endpoint streams files through the Spring Boot backend, so public bucket access is not required.

## 2. Create R2 API token / access keys

Cloudflare Dashboard -> R2 -> Manage R2 API Tokens -> Create API token.

Use permissions that allow object read/write for the bucket.

You need:

```text
R2_ACCESS_KEY_ID
R2_SECRET_ACCESS_KEY
R2_ENDPOINT
R2_BUCKET
```

`R2_ENDPOINT` format:

```text
https://<cloudflare-account-id>.r2.cloudflarestorage.com
```

## 3. Add Railway backend variables

In Railway backend service -> Variables, add:

```text
R2_ENDPOINT=https://<cloudflare-account-id>.r2.cloudflarestorage.com
R2_BUCKET=4sim-registry-files
R2_ACCESS_KEY_ID=<your-access-key-id>
R2_SECRET_ACCESS_KEY=<your-secret-access-key>
R2_REGION=auto
```

Also make sure your frontend origin is allowed:

```text
ALLOWED_ORIGINS=https://4si-reyestr-formasi-2026.vercel.app,http://localhost:5173,http://localhost:8080
```

Redeploy the Railway backend after saving variables.

## 4. Test

Submit a new application with a PDF after redeploy. Old rows may contain filenames but no downloadable file if they were previously saved to Railway's ephemeral filesystem.

Expected DB value for new uploads:

```text
filePath = r2://applications/<uuid>.pdf
```

Admin endpoint remains:

```text
GET /api/admin/applications/{id}/file
Authorization: Bearer <admin-token>
```
