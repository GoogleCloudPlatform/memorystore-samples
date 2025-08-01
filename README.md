# Memorystore Samples
This repository contains a collection of sample applications, documentation and code snippets to help clients make the best use of Google Cloud Memorystore.

This project is intended for demonstration purposes only. It is not
intended for use in a production environment.

## How to use
Each sample application is in it's own subdirectory and contains it's own documentation. 

### Directory Structure
Inside each application's main directory, you'll find subdirectories organized by programming language (e.g., java/, python/). Each language-specific directory contains:
#### GUIDE.md
This provides a high-level overview of the sample application and what it demonstrates.
#### memorystore-code-snippets
This directory contains isolated code snippets. These are small, focused code examples designed to illustrate specific functionality in a minimal and digestible way. They are intended to be:
  - Easy to understand visually
  - Quick to run in a terminal
  - Ideal for exploring core functions in isolation

Here’s what each snippet set demonstrates:
  - **Caching**: Creating, reading, deleting, and automatically expiring cache items.
  - **Session**: Creating, updating, deleting, and retrieving session records with automatic expiry.
  - **Leaderboard**: Managing datasets using sorted sets and unique IDs — including operations for validation, deduplication, updates, deletions, and retrievals.

These snippets help developers quickly grasp how the underlying functionality works without the overhead of a full application.

#### sample-demo-app
This directory contains the full demo application. These demos are fully-fledged, deployable applications built to simulate real-world use cases using the same underlying logic shown in the snippets. They include:
  - Sample data
  - A front-end interface
  - Backend server logic with the required functions

These are designed to be used for visual or customer-facing demonstrations, showcasing how the functionality fits into a larger application such as a working leaderboard page or session-based login.

## About Google Cloud Memorystore
[Google Cloud Memorystore](https://cloud.google.com/memorystore) is a fully managed in-memory Valkey, Redis[^redis] and Memcached service that offers sub millisecond data access, scalability, and high availability for a wide range of applications. 

## Google Cloud Terms of Service
Please refer to our terms of service [here](https://cloud.google.com/terms/service-terms)

[^redis]: Redis is a trademark of Redis Ltd. All rights therein are reserved to Redis Ltd. Any use by Google is for referential purposes only and does not indicate any sponsorship, endorsement or affiliation between Redis and Google. Memorystore is based on and is compatible with open-source Redis versions 7.2 and earlier and supports a subset of the total Redis command library.
