#!/bin/bash
set -e
exec $(javac -cp ./src/ ./src/mbtc/*.java -d ./bin)
exec java -cp ./bin mbtc.Run