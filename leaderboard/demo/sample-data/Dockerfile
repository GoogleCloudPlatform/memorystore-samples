# Use an OpenJDK base image
FROM openjdk:17-jdk-slim

# Install Maven for building the project
RUN apt-get update && apt-get install -y maven

# Add a wait script for the database migration.
RUN apt-get install -y wait-for-it

# Set the working directory
WORKDIR /app

# Copy Maven project files
COPY pom.xml ./
COPY src ./src

# Build the project
RUN mvn clean package -DskipTests

# Copy the built JAR file to the container
RUN cp target/app-1.0-SNAPSHOT.jar app.jar

# Expose the application port
EXPOSE 8080

# Wait for Postgres to be ready, then run the application
CMD ["wait-for-it", "postgres:5432", "--", "java", "-jar", "app.jar"]