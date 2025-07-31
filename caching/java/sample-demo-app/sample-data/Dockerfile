# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Use an OpenJDK base image
FROM openjdk:17-jdk-slim

# Install Maven for building the project
RUN apt-get update && apt-get install -y maven

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

# Run the application
CMD ["java", "-jar", "app.jar"]