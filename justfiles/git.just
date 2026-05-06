# Sync upstream changes
sync-upstream:
  chmod +x {{ justfile_directory() }}/scripts/sync-upstream.sh
  {{ justfile_directory() }}/scripts/sync-upstream.sh

# Remove the exemplar and configure for a new domain
fork domain:
    bb scripts/fork-domain.bb {{ domain }}
