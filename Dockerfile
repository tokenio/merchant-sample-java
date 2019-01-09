FROM java:8

COPY . /usr/src/sample

WORKDIR /usr/src/sample

RUN ./gradlew shadowJar

CMD ["/bin/sh", "-c", "java -jar app/build/libs/app-*.jar"]
