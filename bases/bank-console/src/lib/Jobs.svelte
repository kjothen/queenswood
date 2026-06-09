<script>
  /* Jobs page — the scheduled work the platform runs on a cadence.
     Each job is a preset, ordered pipeline of tasks (accrue →
     capitalize) that fires on a schedule. The page answers the
     operator's standing question at a glance: did the last run
     succeed, and when does it run next?

     The list endpoint carries each job's schedule + last/next-run
     timestamps but not the run outcome, so for the status badge we
     fetch each job's runs (newest-first) and read the head: running
     while a run is in progress, else its outcome, else "scheduled"
     (never run). Jobs are a small fixed set per bank, so the
     per-job runs fetch is cheap. Read-only for now — run-now, the
     expanded task pipeline, and schedule editing are follow-ups.

     All times are UTC. */

  import {
    PageHeader,
    Button,
    Badge,
    Table,
    Thead,
    Tbody,
    Tr,
    Th,
    Td,
    JobStatusBadge,
    humanSchedule,
    cronOf,
    nextRunAt,
    fmtAbs,
    fmtRel,
    lastOutcome,
  } from "@queenswood/bank-ui";
  import { list_jobs, list_job_runs } from "./api.mjs";

  let { user, memberships } = $props();

  let loading = $state(true);
  let error = $state(null);
  let jobs = $state([]);
  // Wall-clock the data was loaded, the anchor for every relative time
  // ("3d ago", "in 14h") so the column reads consistently within a load.
  let now = $state(Date.now());

  const kicker = $derived(memberships?.[0]?.["bank-name"]);

  // The ordered task pipeline, shown as a mono subline under the name.
  function taskSequence(job) {
    return (job["task-kinds"] ?? []).join(" → ");
  }

  // Prefer the server-computed next-run; fall back to deriving it from
  // the periodicity when the runner hasn't stamped one yet.
  function nextRun(job) {
    if (!job.enabled) return null;
    return job["next-run-at"] ?? nextRunAt(job, now);
  }

  async function load() {
    loading = true;
    error = null;
    now = Date.now();
    try {
      const res = await list_jobs();
      if (res.status < 200 || res.status >= 300) {
        error = res.body?.detail ?? `HTTP ${res.status}`;
        jobs = [];
        return;
      }
      const list = res.body?.jobs ?? [];
      // Fetch each job's latest run in parallel for the status badge +
      // last-run timing. A runs error degrades to "no run" rather than
      // failing the whole page.
      jobs = await Promise.all(
        list.map(async (job) => {
          const runsRes = await list_job_runs(job["job-id"]);
          const runs =
            runsRes.status >= 200 && runsRes.status < 300
              ? (runsRes.body?.runs ?? [])
              : [];
          const latest = runs[0] ?? null;
          return {
            id: job["job-id"],
            name: job.name,
            tasks: taskSequence(job),
            schedule: humanSchedule(job),
            cron: cronOf(job),
            enabled: job.enabled,
            outcome: lastOutcome(latest),
            lastAt: latest?.["started-at"] ?? job["last-run-at"] ?? null,
            nextAt: nextRun(job),
          };
        }),
      );
    } catch (err) {
      error = err.message;
      jobs = [];
    } finally {
      loading = false;
    }
  }

  $effect(() => {
    load();
  });
</script>

<PageHeader
  {kicker}
  title="Jobs"
  sub="Scheduled work the platform runs on a cadence. Each job runs an ordered sequence of predefined tasks — if one fails, the rest are skipped. All times UTC."
>
  {#snippet actions()}
    <Button variant="ghost" onclick={load}>Refresh</Button>
  {/snippet}
</PageHeader>

{#if error}
  <div class="alert" role="alert">{error}</div>
{/if}

{#if loading}
  <div class="loading">Loading…</div>
{:else if jobs.length === 0}
  <div class="empty">
    <p>No scheduled jobs.</p>
    <p class="hint">A bank's default jobs are seeded when the bank is provisioned.</p>
  </div>
{:else}
  <Table>
    <Thead>
      <Tr>
        <Th>Job</Th>
        <Th>Schedule</Th>
        <Th>Last run</Th>
        <Th>Next run</Th>
      </Tr>
    </Thead>
    <Tbody>
      {#each jobs as job (job.id)}
        <Tr>
          <Td emphasized>
            {job.name}
            {#if job.tasks}<span class="sub mono">{job.tasks}</span>{/if}
          </Td>
          <Td>
            {job.schedule}
            <span class="sub mono">{job.cron}</span>
          </Td>
          <Td>
            <JobStatusBadge outcome={job.outcome} />
            {#if job.lastAt}
              <span class="sub mono">
                {fmtAbs(job.lastAt)} · {fmtRel(job.lastAt, now)}
              </span>
            {/if}
          </Td>
          <Td>
            {#if !job.enabled}
              <Badge tone="archived">paused</Badge>
            {:else if job.nextAt}
              {fmtRel(job.nextAt, now)}
              <span class="sub mono">{fmtAbs(job.nextAt)}</span>
            {:else}
              <span class="muted">—</span>
            {/if}
          </Td>
        </Tr>
      {/each}
    </Tbody>
  </Table>
{/if}

<style>
  .alert {
    padding: 12px 16px;
    border: 1px solid var(--rule);
    border-radius: 6px;
    background: var(--surface-sunk);
    color: var(--fg);
    font-size: 14px;
  }
  .loading,
  .empty {
    padding: 48px 16px;
    text-align: center;
    color: var(--fg-muted);
    border: 1px dashed var(--rule);
    border-radius: 12px;
  }
  .empty p {
    margin: 0;
  }
  .empty .hint {
    margin-top: 6px;
    font-size: 13px;
  }

  /* Mono subline under a cell's primary value — the cron string, the
     task pipeline, the absolute timestamp. */
  .sub {
    display: block;
    margin-top: 3px;
    font-size: 12px;
    color: var(--fg-muted);
  }
  .sub.mono,
  .mono {
    font-family: var(--mono);
  }
  .muted {
    color: var(--fg-muted);
  }
</style>
