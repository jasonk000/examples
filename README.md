examples
========
for disruptor samples
examples/disruptor $ mvn compile exec:java -Dthreadpool
examples/disruptor $ mvn compile exec:java -Dbio
examples/disruptor $ mvn compile exec:java -Ddisruptor

and then run the client

mvn package ; java -jar target/disruptor-1.0-SNAPSHOT.jar 192.168.0.253

