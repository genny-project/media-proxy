FROM adoptopenjdk/openjdk11:alpine

RUN apk update && apk add jq && apk add curl && apk add bash

ENV MEDIA_PROXY_SERVER_PORT 80
ADD target/media-proxy-fat.jar /service.jar
RUN mkdir -p /src/conf
ADD src/conf/ /src/conf/
ADD docker-entrypoint.sh /docker-entrypoint.sh

WORKDIR /

EXPOSE $MEDIA_PROXY_SERVER_PORT

HEALTHCHECK --interval=10s --timeout=3s --retries=15 CMD curl -f / http://localhost:80/version || exit 1

ENTRYPOINT [ "/docker-entrypoint.sh" ]

