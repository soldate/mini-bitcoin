#!/bin/bash

# https://stackoverflow.com/questions/52394419/creating-a-nohup-process-in-java
# obviously tweak this as necessary to get the behavior you want,
# such as redirecting output or using disown instead of nohup
java -cp ./bin mbtc.Main
