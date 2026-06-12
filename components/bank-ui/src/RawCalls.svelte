<script>
  /* RawCalls — a dark terminal block that reveals the underlying API
     calls behind a friendly step list. Each `row` is
     `{ method, path, tag }` (the HTTP verb, the path, and the runner
     verb tag — request / poll / mint-token / wait); `backing` lists the
     scenario id(s) the steps are drawn from. Hidden until `show`. */

  let { rows = [], backing = [], show = false } = $props();
</script>

<div class="rawcalls" class:show>
  {#each rows as r, i (r.method + r.path + i)}
    <div class="raw-row">
      <span class="raw-verb">
        <span class="raw-method">{r.method}</span>
        <span class="raw-path">{r.path}</span>
      </span>
      <span class="raw-tag">:{r.tag}</span>
    </div>
  {/each}
  <div class="raw-foot">
    <span class="rf-label">backing scenario{backing.length > 1 ? "s" : ""}:</span>
    {backing.join(" · ")}
  </div>
</div>

<style>
  .rawcalls {
    display: none;
    border: 1px solid var(--rule-2);
    border-radius: 8px;
    overflow: hidden;
    background: light-dark(#1d1a16, #161310);
  }
  .rawcalls.show { display: block; }
  .raw-row {
    display: flex;
    align-items: baseline;
    gap: 12px;
    padding: 7px 14px;
    border-bottom: 1px solid rgba(244, 241, 234, 0.07);
    font-family: var(--mono);
    font-size: 12px;
  }
  .raw-row:last-child { border-bottom: none; }
  .raw-verb { display: inline-flex; align-items: baseline; gap: 12px; }
  .raw-method {
    display: inline-block;
    min-width: 78px;
    color: var(--gold-bright);
    letter-spacing: 0.04em;
  }
  .raw-path { color: #cfc8bb; }
  .raw-tag {
    margin-left: auto;
    color: #7e776b;
    font-size: 11px;
    white-space: nowrap;
  }
  .raw-foot {
    padding: 8px 14px;
    font-family: var(--mono);
    font-size: 11px;
    color: #8a8377;
    border-top: 1px solid rgba(244, 241, 234, 0.07);
    display: flex;
    gap: 8px;
    align-items: center;
  }
  .raw-foot .rf-label { color: #6f685d; }
</style>
