# NexusAI frontend

Next.js (App Router) + React + TypeScript + Tailwind CSS UI for the NexusAI API.
Run instructions and configuration: see the [root README](../README.md).

```bash
npm install
npm run dev          # http://localhost:3000 (backend expected on :8080)
npm run typecheck
npm run lint
npm run build && npm start
```

- `src/lib/api/` is the typed client. Types mirror the Spring Boot DTOs; errors are parsed
  from RFC 9457 problem details into `ApiError` (with per-field validation messages).
- All data is fetched from the live backend in the browser; there are no mocks.
- `cacheComponents` is enabled: visited routes stay mounted but hidden (React `<Activity>`),
  so dialogs close themselves when hidden and data reloads when a route is shown again.
