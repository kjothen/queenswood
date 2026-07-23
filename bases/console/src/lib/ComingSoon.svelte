<script>
  /* Placeholder for sidenav destinations whose data isn't wired up
     yet (Organizations / Parties / Accounts / Policies). Renders the
     same PageHeader chrome as a real page so the layout doesn't jump
     when the user clicks between rails. */

  import { PageHeader } from "@queenswood/ui";

  let { name, kicker } = $props();
</script>

<PageHeader
  kicker={kicker ?? "Console"}
  title={name}
  sub="Coming soon — this section will be available in a future release."
/>
