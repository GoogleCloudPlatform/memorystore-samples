# Building a Caching Service on Google Cloud using Valkey, Spring Boot, and PostgreSQL

Modern applications need to deliver fast, responsive user experiences at scale.

In this guide, there are architectural concepts and deployment steps for creating a high-performance caching service on Google Cloud. Using a combination of Java, Spring Boot, PostgreSQL, and Valkey, you can reduce latency while also reducing the load on your database.

## Why Caching Matters

- **Speed & Latency:** Storing frequently requested data in memory avoids repeated round-trip queries to databases, while reducing response times.
- **Scalability:** By reducing the workload on your database, applications can serve data directly from memory, increasing the capacity for requests.

## Before Getting started

Some aspects of this guide will require a running instance of Memorystore for Valkey.

### Local development

If running on your local machine, you can use the [Valkey CLI](https://valkey.io/topics/cli/).

### Production

If using Memorystore on the Google Cloud Platform, the following apis will need to be enabled:

- [Google Cloud Memorystore for Redis API](https://console.cloud.google.com/marketplace/product/google/redis.googleapis.com).
- [Valkey for Memorystore](https://console.cloud.google.com/memorystore/valkey/instances)

#### Creating a Memorystore for Valkey instance (optional)

Follow this [guide](https://cloud.google.com/memorystore/docs/valkey/create-instances) to create an instance.

You will be also be required to setup a `Service Connection Policy` when creating a new instance. Please follow the [Networking guide](https://cloud.google.com/memorystore/docs/valkey/networking) for further information.

## What You’ll Build

You’ll set up a caching service that:

1. **Works with a PostgreSQL database** to store long-lived, persistent records.
2. **Incorporates Valkey** as a high-speed, in-memory cache, fronting the PostgreSQL database.
3. **Uses Spring Boot** to expose REST endpoints, providing a simple interface for reading, writing, and invalidating cached data.
4. **A solution that can be deployed to Google Cloud Platform (GCP)** for production, leveraging services like Cloud Run, Cloud SQL, and Memorystore.

By following this guide, you’ll have a reference architecture ready to adapt, test, and deploy to meet the performance needs of your application.

## Architecture Overview

- **Spring Boot Application:** Serves as the middle tier for responding to API calls. When a request is received, the API checks Valkey for cached results; if no entries are found, then the API will retrieve data from the PostgreSQL database and update the cache.
- **Valkey (In-Memory Cache):** A Redis-like memory store that keeps hot data ready to be served instantly.
- **PostgreSQL Database:** Your source of truth for all data. The cache reduces how often the app queries this database.
- **Google Cloud Infrastructure:** Deployed using Terraform, you can host the application on Cloud Run, store data in Cloud SQL for PostgreSQL, and leverage Memorystore for Valkey.

## Scaling and Optimization

As traffic increases, the architecture can scale horizontally:

- **Cloud Run** can automatically scale instances based on load.
- **Memorystore (Valkey)** can be sized or upgraded to handle more cached data or higher throughput.
- **Cloud SQL** can scale vertically or horizontally (with read replicas) as needed.

You can fine-tune cache expiration strategies (TTL values) and eviction policies, depending on your data access patterns.

## Conclusion

By combining an in-memory store (Valkey) with a reliable database (PostgreSQL), all orchestrated by a Spring Boot application, you’ve built a caching solution that delivers high performance, reduces database load, and ensures an excellent user experience. Running it in Google Cloud extends these benefits further, providing managed services and easy scaling.

For more information, check out the [repository](https://github.com/GoogleCloudPlatform/java-docs-samples/tree/main/memorystore/valkey/caching) for the full project details and follow the instructions to get started.
