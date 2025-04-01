/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

variable "project_id" {
  description = "The GCP project ID to deploy to"
  type = string
}

variable "region" {
  description = "The GCP region to deploy to"
  type = string
}

provider "google" {
  project = var.project_id
  region  = var.region
}

data "google_project" "project" {
  project_id = var.project_id
}

resource "google_compute_network" "app_network" {
  name = "session-app-network"
}

resource "google_compute_firewall" "allow_http" {
  name    = "session-app-allow-http-8080"
  network = google_compute_network.app_network.name

  allow {
    protocol = "tcp"
    ports    = ["8080"]
  }

  source_ranges = ["0.0.0.0/0"]

  depends_on = [google_compute_network.app_network]
}

resource "google_cloud_run_v2_service" "app" {
  name     = "session-app-service"
  location = "us-central1"
  deletion_protection = false

  template {
    containers {
      image = "gcr.io/${var.project_id}/session-app:latest"

      env {
        name  = "VALKEY_HOST"
        value = module.valkey.discovery_endpoints[0]["address"]
      }

      env {
        name  = "VALKEY_PORT"
        value = module.valkey.discovery_endpoints[0]["port"]
      }

      env {
        name  = "DB_URL"
        value = "jdbc:postgresql://${google_sql_database_instance.postgres.public_ip_address}/${google_sql_database.postgres_db.name}"
      }

      env {
        name  = "DB_USERNAME"
        value = google_sql_user.postgres_user.name
      }

      env {
        name  = "DB_PASSWORD"
        value = google_sql_user.postgres_user.password
      }

      ports {
        container_port = 8080
      }
    }

    vpc_access {
      network_interfaces {
        network = google_compute_network.app_network.name
        subnetwork = google_compute_network.app_network.name
        tags = []
      }
    }
  }

  depends_on = [
    google_compute_network.app_network,
    module.valkey,
    google_sql_database_instance.postgres
  ]
}

module "valkey" {
  source         = "terraform-google-modules/memorystore/google//modules/valkey"
  version        = "12.0"

  instance_id    = "session-app-valkey-instance"
  project_id     = data.google_project.project.project_id
  location       = "us-central1"
  node_type      = "SHARED_CORE_NANO"
  shard_count    = 1
  deletion_protection_enabled = false
  engine_version = "VALKEY_7_2"

  network = google_compute_network.app_network.name

  service_connection_policies = {
    session_valkey_scp = {
      subnet_names = [google_compute_network.app_network.name]
    }
  }

  depends_on = [google_compute_network.app_network]
}

resource "google_sql_database_instance" "postgres" {
  name             = "session-app-postgres-instance"
  database_version = "POSTGRES_16"
  region           = "us-central1"
  deletion_protection = false

  settings {
    edition = "ENTERPRISE"
    tier    = "db-custom-1-3840"

    ip_configuration {
      ipv4_enabled = true

      authorized_networks {
        name  = "session-app-access"
        value = "0.0.0.0/0"
      }
    }
  }

  depends_on = [google_compute_network.app_network]
}

resource "google_sql_user" "postgres_user" {
  name     = "admin"
  instance = google_sql_database_instance.postgres.name
  password = "password"

  depends_on = [google_sql_database_instance.postgres]
}

resource "google_sql_database" "postgres_db" {
  name     = "session-app-db"
  instance = google_sql_database_instance.postgres.name

  depends_on = [google_sql_database_instance.postgres]
}