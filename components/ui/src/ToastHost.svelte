<script>
  /* ToastHost — renders whatever `toast()` last published. Mount once,
     in the app shell; call `toast(message, detail)` from anywhere.

     The pill is inverted against the page so it reads as chrome rather
     than content, and `detail` trails in mono for the figure an
     operator actually wants ("1,204 held back"). */

  import { fly } from "svelte/transition";
  import { toastState } from "./toast.svelte.js";
</script>

<div class="qw-toast-wrap" aria-live="polite">
  {#if toastState.current}
    {#key toastState.current.id}
      <div class="qw-toast" transition:fly={{ y: 12, duration: 180 }}>
        <span>{toastState.current.message}</span>
        {#if toastState.current.detail}
          <span class="qw-toast-detail">{toastState.current.detail}</span>
        {/if}
      </div>
    {/key}
  {/if}
</div>

<style>
  .qw-toast-wrap {
    position: fixed;
    bottom: 24px;
    left: 50%;
    transform: translateX(-50%);
    z-index: 80;
    pointer-events: none;
  }
  .qw-toast {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 11px 16px;
    border-radius: 8px;
    background: light-dark(#161310, #f4f1ea);
    color: light-dark(#f4f1ea, #161310);
    font-family: var(--grotesk);
    font-size: 13px;
    white-space: nowrap;
    box-shadow: 0 8px 28px -8px rgba(0, 0, 0, 0.45);
  }
  .qw-toast-detail {
    font-family: var(--mono);
    font-size: 11.5px;
    font-variant-numeric: tabular-nums;
    opacity: 0.7;
  }
  @media (prefers-reduced-motion: reduce) {
    .qw-toast {
      transition: none;
    }
  }
</style>
