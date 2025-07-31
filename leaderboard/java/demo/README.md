# Leaderboard Demo Application

This demo shows how to use Valkey as an in-memory cache to accelerate data retrieval in a leaderboard application. By storing top scores in Valkey, the application can quickly retrieve the top entries without having to query the database.

---

## Running the application locally

### 1. Install Dependencies

- **PostgreSQL**: [Download & Install](https://www.postgresql.org/download/)
- **Valkey** (Redis-compatible): [Download & Install](https://valkey.io/download/)
- **Docker** (optional) [Download & Install](https://docs.docker.com/engine/install/)

### 2. Create a Postgres user called `postgres`

```bash
createuser -s postgres
```

### 3. Set Environment Variables

```bash
export DB_URL=jdbc:postgresql://localhost:5432/postgres
export DB_USERNAME=postgres
export DB_PASSWORD=password
```

### 4. Initialize the Database Schema

```bash
psql -U postgres -d postgres -f ./app/init.sql
```

### 5. (Optional) Generate Sample Data

This populates the database with sample items using [Java Faker](https://github.com/DiUS/java-faker).

```bash
cd sample-data
mvn compile exec:java -Dexec.mainClass=app.Main
```

### 6. Start the Application

Run the main Spring Boot application from the `app` directory:

```bash
cd app
mvn clean spring-boot:run
```

Navigate to the web url `http://localhost:8080` to view your application

## Run locally using Docker

Use [docker compose](https://docs.docker.com/compose/install/) to run the application locally:

```bash
cd app
docker compose up --build
```

To run with sample data:

```bash
cd sample-data
docker compose up --build
```

## How to deploy the application to Google Cloud

You can use [Terraform](https://learn.hashicorp.com/tutorials/terraform/install-cli) to deploy the infrastructure to Google Cloud.

### Pre-requisites

You will need an exisiting [GCP project](https://developers.google.com/workspace/guides/create-project). With the following APIs enabled:

 1. [Compute Engine API](https://console.cloud.google.com/apis/library/compute.googleapis.com).
 2. [Network Connectivity API](https://console.cloud.google.com/apis/library/networkconnectivity.googleapis.com)
 3. [Memorystore](https://console.cloud.google.com/apis/library/memorystore.googleapis.com)
 4. [Cloud Run API](https://console.developers.google.com/apis/api/run.googleapis.com)

### Installing

#### 1. Set your GCP Project ID

```bash
cd app
gcloud config set project YOUR_PROJECT_ID
```

#### 2. Build and Submit the App

```bash
gcloud builds submit --config cloudbuild.yaml .
```

#### 3. Configure Terraform

Edit terraform.tfvars with your project and region:

```bash
project_id = "your-project-id"
region = "your-region-id" #eg:us-central1
```

#### 4. Deploy Infrastructure

```bash
terraform init
terraform apply
```

Terraform will list a series of actions to perform. Enter `yes` and enter to start the deployment

#### 6. Database Migration

Now that your application has successfully deployed, add the neccessary database schemas.

1. Open [Cloud SQL Studio](https://console.cloud.google.com/sql/instances/leaderboard-app-postgres-instance/studio).
2. Login with:
   - Username: `admin`
   - Password:`password`.
3. Run the following script:

```sql
CREATE TABLE leaderboard (
    username VARCHAR(255) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (username)
);

-- Create an index to ensure efficient ordering by score (descending)
CREATE INDEX idx_leaderboard_score ON leaderboard (score DESC);

-- Create an index to ensure efficient ordering by score (ascending)
CREATE INDEX idx_leaderboard_score_asc ON leaderboard (score ASC);
```

4. You will be required to allow unauthenticated invocations. Navigate to your [Leaderboasrd App Service](https://console.cloud.google.com/run/detail/us-central1/leaderboard-app-service/security?) to enable this.

You should have a fully working caching solution deployed on Google cloud with Memorystore for Valkey. Select the service URL to view your application.

### Troubleshooting

### Error: Permission Denied on init.sql

If you see a `Permission denied error` when initializing the database (e.g. in Docker logs for postgres), it may be caused by restricted file permissions on your host machine.

Ensure the init.sql file is readable by all users:

```bash
chmod 644 ./app/init.sql
```

### Endpoints

- `GET /api/leaderboard`: By default, this endpoint returns the top X entries in the leaderboard. Optionally, a parameter position can be provided to return the leaderboard starting from that position.
- `POST /api/leaderboard`: This endpoint creates or updates a leaderboard entry with a given username and score.
