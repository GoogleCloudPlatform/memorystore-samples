# Session Demo Application

This demo shows how to use Valkey as an in-memory sessions to store user tokens for quick access in a session management application. By storing user tokens in Valkey, the application can quickly retrieve and validate tokens without having to query the database.

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

This populates the database with sample accounts using [Java Faker](https://github.com/DiUS/java-faker).

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

1. Open [Cloud SQL Studio](https://console.cloud.google.com/sql/instances/session-app-postgres-instance/studio).
2. Login with:
   - Username: `admin`
   - Password:`password`.
3. Run the following script:

```sql
CREATE TABLE IF NOT EXISTS account (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    username VARCHAR(20) NOT NULL,
    password VARCHAR(255) NOT NULL
);
```

4. You will be required to allow unauthenticated invocations. Navigate to your [session App Service](https://console.cloud.google.com/run/detail/us-central1/session-app-service/security?) to enable this.

You should have a fully working caching solution deployed on Google cloud with Memorystore for Valkey. Select the service URL to view your application.

### Endpoints

- `POST /auth/register` - Registers a new user
- `POST /auth/login` - Logs in a user
- `POST /auth/logout` - Logs out a user
- `POST /auth/verify` - Verifies a user's token
- `GET /api/basket` - Get all items
- `POST /api/basket/add` - Add item with quantity
- `POST /api/basket/remove` - Remove item quantity
- `POST /api/basket/clear` - Clear entire basket
