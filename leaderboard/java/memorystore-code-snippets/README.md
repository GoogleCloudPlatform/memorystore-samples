# Samples

This folder contains samples in the form of stand-alone snippets that demonstrate useful scenarios.

## Prerequisites

Ensure the following setup is in place.

### Java

You must have java installed locally on your machine. Run `java --version` to check if this is available.

### Accessible Memorystore for Valkey instance

Valkey-cli: [Download & Install](https://valkey.io/download/)

## Running the sample code

Each example contains instructions on any prerequisite configuration.

### Compile the app through Maven (optional)

```bash
mvn compile
```

### Run the sample code

```bash
mvn exec:java -Dexec.mainClass=MemorystoreAddScore #Replace the main class as needed
```
