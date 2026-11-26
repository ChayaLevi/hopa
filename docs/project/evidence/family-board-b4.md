# Family board B4: smoke run against staging

The board's append and pull latency on a deployed instance (family board plan,
task B4.7). The run appends N random `family` ops over the gateway and pulls
them back over HTTP; see
[the board's server side](../../design/architecture/family-board.md).

## How to run

1. Pair a test device into a **test household** on staging and copy its access
   token (the ops stay in that household's board, and no device can decrypt them).
2. From the repository root:

   ```powershell
   apps\android\gradlew.bat -p services/backend boardSmoke --args="--url https://<staging host> --token <access token> --ops 100"
   ```

3. Paste the three output lines below, with the date, the instance and the
   commit it runs.

## Results

Status: **not run yet (owner to run).**

| Date | Instance | Commit | Ops | Append p50 | Append p95 | Pull page p50 | Pull page p95 |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

The same tool runs in the backend suite against a local server
(`BoardSmokeTest`), which proves it works but says nothing about staging latency.
