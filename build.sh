#!/bin/bash

set -ex

docker build --rm --tag cnv .

if [ "$RUN" != "" ]; then
  docker run --rm -p 8000:8000 -it cnv
fi;

set +ex
