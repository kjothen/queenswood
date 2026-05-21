<script>
  /* Modal renderer for markdown docs imported at build time via Vite's
     `?raw` query. Rewrites relative `.md` links to GitHub URLs so
     cross-doc references still resolve from inside the modal. */

  import { marked } from "marked";

  let { doc, onClose } = $props();

  const githubBase = "https://github.com/repldriven/queenswood/blob/main/";

  function resolveRelative(href, docPath) {
    // docPath e.g. "docs/adr/0013-single-unified-api.md"
    // href    e.g. "../tdd/scenario-testing.md"  or  "./0005-error-handling-with-anomalies.md"
    const docDir = docPath.substring(0, docPath.lastIndexOf("/"));
    const combined = (docDir ? docDir + "/" : "") + href;
    const out = [];
    for (const seg of combined.split("/")) {
      if (seg === "..") out.pop();
      else if (seg !== "." && seg !== "") out.push(seg);
    }
    return githubBase + out.join("/");
  }

  function renderMarkdown(raw, docPath) {
    const renderer = new marked.Renderer();
    const origLink = renderer.link.bind(renderer);
    renderer.link = function (token) {
      let { href } = token;
      const isAbsolute =
        /^https?:|^mailto:|^tel:/.test(href) || href.startsWith("#");
      if (!isAbsolute && docPath) {
        const githubUrl = resolveRelative(href, docPath);
        return `<a href="${githubUrl}" target="_blank" rel="noreferrer">${
          token.text
        }</a>`;
      }
      return origLink(token);
    };
    return marked.parse(raw, { renderer, gfm: true, breaks: false });
  }

  let renderedHtml = $derived(
    doc ? renderMarkdown(doc.raw, doc.path) : "",
  );
  let githubUrl = $derived(doc ? githubBase + doc.path : "");

  function handleBackdrop(e) {
    if (e.target === e.currentTarget) onClose?.();
  }

  function handleKeydown(e) {
    if (e.key === "Escape" && doc) onClose?.();
  }
</script>

<svelte:window onkeydown={handleKeydown} />

{#if doc}
  <div
    class="backdrop"
    role="dialog"
    aria-modal="true"
    aria-label={doc.label}
    onclick={handleBackdrop}
  >
    <div class="modal">
      <header class="modal-header">
        <span class="modal-label">{doc.label}</span>
        <button class="modal-close" onclick={onClose} aria-label="Close">×</button>
      </header>
      <div class="modal-body doc-content">
        {@html renderedHtml}
      </div>
      <footer class="modal-footer">
        <a href={githubUrl} target="_blank" rel="noreferrer">View on GitHub ↗</a>
      </footer>
    </div>
  </div>
{/if}

<style>
  .backdrop {
    position: fixed;
    inset: 0;
    background: rgba(20, 15, 10, 0.55);
    backdrop-filter: blur(4px);
    z-index: 100;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 32px;
  }
  .modal {
    background: var(--surface-raised);
    color: var(--fg);
    border-radius: 12px;
    box-shadow: 0 32px 80px -16px rgba(20, 15, 10, 0.5);
    width: min(880px, 100%);
    max-height: 85vh;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  .modal-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 24px;
    border-bottom: 1px solid var(--rule-2);
  }
  .modal-label {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    color: var(--gold-deep);
  }
  .modal-close {
    background: transparent;
    border: none;
    font-size: 24px;
    line-height: 1;
    color: var(--fg-muted);
    cursor: pointer;
    padding: 4px 8px;
    border-radius: 4px;
    transition: background 0.12s, color 0.12s;
  }
  .modal-close:hover {
    background: var(--hover-overlay);
    color: var(--fg);
  }
  .modal-body {
    overflow-y: auto;
    padding: 28px 40px;
    line-height: 1.6;
  }
  .modal-footer {
    padding: 14px 24px;
    border-top: 1px solid var(--rule-2);
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.16em;
    text-transform: uppercase;
    color: var(--fg-muted);
  }
  .modal-footer a {
    color: var(--fg);
    border-bottom: 1px solid var(--rule);
    padding-bottom: 1px;
  }
  .modal-footer a:hover {
    border-color: var(--fg);
  }

  /* Rendered markdown styles — scoped to the modal body */
  .doc-content :global(h1) {
    font-family: var(--serif);
    font-weight: 500;
    font-size: 32px;
    line-height: 1.15;
    letter-spacing: -0.01em;
    margin: 0 0 16px;
  }
  .doc-content :global(h2) {
    font-family: var(--serif);
    font-weight: 500;
    font-size: 24px;
    line-height: 1.2;
    letter-spacing: -0.005em;
    margin: 28px 0 12px;
  }
  .doc-content :global(h3) {
    font-family: var(--serif);
    font-weight: 500;
    font-size: 19px;
    line-height: 1.25;
    margin: 22px 0 10px;
  }
  .doc-content :global(h4) {
    font-family: var(--grotesk);
    font-weight: 500;
    font-size: 15px;
    margin: 18px 0 8px;
  }
  .doc-content :global(p) {
    margin: 0 0 14px;
    font-size: 15px;
    color: var(--fg-2);
  }
  .doc-content :global(ul),
  .doc-content :global(ol) {
    margin: 0 0 14px;
    padding-left: 22px;
    font-size: 15px;
    color: var(--fg-2);
  }
  .doc-content :global(li) {
    margin: 4px 0;
  }
  .doc-content :global(li p) {
    margin: 0;
  }
  .doc-content :global(strong) {
    font-weight: 600;
    color: var(--fg);
  }
  .doc-content :global(em) {
    font-style: italic;
  }
  .doc-content :global(a) {
    color: var(--fg);
    border-bottom: 1px solid var(--gold-deep);
    padding-bottom: 1px;
  }
  .doc-content :global(a:hover) {
    border-bottom-color: var(--fg);
  }
  .doc-content :global(code) {
    font-family: var(--mono);
    font-size: 13px;
    background: var(--hover-overlay);
    padding: 1px 6px;
    border-radius: 3px;
  }
  .doc-content :global(pre) {
    margin: 0 0 14px;
    padding: 16px 18px;
    background: var(--fg);
    color: #e9e3d3;
    border-radius: 8px;
    overflow-x: auto;
    font-family: var(--mono);
    font-size: 13px;
    line-height: 1.55;
  }
  .doc-content :global(pre code) {
    background: transparent;
    padding: 0;
    color: inherit;
    font-size: inherit;
  }
  .doc-content :global(blockquote) {
    margin: 0 0 14px;
    padding: 4px 14px;
    border-left: 3px solid var(--gold-deep);
    color: var(--fg-muted);
    font-style: italic;
  }
  .doc-content :global(hr) {
    border: 0;
    border-top: 1px solid var(--rule);
    margin: 24px 0;
  }
  .doc-content :global(table) {
    width: 100%;
    border-collapse: collapse;
    margin: 0 0 14px;
    font-size: 14px;
  }
  .doc-content :global(th),
  .doc-content :global(td) {
    text-align: left;
    padding: 8px 12px;
    border-bottom: 1px solid var(--rule-2);
  }
  .doc-content :global(th) {
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.14em;
    text-transform: uppercase;
    color: var(--fg-muted);
    font-weight: 500;
  }

  @media (max-width: 640px) {
    .backdrop { padding: 12px; }
    .modal-body { padding: 20px 22px; }
  }
</style>
