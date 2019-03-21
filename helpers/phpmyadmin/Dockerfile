#FROM phpmyadmin/phpmyadmin:4.8.5

FROM centos:7.6.1810

RUN yum update -y &&\
  yum install -y epel-release &&\
  yum install -y \
    httpd \
    mlocate \
    net-tools \
    php php-common php-mbstring php-gd \
    phpmyadmin \
    vim \
    which \
  && yum clean all -y &&\
  rm -rf /var/cache/yum

RUN chgrp -R 0 /run/httpd && \
    chmod -R g=u /run/httpd

CMD [ "httpd", "-DFOREGROUND" ]
COPY phpMyAdmin.conf /etc/httpd/conf.d/phpMyAdmin.conf
COPY httpd.conf /etc/httpd/conf/httpd.conf
