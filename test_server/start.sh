#!/bin/bash

cd $(dirname $0)

MC_VERSION='1.20.2'
PAPER_BUILD='223'
PAPER_JAR_URL="https://api.papermc.io/v2/projects/paper/versions/$MC_VERSION/builds/$PAPER_BUILD/downloads/paper-$MC_VERSION-$PAPER_BUILD.jar"

if [ ! -f paper.jar ]; then
    echo "Downloading paper.jar..."
    curl -o paper.jar $PAPER_JAR_URL
fi

java -Xms3G -Xmx3G -jar paper.jar nogui
