# Tessl plugins: the rules an agent loads, and the copy it loads them
# from.
#
# `plugins/<name>/rules/<name>.md` is the tracked source. AGENTS.md
# loads `.tessl/RULES.md`, which points at
# `.tessl/plugins/queenswood/<name>/rules/<name>.md` -- a gitignored,
# per-machine copy that only an install refreshes. Editing the source
# alone changes nothing any agent reads, and shows no diff, which is
# what `tessl-plugins-check` is for.

# Install every plugin from the working tree, not the registry.
tessl-plugins-install:
    #!/usr/bin/env zsh
    set -e
    for d in ./plugins/*/; do
      tessl install "file:${d%/}"
    done

# Whether the installed rules match the tracked sources.
tessl-plugins-check:
    #!/usr/bin/env zsh
    set -e
    # A clone that has never installed has nothing to compare, which is
    # not drift.
    if [[ ! -d .tessl/plugins/queenswood ]]; then
      echo "no installed plugins: run just tessl-plugins-install"
      exit 0
    fi
    STALE=()
    for SRC in plugins/*/rules/*.md; do
      NAME=$(basename "$SRC")
      DEST=".tessl/plugins/queenswood/${NAME%.md}/rules/$NAME"
      if [[ ! -f $DEST ]]; then
        STALE+=("$NAME (never installed)")
      elif ! diff -q "$SRC" "$DEST" >/dev/null; then
        STALE+=("$NAME")
      fi
    done
    if (( ${#STALE[@]} == 0 )); then
      echo "installed rules match their sources"
      exit 0
    fi
    for S in $STALE; do
      echo "stale: $S"
    done
    echo
    echo "The rules agents load are behind the tree."
    echo "Run: just tessl-plugins-install"
    exit 1
