FROM alpine

WORKDIR /code

ENV APK_PACKAGES bash openjdk8 apache-ant curl
ENV JAVA_HOME=/usr/lib/jvm/java-1.8-openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV CLASSPATH="."

EXPOSE 8000

RUN apk --update add ${APK_PACKAGES}

COPY . .

RUN ant dist

CMD ["ant", "run"]
