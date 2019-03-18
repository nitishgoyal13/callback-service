FROM docker.phonepe.com:5000/pp-ops-xenial:0.6

EXPOSE 8080
EXPOSE 8081
EXPOSE 9010
EXPOSE 5005

VOLUME /var/log/phonepe-api-callback

#Add Maxmind Database
#ADD maxmind/GeoIP2-City.mmdb GeoIP2-City.mmdb

ENV CONFIG_PATH callback.yml
ENV JAR_FILE phonepe-api-callback.jar

ADD target/callback-service*.jar ${JAR_FILE}
ADD certs certs

CMD sh -exc "curl -X GET --header 'Accept: application/x-yaml' http://${CONFIG_SERVICE_HOST_PORT}/v1/phonepe/callback/${CONFIG_ENV} > ${CONFIG_PATH} \
    && java -jar -Duser.timezone=Asia/Kolkata ${JAVA_OPTS} -Xms${JAVA_PROCESS_MIN_HEAP-512m} -Xmx${JAVA_PROCESS_MAX_HEAP-512m} ${JAR_FILE} server /rosey/config.yml"
