#!/bin/bash

set -ex

nodemon --exec "RUN=1 sh" -e java,sh,xml ./build.sh --ignore lib --ignore input

set +ex
