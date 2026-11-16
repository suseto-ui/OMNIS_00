# VPC a Serverless VPC Access Connector jsou aktivovány POUZE pokud je explicitně zapnuto var.enable_cloud_sql = true.
# Při výchozím nastavení (Cloud Firestore primární, enable_cloud_sql = false) nevznikají žádné fixní měsíční poplatky (~15 USD/měsíc).

resource "google_compute_network" "omnis_network" {
  count                   = var.enable_cloud_sql ? 1 : 0
  name                    = "omnis-vpc"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "omnis_subnet" {
  count         = var.enable_cloud_sql ? 1 : 0
  name          = "omnis-subnet"
  ip_cidr_range = "10.0.0.0/28"
  region        = var.region
  network       = google_compute_network.omnis_network[0].id
}

resource "google_vpc_access_connector" "connector" {
  count         = var.enable_cloud_sql ? 1 : 0
  name          = "omnis-vpc-conn"
  region        = var.region
  network       = "default"
  ip_cidr_range = "10.8.0.0/28"
}

# Rezervace privátních IP pro volitelnou Cloud SQL pgvector instanci
resource "google_compute_global_address" "private_ip_address" {
  count         = var.enable_cloud_sql ? 1 : 0
  name          = "omnis-private-ip-address"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.omnis_network[0].id
}