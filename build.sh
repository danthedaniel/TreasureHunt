#!/bin/bash
set -euo pipefail

cd $(dirname $0)

mvn package
cp target/TreasureHunt-1.0.0.jar ~/test/plugins/
tmux send-keys -t test 'reload confirm' Enter
