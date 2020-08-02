FROM openjdk:11
MAINTAINER Marconi Soldate
COPY . /tmp
WORKDIR /tmp
RUN javac -cp ./src/ ./src/mbtc/*.java -d ./bin
ENTRYPOINT java -cp ./bin mbtc.Main