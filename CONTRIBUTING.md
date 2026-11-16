# Contributing

Thanks for helping. Bug reports, fixes, new integrations and documentation are all welcome.

## Run it for development

You need Docker. For frontend work you also need Node 22; for backend tests, Java 25 and Maven.

```bash
./scripts/setup.sh
docker compose up -d --build          # the whole stack, http://localhost:8080
```

**Backend changes:** rebuild and restart just the backend.

```bash
docker compose up -d --build backend
docker compose logs -f backend
```

**Frontend changes:** run the dev server with hot reload against the running stack.

```bash
cd frontend
npm install
NEXUS_API_TARGET=http://localhost:8080 npm run dev     # http://localhost:5173
```

## Before you open a pull request

```bash
cd backend && mvn verify          # unit tests + an integration test on a real Postgres (needs Docker)
cd frontend && npx tsc -b --noEmit && npm run build
```

- Keep changes focused: one fix or feature per pull request.
- Add or update a test when you change behaviour.
- Never commit secrets, real hostnames or customer data. `.env` is ignored for that reason.
- New investigation tools must be read-only and validate their arguments.

## Where things live

| Path | What |
|---|---|
| `backend/src/main/java/com/maverickignite/holmes` | Intelligence: ingestion, incidents, investigations, toolsets, fix PRs, notifications |
| `backend/src/main/java/com/maverickignite/mcp` | MCP: servers, chat, agents, model providers |
| `backend/src/main/resources/toolsets/*.yaml` | CLI toolsets (kubernetes, docker, aws, azure): add a tool with a few lines of YAML |
| `frontend/src/holmes` | Intelligence pages |
| `frontend/src/components` | MCP chat, settings and the shared shell |
| `deploy/` | Vector config, remote log agent, nginx example |

## Adding an integration

- **A CLI tool** (anything with a read-only command line): add a YAML file under
  `backend/src/main/resources/toolsets/`, following `docker.yaml`.
- **An API** (HTTP, a database driver): add a `Toolset` class under `toolset/builtin`, following
  `PrometheusToolset`.
- **An MCP server**: add an entry to `McpCatalog`.

By contributing you agree that your work is released under the MIT License.
