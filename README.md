# pvcli

A Babashka CLI for the **Pseudovision media-automation ecosystem**: Pseudovision,
Tunarr Scheduler, and Grout. One binary, three services, JSON by default.

```bash
# list Pseudovision channels
pvcli pv channels list

# inspect a Tunarr Scheduler plan for a channel
pvcli ts plan --channel goldenreels

# tag and intake a clip into Grout
pvcli grout intake --tags=kids,daytime --kind=filler bumper.mp4
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

`pvcli` reads `~/.config/pvcli/config.edn` by default. Generate a starter:

```toml
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
  grout    Grout (filler media store: intake, tags, query, by-hash)
```

Run `pvcli <service> --help` for subcommand help.

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

> **Note:** this repo does not yet commit a `flake.lock`. Run `nix flake lock`
> and commit the result so `nix run github:fudoniten/pseudovision-cli` resolves
> its inputs reproducibly.

## License

Personal project — all rights reserved by the author.
