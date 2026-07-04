# AGENTS.md — pvcli

> Working notes for AI agents (and humans) working on the `pvcli` Babashka
> CLI. For a user-facing description, see [README.md](README.md).

## What this is

A single-binary Babashka CLI that wraps the three HTTP APIs of the
Pseudovision media-automation ecosystem:

| Service | Local name in `pvcli` | Public URL (prod) |
|---|---|---|
| Pseudovision | `pv` | `https://pseudovision.kube.sea.fudo.link` |
| Tunarr Scheduler | `ts` | `https://tunarr-scheduler.kube.sea.fudo.link` |
| Grout | `grout` | `http://grout.pseudovision.svc.cluster.local:8080` (internal) |

It is **not** a generic OpenAPI client generator. It encodes the conventions
and quirks of these three services (kebab-case JSON, dim/media/filler schemas,
cron-driven scheduling) into friendly subcommands. Each subcommand is a thin
wrapper around a hand-picked subset of the underlying HTTP routes.

## Layout

```
pvcli/
├── bin/pvcli             Entry point. Shebang `#!/usr/bin/env bb`, dispatches to src/pvcli/main.
├── src/pvcli/
│   ├── main.bb           -main entry; cli/dispatch + try/catch.
│   ├── cli.bb            Top-level dispatch (service selection, --version, --help).
│   ├── config.bb         Config + env loading. One source-of-truth map per service.
│   ├── http.bb           HTTP client wrapper. Auth, JSON encode/decode, error mapping.
│   ├── output.bb         JSON (default) and --human table output.
│   ├── command.bb        Shared command-tree dispatch (used by pv/ts/grout).
│   ├── pv.bb             Pseudovision subcommands.
│   ├── ts.bb             Tunarr Scheduler subcommands.
│   └── grout.bb          Grout subcommands.
├── tests/                bb-based unit tests (run via `nix flake check` or `bb test`).
├── flake.nix             Nix build (mkDerivation + makeWrapper).
└── flake.lock            (generate + commit with `nix flake lock`; not yet pinned)
```

## Conventions

- **One file per service module** (`pv.bb`, `ts.bb`, `grout.bb`). Each exports
  a `dispatch` function (called by `cli.bb`) and a `help` string.
- **JSON by default.** Always emit a JSON-serialisable value. `--human` is the
  only way to get a table. Errors go to stderr with a non-zero exit code.
- **Kebab-case in, kebab-case out.** Both PV and the scheduler's HTTP contracts
  are kebab-case JSON. Grout uses kebab-case too. No name munging at the
  boundary — the user's JSON is what the server's JSON is.
- **Config resolution order**: env var > config file > error. The config map
  shape is `{:service {:url ... :api-key ...}}`. URL is required; API key is
  optional (only Tunarr Scheduler needs one for the public-facing endpoints).
- **Errors are `ex-info` with a `:status` key when the response was an HTTP
  error.** The CLI prints `ex-message` to stderr and exits non-zero.
- **No retries in v1.** Add a `--retry` flag later if needed. v1 fails fast
  so the user sees the actual error and can fix it.
- **No global state.** Every subcommand is a pure function of (args, config).
  This makes the CLI easy to test and to compose with other tools (`xargs`,
  `jq`, scripts).

## Subcommand dispatch

`cli.bb` is a simple router: it inspects the first non-option arg and calls
the matching `dispatch` function. Subcommands use `babashka.cli` for arg
parsing. Don't reimplement arg parsing — use the spec map.

## Adding a new subcommand

1. Pick the right service module (`pv.bb`, `ts.bb`, `grout.bb`).
2. Add a top-level key to its `dispatch` map.
3. Add a `:help` entry (one line, used by `pvcli <service> --help`).
4. Add a test in `tests/` that exercises the happy path with a mocked
   `babashka.http-client/request` (use `with-redefs`).
5. If the command needs a new config knob, add it to `config.bb`'s defaults
   AND document it in the README.

## Cross-service references

The Pseudovision ecosystem is documented in detail in the
`pseudovision-ecosystem-development` skill (in the
`fudo-hermes/skills` collection). If you need to understand why a service
behaves a certain way, that's the right place to look. Key patterns:

- **Kebab-case JSON everywhere** (Pitfall 8 — don't reformat names).
- **Cron-driven scheduling** (Issue 1 in the skill — there's no batch POST).
- **Grout intake is multipart** (since `5b947af`).
- **Scheduler uses display name as the storage key** (Pitfall 22 — slug vs
  display name vs PV id; the boundary translation lives in
  `tunarr-scheduler#96`).

## Testing

`nix flake check` runs `tests/run.bb` in a derivation. Locally:

```bash
nix develop
bb tests/run.bb
```

Tests use `babashka.test` and `with-redefs` to mock the HTTP client. No
network access required.

## OpenAPI / spec regeneration

Each service exposes (or should expose) an OpenAPI spec at `/openapi.json`.
When a new endpoint is added, regenerate the CLI command by reading the
spec — but **don't auto-generate** the CLI surface. The CLI is a curated
view, not a 1:1 mapping. Add a command only when a user actually needs it.
