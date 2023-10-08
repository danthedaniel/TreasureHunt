#!/bin/bash
set -euo pipefail

cd $(dirname $0)

mvn package
cp target/TreasureHunt-1.0.0.jar ~/minecraft/plugins/
tmux send-keys -t minecraft 'reload confirm' Enter
