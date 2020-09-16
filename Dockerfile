FROM openjdk:11
MAINTAINER Marconi Soldate
RUN cd /
RUN apt update
RUN apt install -y git
ADD https://www.unixtimestamp.com /tmp
RUN git clone https://github.com/soldate/mini-bitcoin.git
WORKDIR /mini-bitcoin
RUN javac -cp ./src/ ./src/mbtc/*.java -d ./bin
ENTRYPOINT java -cp ./bin mbtc.Main