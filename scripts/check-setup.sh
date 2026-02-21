#!/usr/bin/env bash
# Run after cloning to verify the development environment prerequisites.
set -euo pipefail

ok=true

echo "Checking development prerequisites..."
echo ""

# direnv
if ! command -v direnv &>/dev/null; then
  echo "✗ direnv not found"
  echo "  Install:  brew install direnv  (macOS)"
  echo "            See https://direnv.net/docs/installation.html for Linux"
  echo ""
  echo "  Then add the shell hook to your shell config, e.g. for zsh:"
  echo "    echo 'eval \"\$(direnv hook zsh)\"' >> ~/.zshrc"
  ok=false
elif [ -z "${DIRENV_DIR:-}" ]; then
  echo "✗ direnv is installed but the shell hook is not active"
  echo "  Add to your shell config (e.g. ~/.zshrc for zsh):"
  echo "    eval \"\$(direnv hook zsh)\""
  echo "  Then restart your shell and run: direnv allow"
  ok=false
else
  echo "✓ direnv"
fi

# nix
if ! command -v nix &>/dev/null; then
  echo "✗ nix not found"
  echo "  Install:  curl --proto '=https' --tlsv1.2 -sSf -L \\"
  echo "              https://install.determinate.systems/nix | sh -s -- install"
  ok=false
else
  echo "✓ nix"
fi

echo ""
if [ "$ok" = "true" ]; then
  echo "All prerequisites satisfied."
  echo "Run 'direnv allow' in this directory to activate the environment."
else
  echo "Fix the above, then re-run this script."
  exit 1
fi
