# pvcli

A Babashka CLI for the **Pseudovision media-automation ecosystem**: Pseudovision,
Tunarr Scheduler, and Grout. One binary, three services, JSON by default.

Ongoing work and known gaps are tracked in [PLAN.md](PLAN.md). Notes for
contributors (layout, conventions, adding a command) are in
[AGENTS.md](AGENTS.md).

```bash
# list Pseudovision channels
pvcli pv channels list

# inspect the Tunarr Scheduler planning doc for a channel
pvcli ts channel plan goldenreels

# check that Grout is reachable
pvcli grout info
```

## Install

```bash
# Run directly from the repo (no install):
nix run github:fudoniten/pseudovision-cli -- --help

# Or build to your profile:
nix profile install github:fudoniten/pseudovision-cli
pvcli --version
```

## Configuration

`pvcli` reads `~/.config/pvcli/config.edn` by default. A starter config:

```clojure
{:pv    {:url "https://pseudovision.kube.sea.fudo.link"   :api-key "..."}
 :ts    {:url "https://tunarr-scheduler.kube.sea.fudo.link" :api-key "..."}
 :grout {:url "http://grout.pseudovision.svc.cluster.local:8080"}}
```

Override any value via env: `PVCLI_PV_URL`, `PVCLI_PV_API_KEY`, `PVCLI_TS_URL`,
`PVCLI_TS_API_KEY`, `PVCLI_GROUT_URL`. The first non-nil source wins.

## Output

JSON to stdout by default — pipe through `jq`:

```bash
pvcli pv channels list | jq '.[] | select(.group_name == "Testing")'
```

For terminal reading, add `--human` (or `-H`):

```bash
pvcli pv channels list --human
```

Per-command errors go to stderr with a non-zero exit code.

## Exit codes

| Code | Meaning                                                        |
|------|----------------------------------------------------------------|
| 0    | Success                                                        |
| 1    | Runtime / HTTP error (a request reached a service and failed)  |
| 2    | Usage or config error (unknown service/command, no usable URL) |

## Commands

```
pvcli <service> <command> [options] [args]

Services:
  pv       Pseudovision (channels, media, catalog, scheduling, filler)
  ts       Tunarr Scheduler (scheduling, channels, plans, strategies)
  grout    Grout (filler media store; only `info` works today)
```

Run `pvcli <service> --help` for subcommand help. The Grout `media`, `intake`
and `tags` commands are placeholders that return "not implemented"; use
Grout's own `grout-cli` for those until they land (see [PLAN.md](PLAN.md)).

## Development

```bash
# Run from a local checkout
nix run . -- --version
nix run .#pvcli -- pv channels list

# Dev shell with babashka + clj-kondo
nix develop

# Run the test suite directly
bb tests/run.bb
```

CI (`.github/workflows/ci.yml`) runs `nix flake check`, which runs the same
test suite.

## License

Personal project — all rights reserved by the author.
