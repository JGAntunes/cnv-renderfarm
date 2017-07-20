#!/bin/bash

set -ex

docker build --rm --tag cnv .

if [ "$RUN" != "" ]; then
  docker run -v ~/.aws:/root/.aws --rm -p 8000:80 -it cnv
fi;

set +ex
