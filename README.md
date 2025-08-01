# Memorystore Samples
This repository contains a collection of sample applications, documentation and code snippets to help clients make the best use of Google Cloud Memorystore.

This project is intended for demonstration purposes only. It is not
intended for use in a production environment.

## How to use
Each sample application is in it's own subdirectory and contains it's own documentation. 

### Directory Structure
Inside each application's main directory, you'll find subdirectories organized by programming language (e.g., java/, python/). Each language-specific directory contains:
- A GUIDE.md file: This provides a high-level overview of the sample application and what it demonstrates.
- **sample-demo-app** directory: This contains the full demo application. Its README.md file has detailed instructions on how to build and run the app, showing you how Memorystore works in a real-world context.
- **memorystore-code-snippets** directory: This folder contains isolated code snippets. These examples show you exactly how to connect to a Memorystore instance and perform basic operations, making it easy to integrate into your own projects.

## About Google Cloud Memorystore
[Google Cloud Memorystore](https://cloud.google.com/memorystore) is a fully managed in-memory Valkey, Redis[^redis] and Memcached service that offers sub millisecond data access, scalability, and high availability for a wide range of applications. 

## Google Cloud Terms of Service
Please refer to our terms of service [here](https://cloud.google.com/terms/service-terms)

[^redis]: Redis is a trademark of Redis Ltd. All rights therein are reserved to Redis Ltd. Any use by Google is for referential purposes only and does not indicate any sponsorship, endorsement or affiliation between Redis and Google. Memorystore is based on and is compatible with open-source Redis versions 7.2 and earlier and supports a subset of the total Redis command library.
