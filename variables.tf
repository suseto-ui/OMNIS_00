variable "project_id" {
  type        = string
  description = "Google Cloud Project ID pro Cloud Run, Artifact Registry a Secret Manager"
}

variable "firebase_project_id" {
  type        = string
  description = "Volitelne oddelene Firebase Project ID (pokud je prazdne, pouzije se var.project_id)"
  default     = ""
}

variable "firestore_database_id" {
  type        = string
  description = "Pojmenovany identifikator Cloud Firestore databaze"
  default     = "ai-studio-android-omnis-fba4ba28-3ec2-4eaf-a1a7-338554af8dfe"
}

variable "region" {
  type    = string
  default = "europe-west1"
}

variable "db_name" {
  type    = string
  default = "omnis_db"
}

variable "enable_cloud_sql" {
  type        = bool
  description = "Povolit volitelnou VPC a Cloud SQL pgvector infrastrukturu (výchozí false pro striktní rozpočet < 5 USD/měsíc)"
  default     = false
}

variable "enable_cloud_kms" {
  type        = bool
  description = "Volitelné hardwarové podepisování auditních otisků přes Google Cloud KMS ECDSA P-256"
  default     = false
}

variable "cloud_kms_key_name" {
  type        = string
  description = "Plný název verze klíče Google Cloud KMS (pokud je enable_cloud_kms = true)"
  default     = ""
}

variable "billing_account_id" {
  type        = string
  description = "Google Cloud Billing Account ID pro rozpoctove alarmy (< 5 USD)"
  default     = ""
}