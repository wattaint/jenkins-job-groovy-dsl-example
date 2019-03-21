#FROM phpmyadmin/phpmyadmin:4.8.5

FROM centos:7.6.1810

RUN yum update -y &&\
  yum install -y epel-release &&\
  yum install -y \
    httpd \
    mariadb \
    mlocate \
    nc \
    net-tools \
    php php-common php-mbstring php-gd \
    phpmyadmin \
    telnet \
    vim \
    which \
  && yum clean all -y &&\
  rm -rf /var/cache/yum

RUN chgrp -R 0 /run/httpd &&\
    chmod -R g=u /run/httpd \
      && \
    chgrp -R 0 /var/lib/php &&\
    chmod -R g=u /var/lib/php \
      && \
    chgrp -R 0 /etc/phpMyAdmin &&\
    chmod -R g=u /etc/phpMyAdmin \
      && \
    mkdir /logs && touch /logs/error_log && touch /logs/access_log &&\
    chmod 777 /logs/*

CMD [ "httpd", "-DFOREGROUND" ]
COPY etc/httpd/conf.d/phpMyAdmin.conf /etc/httpd/conf.d/phpMyAdmin.conf
COPY etc/httpd/conf/httpd.conf /etc/httpd/conf/httpd.conf
COPY etc/phpMyAdmin/config.inc.php /etc/phpMyAdmin/config.inc.php
EXPOSE 8080