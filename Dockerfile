FROM bellsoft/liberica-openjdk-alpine-musl:21.0.11

LABEL maintainer="opensourcegroup@netcracker.com"
LABEL atp.service="atp3-tdm-be"

ENV HOME_EX=/atp-tdm
ENV TDM_DB_USER=tdmadmin
ENV TDM_DB_PASSWORD=tdmadmin
ENV JDBC_URL=jdbc:h2:file:./database/atptdm;

WORKDIR $HOME_EX

RUN echo "https://dl-cdn.alpinelinux.org/alpine/v3.23/community/" >/etc/apk/repositories && \
    echo "https://dl-cdn.alpinelinux.org/alpine/v3.23/main/" >>/etc/apk/repositories && \
    apk add --update --no-cache --no-check-certificate \
        bash \
        curl \
        font-dejavu \
        fontconfig \
        gcompat \
        gettext \
        git \
        htop \
        jq \
        libpng \
        libcrypto3 \
        libexpat \
        libssl3 \
        musl \
        musl-utils \
        net-tools \
        nss_wrapper \
        pcre2 \
        procps-ng \
        sops \
        sysstat \
        tcpdump \
        wget \
        xz-libs \
        zip \
        zlib && \
      rm -rf /var/cache/apk/*

COPY build-context/qubership-atp-tdm-distribution/target/ /tmp/

RUN adduser -D -H -h /atp -s /bin/bash -u 1007 atp && \
    mkdir -p /etc/env /etc/alternatives /tmp/log/diagnostic /tmp/cert && \
    ln -s ${JAVA_HOME}/bin/java /etc/alternatives/java && \
    echo "${JAVA_HOME}/bin/java \$@" >/usr/bin/java && \
    chmod a+x /usr/bin/java

RUN unzip /tmp/qubership-atp-tdm-distribution-*.zip -d $HOME_EX/ && \
    chown -R atp:root $HOME_EX/ && \
    find $HOME_EX -type f -name '*.sh' -exec chmod a+x {} + && \
    find $HOME_EX -type d -exec chmod 777 {} \;

RUN find atp-tdm -mindepth 1 -maxdepth 1 -exec mv -t . {} + || true

EXPOSE 8080 9000

USER atp

CMD ["./run.sh"]