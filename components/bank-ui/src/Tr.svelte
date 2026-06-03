<script>
  /* Tr — table row.

     Plain rows need no props. Tree-table rows opt in:

       expandable  account (parent) row — clickable, focusable, role=button.
                   Pair with `expanded` to reflect open state.
       expanded    drives the chevron rotation + (consumer-controlled)
                   visibility of the balance rows beneath.
       balance     balance (child) row — sunk background, indented.
       last        the final balance under an account — firmer closing rule.

       <Tr expandable expanded={open} onclick={() => open = !open}> … </Tr>
       {#if open}
         {#each acc.balances as b, i}
           <Tr balance last={i === acc.balances.length - 1}> … </Tr>
         {/each}
       {/if} */

  let {
    expandable = false,
    expanded = false,
    balance = false,
    last = false,
    children,
    ...rest
  } = $props();
</script>

<tr
  class:qw-account={expandable}
  class:qw-balance={balance}
  class:qw-balance-last={balance && last}
  role={expandable ? "button" : undefined}
  tabindex={expandable ? 0 : undefined}
  aria-expanded={expandable ? expanded : undefined}
  {...rest}
>{@render children?.()}</tr>
