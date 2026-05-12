# Authenticate gcloud and pin the active project to $GOOGLE_CLOUD_PROJECT_ID
gcp-login:
    gcloud auth login
    gcloud config set project $GOOGLE_CLOUD_PROJECT_ID

# Login to GCP impersonating the crossplane SA
gcp-sa-login:
    #!/usr/bin/env zsh
    set -e    
    PROJECT_ID=$GOOGLE_CLOUD_PROJECT_ID
    SA=crossplane-provider
    SA_EMAIL=${SA}@${PROJECT_ID}.iam.gserviceaccount.com
    
    gcloud auth application-default login \
      --impersonate-service-account=$SA_EMAIL
    gcloud config set project $GOOGLE_CLOUD_PROJECT_ID

# Provision the crossplane-provider service account, IAM bindings, key, and APIs
gcp-bootstrap:
    #!/usr/bin/env zsh
    set -e

    PROJECT_ID=$GOOGLE_CLOUD_PROJECT_ID
    SA=crossplane-provider
    SA_EMAIL=${SA}@${PROJECT_ID}.iam.gserviceaccount.com
    ME=$(gcloud config get-value account)

    gcloud config set project $PROJECT_ID

    # Enable the APIs Crossplane will need
    gcloud services enable \
      container.googleapis.com \
      compute.googleapis.com \
      dns.googleapis.com \
      iam.googleapis.com \
      iamcredentials.googleapis.com \
      cloudresourcemanager.googleapis.com

    # Create the service account Crossplane will act as
    gcloud iam service-accounts create $SA \
      --display-name "Crossplane Provider"

    # Grant the SA the roles it needs to manage GKE + networking + DNS
    for role in \
      roles/container.admin \
      roles/compute.networkAdmin \
      roles/dns.admin \
      roles/iam.serviceAccountUser \
      roles/resourcemanager.projectIamAdmin ; do
      gcloud projects add-iam-policy-binding $PROJECT_ID \
        --member="serviceAccount:${SA_EMAIL}" \
        --role="$role"
    done

    # Grant yourself permission to impersonate the SA (replaces key creation)
    gcloud iam service-accounts add-iam-policy-binding $SA_EMAIL \
      --member="user:${ME}" \
      --role="roles/iam.serviceAccountTokenCreator"

# Print Cloud DNS nameservers for the queenswood zone (paste into registrar NS records)
gcp-dns-ns:
    kubectl --context kind-xp-mp get managedzone.dns.gcp.upbound.io queenswood-zone \
      -o jsonpath='{.status.atProvider.nameServers}{"\n"}'
