FROM mysql
MAINTAINER Arsen <ter.hov.ars@gmail.com>
WORKDIR /www
RUN cd /www
COPY ./migration.sql /www
COPY ./migrate.sh /migrate.sh
RUN ["chmod", "+x", "/migrate.sh"]
CMD ["/migrate.sh"]