FROM alpine:3.14
EXPOSE 8000
ARG HOST
ARG PORT
ARG USER
ARG PASSWRORD
ARG DB
WORKDIR /www
RUN apk update
RUN apk add openjdk11
RUN apk add git 
RUN git clone https://github.com/ATer-Oganisyan/otus-hw-delivery-service.git 
RUN cd otus-hw-delivery-service && jar xf mysql.jar && javac DeliveryService.java && apk del git && rm DeliveryService.java
ENTRYPOINT java -classpath /www/otus-hw-delivery-service DeliveryService $HOST $PORT $USER $PASSWRORD $DB v5