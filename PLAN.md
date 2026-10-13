# pvcli plan

Last reviewed: 2026-10-13

This is the one place for ongoing work on pvcli: what's being worked on, what's
next, known issues, and the decisions that still shape the code. Delete items
once they're done (git history keeps them). Cross-repo priorities live in the
[ecosystem plan](https://github.com/fudoniten/pseudovision/blob/master/PLAN.md#ecosystem).

## Now

- [ ] Remove `ts strategies current` when Tunarr Scheduler deletes its
      strategies endpoints (ecosystem step 3).
- [ ] Remove `ts scheduling run-weekly` when TS retires the weekly DailySlots
      push (ecosystem step 3).

## Next

- [ ] Grout commands: either implement `grout media list|get|by-hash`,
      `grout intake` and `grout tags add` (and retire Grout's own `grout-cli`),
      or drop the stubs and point at `grout-cli`. Today only `grout info` works.

## Later

- Merge pvcli and `grout-cli` into one CLI (follows from the Grout decision above).
- Once services enforce auth, confirm the `Authorization`/`X-Api-Key` headers
  pvcli already sends match what they check.

## Known issues

- The `pv.bb` docstring's "coverage" list was checked against the live OpenAPI
  in July 2026 and may have drifted.
- The `ts.bb` docstring says TS keys grids by display name; TS's `tasks.clj`
  now keys them by the TS channel UUID.

## Decisions

- 2026-07: The CLI is a curated view of the service APIs, not generated from
  OpenAPI. Add a command only when someone needs it.
