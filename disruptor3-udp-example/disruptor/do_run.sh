#!/usr/local/bin/bash
mvn clean compile package 
java -jar target/disruptor-1.0-SNAPSHOT.jar 192.168.0.253 1
java -jar target/disruptor-1.0-SNAPSHOT.jar 192.168.0.253 2
java -jar target/disruptor-1.0-SNAPSHOT.jar 192.168.0.253 3
java -jar target/disruptor-1.0-SNAPSHOT.jar 192.168.0.253 4
java -jar target/disruptor-1.0-SNAPSHOT.jar 192.168.0.253 5
java -jar target/disruptor-1.0-SNAPSHOT.jar 192.168.0.253 6
java -jar target/disruptor-1.0-SNAPSHOT.jar 192.168.0.253 7
java -jar target/disruptor-1.0-SNAPSHOT.jar 192.168.0.253 8
