#FROM phpmyadmin/phpmyadmin:4.8.5

FROM centos:7.6.1810

RUN yum update -y &&\
  yum install -y epel-release &&\
  yum install -y \
    httpd \
    net-tools \
    php php-common php-mbstring php-gd \
    phpmyadmin \
    which \
  && yum clean all &&\
  rm -rf /var/cache/yum

COPY phpMyAdmin.conf /etc/httpd/conf.d/phpMyAdmin.conf
