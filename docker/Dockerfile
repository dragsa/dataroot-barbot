FROM ubuntu

MAINTAINER to.gnatuk@gmail.com
    ## install Java
RUN apt-get update && \
    apt-get install -y iputils-ping software-properties-common python-software-properties && \
    echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
    add-apt-repository -y ppa:webupd8team/java && \
    apt-get update && \
    apt-get install -y oracle-java8-installer && \
    rm -rf /var/lib/apt/lists/* && \
    rm -rf /var/cache/oracle-jdk8-installer && \
    ## compiler and libs
    apt-get update && \
    apt-get install -y git build-essential libreadline-dev zlib1g-dev && \
    mkdir -p /temp/postgres_9.6/sources && \
    apt-get update && \
    ## unicode support
    apt-get install -y locales && rm -rf /var/lib/apt/lists/* && \
    localedef -i en_US -c -f UTF-8 -A /usr/share/locale/locale.alias en_US.UTF-8
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle
ENV LANG en_US.utf8

RUN groupadd -r postgres --gid=999 && useradd -r -g postgres --uid=999 postgres

ADD https://ftp.postgresql.org/pub/source/v9.6.6/postgresql-9.6.6.tar.gz /temp/postgres_9.6/sources
RUN cd /temp/postgres_9.6/sources && gunzip postgresql-9.6.6.tar.gz && tar xf postgresql-9.6.6.tar && \
	cd /temp/postgres_9.6/sources/postgresql-9.6.6 &&\
	./configure && \
	make && \
	make install && \
	mkdir /usr/local/pgsql/data && \
	chown postgres /usr/local/pgsql/data

COPY ./init.sh /init.sh
COPY ./bar-table-inserts.sql /app/bar-table-inserts.sql
COPY ./barbot-assembly-0.1.jar /app/barbot-assembly-0.1.jar
COPY ./wrapper.sh /wrapper.sh
RUN chmod +x /init.sh && chown postgres /init.sh && chmod +x /wrapper.sh && mkdir /tmp/data/

USER postgres

ENV PGDATA /usr/local/pgsql/data
ENV PGHOME /usr/local/pgsql
ENV PATH $PATH:/usr/local/pgsql/bin

RUN $PGHOME/bin/initdb -D $PGDATA
RUN echo "host all  all    0.0.0.0/0  md5" >> /usr/local/pgsql/data/pg_hba.conf
RUN echo "listen_addresses='*'" >> /usr/local/pgsql/data/postgresql.conf

VOLUME /usr/local/pgsql/data/volume
EXPOSE 5432

RUN /init.sh

CMD ./wrapper.sh