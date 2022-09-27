FROM adoptopenjdk/openjdk11:alpine
RUN echo http://mirror.yandex.ru/mirrors/alpine/v3.9/main > /etc/apk/repositories; \
    echo http://mirror.yandex.ru/mirrors/alpine/v3.9/community >> /etc/apk/repositories

RUN apk update && apk add jq && apk add curl && apk add bash
RUN mkdir /opt
ENV MEDIA_PROXY_SERVER_PORT 80
ADD target/media-proxy-fat.jar /opt/service.jar
ADD src/conf/ /opt/
ADD docker-entrypoint.sh /opt/docker-entrypoint.sh

WORKDIR /opt

EXPOSE $MEDIA_PROXY_SERVER_PORT

HEALTHCHECK --interval=10s --timeout=3s --retries=15 CMD curl -f / http://localhost:80/version || exit 1

ENTRYPOINT [ "/opt/docker-entrypoint.sh" ]
