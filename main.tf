terraform {
  required_version = ">= 1.5.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.20.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

# 1. Aktivace základních Google Cloud API
resource "google_project_service" "enabled_apis" {
  for_each = toset(concat([
    "run.googleapis.com",
    "secretmanager.googleapis.com",
    "artifactregistry.googleapis.com",
    "cloudbuild.googleapis.com",
    "firestore.googleapis.com",
    "iam.googleapis.com",
    "billingbudgets.googleapis.com"
  ], var.enable_cloud_sql ? ["sqladmin.googleapis.com"] : [], var.enable_cloud_kms ? ["cloudkms.googleapis.com"] : []))
  project            = var.project_id
  service            = each.key
  disable_on_destroy = false
}

# 2. Google Cloud Artifact Registry (pro Docker obrazy O.M.N.I.S.)
resource "google_artifact_registry_repository" "omnis_repo" {
  depends_on    = [google_project_service.enabled_apis]
  location      = var.region
  repository_id = "omnis-repo"
  description   = "Docker repozitar pro O.M.N.I.S. FastAPI backend"
  format        = "DOCKER"
}

# 3. Google Cloud Secret Manager (Zero Hardcoding)
resource "google_secret_manager_secret" "database_url" {
  depends_on = [google_project_service.enabled_apis]
  secret_id  = "OMNIS_DATABASE_URL"
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret" "jwt_secret" {
  depends_on = [google_project_service.enabled_apis]
  secret_id  = "JWT_SECRET_KEY"
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret" "audit_hmac_key" {
  depends_on = [google_project_service.enabled_apis]
  secret_id  = "AUDIT_HMAC_KEY"
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret" "google_client_id" {
  depends_on = [google_project_service.enabled_apis]
  secret_id  = "GOOGLE_CLIENT_ID"
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret" "gemini_api_key" {
  depends_on = [google_project_service.enabled_apis]
  secret_id  = "GEMINI_API_KEY"
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret" "sync_api_key" {
  depends_on = [google_project_service.enabled_apis]
  secret_id  = "OMNIS_SYNC_API_KEY"
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret" "admin_secret_hashes" {
  depends_on = [google_project_service.enabled_apis]
  secret_id  = "OMNIS_ADMIN_SECRET_HASHES"
  replication {
    auto {}
  }
}

# 4. Google Cloud Run (Cost-Optimized: Scale-to-Zero = 0 USD idle, Firestore Primary)
resource "google_cloud_run_v2_service" "omnis_backend" {
  depends_on = [
    google_project_service.enabled_apis,
    google_artifact_registry_repository.omnis_repo,
    google_secret_manager_secret.database_url,
    google_secret_manager_secret.jwt_secret,
    google_secret_manager_secret.audit_hmac_key,
    google_secret_manager_secret.google_client_id,
    google_secret_manager_secret.gemini_api_key,
    google_secret_manager_secret.sync_api_key,
    google_secret_manager_secret.admin_secret_hashes,
    google_secret_manager_secret_iam_member.cloud_run_secret_access
  ]
  name     = "omnis-backend"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"

  template {
    service_account = google_service_account.cloud_run_sa.email

    scaling {
      min_instance_count = 0  # 100% Scale-to-zero pro nulové fixní náklady
      max_instance_count = 3  # Prevence přetečení rozpočtu
    }

    containers {
      image = "${var.region}-docker.pkg.dev/${var.project_id}/omnis-repo/omnis-backend:latest"

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle = true  # CPU alokováno POUZE během zpracování dotazu
      }

      env {
        name  = "ENV"
        value = "production"
      }
      env {
        name  = "REQUIRE_APP_CHECK"
        value = "true"
      }
      env {
        name  = "PROJECT_ID"
        value = var.project_id
      }
      env {
        name  = "GOOGLE_CLOUD_PROJECT"
        value = var.project_id
      }
      env {
        name  = "GEMINI_MODEL"
        value = "gemini-2.5-flash"
      }
      env {
        name  = "FIREBASE_PROJECT_ID"
        value = var.firebase_project_id != "" ? var.firebase_project_id : var.project_id
      }
      env {
        name  = "FIRESTORE_DATABASE_ID"
        value = var.firestore_database_id
      }
      env {
        name  = "ENABLE_FIRESTORE_CQRS_PROJECTION"
        value = "true"
      }
      env {
        name  = "ENABLE_CLOUD_KMS"
        value = tostring(var.enable_cloud_kms)
      }
      env {
        name  = "CLOUD_KMS_KEY_NAME"
        value = var.cloud_kms_key_name
      }
      env {
        name = "SECRET_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.jwt_secret.secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "AUDIT_HMAC_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.audit_hmac_key.secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "OMNIS_SYNC_API_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.sync_api_key.secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "OMNIS_ADMIN_SECRET_HASHES"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.admin_secret_hashes.secret_id
            version = "latest"
          }
        }
      }
      dynamic "env" {
        for_each = var.enable_cloud_sql ? [1] : []
        content {
          name = "DATABASE_URL"
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.database_url.secret_id
              version = "latest"
            }
          }
        }
      }
      env {
        name = "GEMINI_API_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.gemini_api_key.secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "GOOGLE_CLIENT_ID"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.google_client_id.secret_id
            version = "latest"
          }
        }
      }
    }
  }
}

# 5. Veřejný přístup pro Cloud Run API
resource "google_cloud_run_service_iam_member" "public_access" {
  location = google_cloud_run_v2_service.omnis_backend.location
  service  = google_cloud_run_v2_service.omnis_backend.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

# 6. Rozpočtový strážce (Google Cloud Budget Alert < 5 USD)
resource "google_billing_budget" "budget_limit" {
  count           = var.billing_account_id != "" ? 1 : 0
  billing_account = var.billing_account_id
  display_name    = "O.M.N.I.S. Strikni Limit do 5 USD"

  budget_filter {
    projects = ["projects/${var.project_id}"]
  }

  amount {
    specified_amount {
      currency_code = "USD"
      units         = "5"
    }
  }

  threshold_rules {
    threshold_percent = 0.5
  }
  threshold_rules {
    threshold_percent = 0.8
  }
  threshold_rules {
    threshold_percent = 1.0
  }
}
