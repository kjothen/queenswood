CODE := env_var_or_default("QW_CODE", "qw01")
MGMT_CTX := CODE + "-mgmt"
INSTALLATIONS_REPO := env_var_or_default("QW_INSTALLATIONS_REPO", "../installations")

# One region, said three ways, and the three must agree. GCP publishes
# no short form, so REGION_CODE is ours.
REGION := "europe-west2"
REGION_CODE := "euw2"
ZONE := REGION + "-a"

ORG_ADMIN := env_var_or_default("QW_ORG_ADMIN", "grp-gcp-org-admin")
FOLDER_ADMIN := env_var_or_default("QW_FOLDER_ADMIN", "grp-gcp-folder-admin")
SECURITY_REVIEWER := env_var_or_default("QW_SECURITY_REVIEWER", "grp-gcp-security-reviewer")
ORG_BILLING_ADMIN_GROUP := env_var_or_default("QW_ORG_BILLING_ADMIN_GROUP", "grp-gcp-billing-admin")

PLATFORM_VIEWER := env_var_or_default("QW_PLATFORM_VIEWER", "grp-gcp-" + CODE + "-platform-viewer")
PLATFORM_ADMIN := env_var_or_default("QW_PLATFORM_ADMIN", "grp-gcp-" + CODE + "-platform-admin")
CLUSTER_ADMIN := env_var_or_default("QW_CLUSTER_ADMIN", "grp-gcp-" + CODE + "-cluster-admin")
SECRETS_ADMIN := env_var_or_default("QW_SECRETS_ADMIN", "grp-gcp-" + CODE + "-secrets-admin")
