#!/bin/bash

set -ex

docker build --rm --tag cnv .

if [ "$RUN" != "" ]; then
  docker run -v ~/.aws:/root/.aws --rm -p 8000:80 cnv ant run-lb
elif [ "$RUN_WEB" != "" ]; then 
  docker run -v ~/.aws:/root/.aws --rm -p 8000:80 cnv ant run-webserver
elif [ "$AS" != "" ]; then 
  docker run -v ~/.aws:/root/.aws --rm -p 8000:80 cnv ant run-autoscaler
fi;

set +ex

