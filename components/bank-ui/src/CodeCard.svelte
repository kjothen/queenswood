<script>
  /* CodeCard — dark code block with title bar and syntax tokens.

     Always dark, in both themes (terminal aesthetic). For syntax
     highlighting, wrap tokens in spans with these classes:

       .syn-comment   .syn-keyword   .syn-string
       .syn-number    .syn-symbol    .syn-fn
       .syn-punct     .syn-emphasis  .syn-type

     If you have a build-time highlighter (Shiki / Prism), generate
     the matching spans and pass the result as children. If you don't,
     class spans by hand — the colors are designed to look intentional
     even on hand-classed snippets.

         <CodeCard filename="~/queenswood · zsh">
           <pre><span class="syn-comment"># comment</span>
     <span class="syn-keyword">curl</span> -X POST ...</pre>
         </CodeCard>
  */

  let {
    filename,
    children,
    ...rest
  } = $props();
</script>

<div class="codecard" {...rest}>
  <header class="codecard-bar">
    <span class="codecard-dots">
      <span class="dot"></span><span class="dot"></span><span class="dot"></span>
    </span>
    {#if filename}<span class="codecard-name">{filename}</span>{/if}
  </header>
  <div class="codecard-body">
    {@render children?.()}
  </div>
</div>

<style>
  .codecard {
    background: var(--ink);
    color: rgba(244, 241, 234, 0.88);
    border-radius: 12px;
    overflow: hidden;
    font-family: var(--mono);
    font-size: 13.5px;
    line-height: 1.6;
    box-shadow:
      0 0 0 1px rgba(255, 255, 255, 0.05) inset,
      0 18px 48px -16px rgba(0, 0, 0, 0.35);
  }
  .codecard-bar {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 16px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.07);
    font-family: var(--mono);
    font-size: 12px;
    color: rgba(244, 241, 234, 0.6);
  }
  .codecard-dots { display: flex; gap: 6px; }
  .codecard-dots .dot {
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.12);
  }
  .codecard-name {
    font-family: var(--mono);
    font-size: 12px;
    color: rgba(244, 241, 234, 0.55);
  }
  .codecard-body {
    padding: 18px 22px;
    color: rgba(244, 241, 234, 0.88);
  }
  .codecard-body :global(pre) {
    margin: 0;
    font-family: var(--mono);
    font-size: 13.5px;
    line-height: 1.6;
    white-space: pre;
    overflow-x: auto;
    color: inherit;
  }
  /* Syntax tokens — designed for hand-classed spans. If you pipe
     output from Shiki/Prism, you'll likely need to bridge their
     class names to these (`.token.comment` → `.syn-comment`, etc). */
  .codecard-body :global(.syn-comment)  { color: rgba(244, 241, 234, 0.42); font-style: italic; }
  .codecard-body :global(.syn-keyword)  { color: oklch(0.86 0.08 90); }
  .codecard-body :global(.syn-string)   { color: oklch(0.82 0.10 145); }
  .codecard-body :global(.syn-number)   { color: oklch(0.78 0.12 60); }
  .codecard-body :global(.syn-symbol)   { color: oklch(0.85 0.05 60); }
  .codecard-body :global(.syn-fn)       { color: oklch(0.74 0.12 245); }
  .codecard-body :global(.syn-punct)    { color: rgba(244, 241, 234, 0.55); }
  .codecard-body :global(.syn-emphasis) { color: var(--gold-bright); }
  .codecard-body :global(.syn-type)     { color: oklch(0.80 0.10 320); }
</style>
