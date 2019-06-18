FROM ubuntu:14.04
MAINTAINER Nitish Goyal <nitishgoyal13 [at] gmail.com>


EXPOSE 8080
EXPOSE 8081
EXPOSE 9010
EXPOSE 5005


RUN \
  apt-get clean && apt-get update && apt-get install -y --no-install-recommends software-properties-common \
  && add-apt-repository ppa:webupd8team/java \
  && gpg --keyserver hkp://keys.gnupg.net --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3 \
  && apt-get update \
  && echo debconf shared/accepted-oracle-license-v1-1 select true |  debconf-set-selections \
  && echo debconf shared/accepted-oracle-license-v1-1 seen true |  debconf-set-selections \
  && apt-get install -y --no-install-recommends oracle-java8-installer \
  && apt-get install -y --no-install-recommends curl

VOLUME /var/log/api-callback

#Add Maxmind Database
#ADD maxmind/GeoIP2-City.mmdb GeoIP2-City.mmdb

ENV CONFIG_PATH callback.yml
ENV JAR_FILE api-callback.jar

ADD callback-core/target/callback*.jar ${JAR_FILE}

CMD sh -c "sleep 15 ; java -jar server.jar initialize docker.yml || true ;  java -Dfile.encoding=utf-8 -jar server.jar server docker.yml"