#!/bin/bash

set -ex

if [ "$AS" != "" ]; then
  docker build --rm --tag cnv -f Dockerfile.$AS .
else
  docker build --rm --tag cnv .
fi;

if [ "$RUN" != "" ]; then
  docker run -v ~/.aws:/root/.aws --rm -p 8000:80 cnv
fi;

set +ex
