#!/bin/bash

# Assume redis is up and running
cd persistence-framework
mvn clean install
# Insert mvn command to insert to local repository
cd ..

cd microservice-framework
mvn clean install
# Insert mvn command to insert to local repository
cd ..

cd microservices
mvn clean install
mvn exec:java &
cd ..

sleep 20 # Wait for microservices to start

cd main-app
mvn clean install
mvn exec:java
cd ..
