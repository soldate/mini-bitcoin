FROM openjdk:11
MAINTAINER Marconi Soldate
COPY . /usr/src
WORKDIR /usr/src
RUN javac -cp ./src/ ./src/mbtc/*.java -d ./bin
ENTRYPOINT java -cp ./bin mbtc.Main
EXPOSE 10762