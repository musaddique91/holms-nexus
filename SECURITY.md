# Security

## Reporting a vulnerability

Please do not open a public issue for a security problem. Use GitHub's private reporting
instead: **Security → Report a vulnerability** on this repository. Include what you found, how to
reproduce it and the version or commit. You should get a reply within a few days.

## What MVX Nexus has access to

MVX Nexus reads logs, infrastructure state and source code, and can open pull requests. Run it
with that in mind:

- **Put it behind HTTPS and keep it private.** The bundled ports bind to `127.0.0.1` by default.
  See `deploy/nginx/example.conf` before exposing it.
- **Secrets are encrypted at rest.** API keys, tokens, webhook URLs and SMTP passwords saved in
  the UI are stored AES-256-GCM encrypted. The key is derived from `HOLMES_JWT_SECRET` (or
  `HOLMES_SETTINGS_KEY`), so keep `.env` safe and back it up: without it, saved secrets cannot
  be read.
- **Investigation tools are read-only.** Docker access goes through a proxy that only allows
  GET requests. CLI tools (kubectl, aws, az) run fixed, read-only commands with validated
  arguments. SQL tools reject anything but a single SELECT. Still, give every integration a
  read-only account.
- **Fix pull requests are limited.** They are opened as drafts, touch at most 10 files and 400
  lines, only inside the service's source folder, never CI, build or dot files, and only on the
  git hosts you configure. A person reviews and merges.
- **Log lines are untrusted input.** They are passed to the model as data. Treat any AI output
  as a suggestion to review, not an instruction to follow.
- **MCP servers run code.** A stdio MCP server is a program started inside the backend
  container with the settings you give it. Only add servers you trust.

## Supported versions

Fixes land on `main`. There are no long-term support branches yet.
