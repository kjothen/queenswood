# Export an Excalidraw scene to transparent light + dark SVGs, rendered
# headlessly via Excalidraw's own exportToSvg so fonts and strokes match
# the editor. Output lands beside the source in docs/diagrams as
# <slug>-light.svg and <slug>-dark.svg. Defaults to the system diagram;
# pass a filename for another scene, e.g. `just diagram Idempotency.excalidraw`.
diagram file="System Diagram.excalidraw":
    #!/usr/bin/env zsh
    set -euo pipefail
    cd docs/diagrams/tooling
    [[ -d node_modules ]] || npm install
    node export-diagrams.mjs {{ quote(file) }}

# Export every docs/diagrams/*.excalidraw scene to light + dark SVGs.
diagrams:
    #!/usr/bin/env zsh
    set -euo pipefail
    cd docs/diagrams/tooling
    [[ -d node_modules ]] || npm install
    node export-diagrams.mjs
