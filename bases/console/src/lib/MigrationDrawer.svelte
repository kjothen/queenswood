<script>
  /* MigrationDrawer — author a migration.

     The drawer edits a working copy; nothing reaches the API until
     Save. Every guard bank-api would raise is evaluated live and shown
     with its wire type, and Save stays disabled while any is present —
     the dates advisory excepted, because a draft may legitimately be
     saved without them.

     Create only for now: bank-api has no PATCH on a migration, so the
     Edit entry point on an existing one is disabled in the hero. The
     `migration` prop is the working copy an edit would seed. */

  import {
    Drawer,
    Button,
    Field,
    Input,
    ProductPicker,
    VersionList,
    MIGRATION_GUARDS,
    fmtDay,
    fmtRate,
  } from "@queenswood/ui";

  let { open = false, migration = null, products = [], population = null, onclose, onsave } =
    $props();

  let draft = $state(blank());
  let picking = $state(null);
  let saving = $state(false);

  function blank() {
    return {
      name: "",
      sourceProductId: null,
      sourceVersionIds: [],
      targetProductId: null,
      targetVersionId: null,
      notifiedOn: "",
      dueOn: "",
    };
  }

  // Seed the working copy each time the drawer opens, and drop the
  // caret into the name once the 220ms slide has settled.
  $effect(() => {
    if (!open) return;
    draft = migration
      ? {
          name: migration.name,
          sourceProductId: migration.sourceProductId,
          sourceVersionIds: [...migration.sourceVersionIds],
          targetProductId: migration.targetProductId,
          targetVersionId: migration.targetVersionId,
          notifiedOn: migration.notifiedOn ?? "",
          dueOn: migration.dueOn ?? "",
        }
      : blank();
    picking = null;
    setTimeout(() => document.getElementById("mig-name")?.focus(), 240);
  });

  const sourceProduct = $derived(
    products.find((p) => p.id === draft.sourceProductId) ?? null,
  );
  const targetProduct = $derived(
    products.find((p) => p.id === draft.targetProductId) ?? null,
  );
  const targetVersion = $derived(
    targetProduct?.versions.find((v) => v.id === draft.targetVersionId) ?? null,
  );

  // A migration's target must be the same product type as its source,
  // so the target picker only ever offers that type.
  const targetCandidates = $derived(
    sourceProduct
      ? products.filter((p) => p.type === sourceProduct.type)
      : products,
  );
  const hiddenCount = $derived(products.length - targetCandidates.length);

  function pickerRows(list) {
    return list.map((p) => ({
      id: p.id,
      name: p.name,
      type: p.type,
      publishedCount: p.publishedCount,
      accountCount: population?.byProduct?.[p.id],
    }));
  }

  function versionRows(product, { withCount }) {
    return (product?.versions ?? []).map((v) => ({
      id: v.id,
      number: v.number,
      published: v.published,
      meta: [fmtRate(v.rateBps), v.published ? null : v.status]
        .filter(Boolean)
        .join(" · "),
      from: v.effectiveFrom ? `from ${fmtDay(v.effectiveFrom)}` : null,
      right: withCount
        ? (population?.byVersion?.[v.id]?.toLocaleString("en-GB") ?? "—")
        : v.currencies.join(", "),
    }));
  }

  // Changing a product resets that side's versions: the source takes
  // every published version, the target the highest-numbered published
  // one. Changing the source to a different type retargets to that same
  // product, so the form can never sit in a type-mismatched state by
  // accident.
  function pickSource(p) {
    draft.sourceProductId = p.id;
    draft.sourceVersionIds = p.versions.filter((v) => v.published).map((v) => v.id);
    if (!targetProduct || targetProduct.type !== p.type) pickTarget(p);
    picking = null;
  }

  function pickTarget(p) {
    draft.targetProductId = p.id;
    const published = p.versions.filter((v) => v.published);
    draft.targetVersionId = published.at(-1)?.id ?? null;
    picking = null;
  }

  const allPublishedSelected = $derived(
    (sourceProduct?.versions ?? []).filter((v) => v.published).length > 0 &&
      (sourceProduct?.versions ?? [])
        .filter((v) => v.published)
        .every((v) => draft.sourceVersionIds.includes(v.id)),
  );

  function toggleAllPublished() {
    if (allPublishedSelected) draft.sourceVersionIds = [];
    else {
      draft.sourceVersionIds = (sourceProduct?.versions ?? [])
        .filter((v) => v.published)
        .map((v) => v.id);
    }
  }

  const inScope = $derived(
    population
      ? draft.sourceVersionIds.reduce(
          (sum, id) => sum + (population.byVersion?.[id] ?? 0),
          0,
        )
      : null,
  );

  // Typed guards block Save; the dates advisory does not.
  const guards = $derived.by(() => {
    const out = [];
    if (draft.sourceProductId && draft.sourceVersionIds.length === 0) {
      out.push({
        type: MIGRATION_GUARDS["source-product-not-found"],
        message: "Select at least one source version — nothing is in scope.",
      });
    }
    if (
      draft.targetVersionId &&
      draft.sourceVersionIds.includes(draft.targetVersionId)
    ) {
      out.push({
        type: MIGRATION_GUARDS["target-is-source"],
        message:
          "The target version is also a source version. Those accounts are already on the terms they would move to.",
      });
    }
    if (sourceProduct && targetProduct && sourceProduct.type !== targetProduct.type) {
      out.push({
        type: MIGRATION_GUARDS["product-type-mismatch"],
        message: `Source is a ${sourceProduct.type} product and target is a ${targetProduct.type} one.`,
      });
    }
    if (targetVersion && !targetVersion.published) {
      out.push({
        type: MIGRATION_GUARDS["target-not-published"],
        message: `The target version is ${targetVersion.status}. Only a published version can be a target.`,
      });
    }
    if (draft.notifiedOn && draft.dueOn && draft.notifiedOn > draft.dueOn) {
      out.push({
        type: MIGRATION_GUARDS["notice-after-due"],
        message: "Notice must fall before the move date.",
      });
    }
    return out;
  });

  const complete = $derived(
    Boolean(
      draft.name.trim() &&
        draft.sourceProductId &&
        draft.targetProductId &&
        draft.targetVersionId,
    ),
  );

  async function save() {
    saving = true;
    await onsave?.({ ...draft, name: draft.name.trim() });
    saving = false;
  }
</script>

<Drawer
  {open}
  onClose={onclose}
  width={460}
  kicker={migration ? "Edit" : "Define"}
  title={migration ? migration.name : "New migration"}
  sub="Changing the source or target discards the current preview — run it again before approving."
>
  <Field
    label="Name"
    htmlFor="mig-name"
    hint="Shown to your operations team and on the run report. Customers never see it."
  >
    <Input id="mig-name" bind:value={draft.name} />
  </Field>

  <hr class="sep" />

  <Field label="Source product">
    <ProductPicker
      label="Source product"
      products={pickerRows(products)}
      value={draft.sourceProductId}
      open={picking === "source"}
      ontoggle={() => (picking = picking === "source" ? null : "source")}
      onselect={pickSource}
    />
  </Field>

  {#if sourceProduct}
    <Field>
      {#snippet hint()}
        Accounts on any selected version are in scope{inScope == null
          ? ""
          : ` — ${inScope.toLocaleString("en-GB")} today`}. Accounts on the
        product's other versions are reported as
        <code>version-not-in-source</code>.
      {/snippet}
      <VersionList
        mode="multi"
        title="{sourceProduct.name} · {sourceProduct.versions.length} versions"
        action={{
          label: allPublishedSelected ? "Clear all" : "Select all published",
          onclick: toggleAllPublished,
        }}
        versions={versionRows(sourceProduct, { withCount: true })}
        selected={draft.sourceVersionIds}
        onchange={(ids) => (draft.sourceVersionIds = ids)}
      />
    </Field>
  {/if}

  <hr class="sep" />

  <Field label="Target product">
    <ProductPicker
      label="Target product"
      products={pickerRows(targetCandidates)}
      value={draft.targetProductId}
      open={picking === "target"}
      ontoggle={() => (picking = picking === "target" ? null : "target")}
      onselect={pickTarget}
      hiddenNote={hiddenCount > 0
        ? `${hiddenCount} product${hiddenCount === 1 ? "" : "s"} hidden — a migration's target must be the same product type as its source (${sourceProduct?.type}).`
        : undefined}
    />
  </Field>

  {#if targetProduct}
    <Field hint="Only a published version can be a target.">
      <VersionList
        mode="single"
        name="mig-target-version"
        title="{targetProduct.name} · {targetProduct.versions.length} versions"
        versions={versionRows(targetProduct, { withCount: false })}
        selected={draft.targetVersionId}
        onchange={(id) => (draft.targetVersionId = id)}
      />
    </Field>
  {/if}

  <hr class="sep" />

  <Field hint="Notice must fall before the move date. Both are required to approve.">
    <div class="dates">
      <label class="date">
        <span>Notify customers on</span>
        <input class="input" type="date" bind:value={draft.notifiedOn} />
      </label>
      <label class="date">
        <span>Accounts move on</span>
        <input class="input" type="date" bind:value={draft.dueOn} />
      </label>
    </div>
  </Field>

  {#each guards as g}
    <div class="violation">
      <svg
        viewBox="0 0 16 16"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <circle cx="8" cy="8" r="6.2" />
        <path d="M8 4.8 V8.6 M8 10.8 v0.01" />
      </svg>
      <span>
        {g.message}
        <span class="violation-type">{g.type}</span>
      </span>
    </div>
  {/each}

  <p class="closing">
    Saving does not move anything. Run a preview afterwards to see the effect of
    these settings on the live population.
  </p>

  {#snippet footer()}
    <div class="foot-row">
      <Button size="lg" block onclick={onclose}>Discard changes</Button>
      <Button
        variant="primary"
        size="lg"
        block
        disabled={saving || guards.length > 0 || !complete}
        onclick={save}
      >
        {migration ? "Save migration" : "Create migration"}
      </Button>
    </div>
  {/snippet}
</Drawer>

<style>
  .sep {
    border: none;
    border-top: 1px solid var(--rule-2);
    margin: 4px 0;
  }
  .foot-row {
    display: flex;
    gap: 12px;
  }
  .foot-row > :global(*) {
    flex: 1;
  }
  .dates {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 14px;
  }
  .date {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .date span {
    font-family: var(--grotesk);
    font-size: 13px;
    font-weight: 500;
    color: var(--fg);
  }

  .violation {
    display: flex;
    align-items: flex-start;
    gap: 9px;
    padding: 10px 12px;
    border-radius: 6px;
    background: light-dark(oklch(0.95 0.035 30), oklch(0.27 0.05 30));
    color: light-dark(oklch(0.4 0.115 30), oklch(0.86 0.1 30));
    font-size: 12.5px;
    line-height: 1.45;
  }
  .violation svg {
    width: 15px;
    height: 15px;
    flex: 0 0 auto;
    margin-top: 1px;
  }
  .violation-type {
    display: block;
    margin-top: 3px;
    font-family: var(--mono);
    font-size: 10.5px;
    opacity: 0.8;
  }

  .closing {
    margin: 0;
    font-size: 12.5px;
    line-height: 1.5;
    color: var(--fg-muted);
  }
</style>
