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

CMD DNS_HOST=`ip r | awk '/default/{print $3}'` && printf "nameserver $DNS_HOST\n" > /etc/resolv.conf && export ZK_CONNECTION_STRING=${ZK_CONNECTION_STRING}; java -jar -XX:+${GC_ALGO-UseG1GC} -Xms${JAVA_PROCESS_MIN_HEAP-1g} -Xmx${JAVA_PROCESS_MAX_HEAP-1g} ${JAVA_OPTS} phonepe-api-callback.jar server /rosey/config.yml