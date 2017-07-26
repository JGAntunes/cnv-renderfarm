#!/bin/bash

set -ex

nodemon --exec "sh" -e java,sh,xml ./build.sh --ignore lib --ignore input

set +ex
