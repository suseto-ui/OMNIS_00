# Service Account pro GitHub Actions CI/CD
resource "google_service_account" "github_actions" {
  account_id   = "github-actions-deployer"
  display_name = "O.M.N.I.S. GitHub Actions Service Account"
}

# Role potřebné pro build a push do Artifact Registry
resource "google_project_iam_member" "ar_writer" {
  project = var.project_id
  role    = "roles/artifactregistry.writer"
  member  = "serviceAccount:${google_service_account.github_actions.email}"
}

# Role pro nasazení do Cloud Run (Least Privilege: bez roles/run.admin)
resource "google_project_iam_member" "run_developer" {
  project = var.project_id
  role    = "roles/run.developer"
  member  = "serviceAccount:${google_service_account.github_actions.email}"
}

# Povolení pro Service Account užívat runtime identitu Cloud Runu
resource "google_project_iam_member" "sa_user" {
  project = var.project_id
  role    = "roles/iam.serviceAccountUser"
  member  = "serviceAccount:${google_service_account.github_actions.email}"
}

# Per-secret přístup k tajným klíčům v Secret Manageru pro CI/CD
resource "google_secret_manager_secret_iam_member" "secret_access" {
  for_each = toset([
    "OMNIS_DATABASE_URL",
    "JWT_SECRET_KEY",
    "AUDIT_HMAC_KEY",
    "GOOGLE_CLIENT_ID",
    "GEMINI_API_KEY",
    "OMNIS_SYNC_API_KEY",
    "OMNIS_ADMIN_SECRET_HASHES",
  ])
  project   = var.project_id
  secret_id = each.value
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.github_actions.email}"
}

# Role pro volitelnou správu Cloud SQL (pouze pokud je enable_cloud_sql = true)
resource "google_project_iam_member" "sql_client" {
  count   = var.enable_cloud_sql ? 1 : 0
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.github_actions.email}"
}

# Runtime Service Account pro Cloud Run instanci
resource "google_service_account" "cloud_run_sa" {
  account_id   = "omnis-cloud-run"
  display_name = "O.M.N.I.S. Cloud Run Runtime Service Account"
}

# Runtime access pro volitelnou Cloud SQL instanci (pouze pokud enable_cloud_sql = true)
resource "google_project_iam_member" "cloud_run_sql_client" {
  count   = var.enable_cloud_sql ? 1 : 0
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.cloud_run_sa.email}"
}

# Per-secret Least-Privilege přístup k tajným klíčům pro Cloud Run runtime (nahrazuje project-wide oprávnění)
resource "google_secret_manager_secret_iam_member" "cloud_run_secret_access" {
  for_each = toset([
    "OMNIS_DATABASE_URL",
    "JWT_SECRET_KEY",
    "AUDIT_HMAC_KEY",
    "GOOGLE_CLIENT_ID",
    "GEMINI_API_KEY",
    "OMNIS_SYNC_API_KEY",
    "OMNIS_ADMIN_SECRET_HASHES",
  ])
  project   = var.project_id
  secret_id = each.value
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.cloud_run_sa.email}"
}

# Přístup k pojmenované databázi Firebase Firestore pro primární auditní a provozní záznamy
resource "google_project_iam_member" "cloud_run_datastore_user" {
  project = var.project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${google_service_account.cloud_run_sa.email}"
}

# Volitelný Cloud KMS signer přístup (pouze pokud je aktivní enable_cloud_kms = true)
resource "google_project_iam_member" "cloud_run_kms_signer" {
  count   = var.enable_cloud_kms ? 1 : 0
  project = var.project_id
  role    = "roles/cloudkms.signerVerifier"
  member  = "serviceAccount:${google_service_account.cloud_run_sa.email}"
}

# Zápis provozních logů do Cloud Logging
resource "google_project_iam_member" "cloud_run_log_writer" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.cloud_run_sa.email}"
}