#!/bin/bash

cd $(dirname $0)

mvn clean install
docker build --tag unterstein/neo4j-beer-demo:latest .
