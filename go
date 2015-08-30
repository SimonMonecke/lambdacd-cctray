#!/bin/bash 
set -e

test() {
  lein test
}

testunit() {
  lein test
}

push() {
  test && git push
}

release() {
  test && lein release "$1" && scripts/github-release.sh
}

function serve() {
  lein run
}

if type $1 &>/dev/null; then
    $1 $2
else
    echo "usage: $0 <goal>

goal:
    test     -- run unit tests
    testall  -- run all tests
    push     -- run all tests and push current state
    serve    -- run the sample pipeline
    release  -- release current version"

    exit 1
fi
