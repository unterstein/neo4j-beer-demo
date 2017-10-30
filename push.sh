#!/bin/bash

cd $(dirname $0)

docker push unterstein/neo4j-beer-demo:latest
