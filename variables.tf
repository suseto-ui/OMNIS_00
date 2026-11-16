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
  description = "Pojmenovany identifikator Cloud Firestore databaze (legacy fallback)"
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
  description = "Povolit volitelnou VPC a Cloud SQL PostgreSQL 17 infrastrukturu (výchozí false pro striktní rozpočet < 5 USD/měsíc)"
  default     = false
}

variable "cloud_sql_tier" {
  type        = string
  description = "Machine tier pro Cloud SQL PostgreSQL 17 instanci (pokud je enable_cloud_sql = true)"
  default     = "db-f1-micro"
}

variable "cloud_sql_disk_size_gb" {
  type        = number
  description = "Velikost SSD disku v GB pro Cloud SQL PostgreSQL 17"
  default     = 10
}

variable "cloud_sql_deletion_protection" {
  type        = bool
  description = "Ochrana proti náhodnému smazání Cloud SQL PostgreSQL 17 instance"
  default     = false
}

variable "use_oss_postgres_sync" {
  type        = bool
  description = "Feature flag pro aktivaci open-source ElectricSQL + PostgreSQL 17 synchronizace místo Firestore"
  default     = true
}

variable "electric_url" {
  type        = string
  description = "Veřejná nebo interní URL instance ElectricSQL (Apache-2.0)"
  default     = ""
}

variable "llm_provider_mode" {
  type        = string
  description = "Režim inference pro LlmInferenceGateway (gemma4_local | gemma4_cloud_fallback | gemini_legacy)"
  default     = "gemma4_cloud_fallback"
}

variable "gemma4_api_base_url" {
  type        = string
  description = "OpenAI-kompatibilní URL pro self-hosted Gemma 4 (vLLM / Ollama / llama.cpp)"
  default     = ""
}

variable "gemma4_model_name" {
  type        = string
  description = "Identifikátor open-weights modelu Gemma 4"
  default     = "gemma4:e4b"
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