FROM openjdk:11
MAINTAINER Marconi Soldate
RUN apt update && apt install -y git \
&& rm -rf /var/lib/apt/lists/*
ADD https://www.unixtimestamp.com /tmp
COPY .classpath .project index.html start-mbtc.sh /mini-bitcoin/
COPY ./src /mini-bitcoin/src
WORKDIR /mini-bitcoin
ENTRYPOINT ["/mini-bitcoin/start-mbtc.sh"]