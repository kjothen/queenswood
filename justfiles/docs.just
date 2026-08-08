EXPORT_DIAGRAM := "components/excalidraw/src/export.mjs"

# Install the exporter's npm deps and its headless Chromium. Both are
# idempotent, and run unconditionally so a lockfile bump is picked up.
[private]
diagram-deps:
    #!/usr/bin/env zsh
    set -euo pipefail
    cd components/excalidraw
    npm install --silent
    npx playwright install chromium

# Export one docs/diagrams scene to light + dark SVGs (no args = the system diagram)
diagram file="System Diagram.excalidraw": diagram-deps
    #!/usr/bin/env zsh
    set -euo pipefail
    # Rendered headlessly through Excalidraw's own exportToSvg, so fonts
    # and hand-drawn strokes match the editor. Each SVG lands beside its
    # scene as <slug>-light.svg and <slug>-dark.svg.
    node {{ EXPORT_DIAGRAM }} docs/diagrams/{{ quote(file) }}

# Export every docs/diagrams/*.excalidraw scene to light + dark SVGs.
diagrams: diagram-deps
    #!/usr/bin/env zsh
    set -euo pipefail
    node {{ EXPORT_DIAGRAM }} docs/diagrams/*.excalidraw
