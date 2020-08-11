FROM openjdk:11
MAINTAINER Marconi Soldate
COPY ./src /tmp/src
COPY ./index.html /tmp
COPY ./data/blockchain/* /tmp/data/blockchain/
WORKDIR /tmp
RUN javac -cp ./src/ ./src/mbtc/*.java -d ./bin
ENTRYPOINT java -cp ./bin mbtc.Main