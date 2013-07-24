#!/bin/sh
L=`readlink -f $0`
L=`dirname $L`/lib
cp=`echo $L/*.jar|sed 's/ /:/g'`
exec scala -classpath $cp $0 $@
exit
!#

//
// full credit to these examples:
// http://redstack.wordpress.com/2011/01/03/simple-jms-client-in-scala/
// https://gist.github.com/prassee/5283799
//
 
import net.timewalker.ffmq3.FFMQConstants
import java.util.{Hashtable => JHashtable}
import javax.naming._
import javax.jms._

object SimpleJMSClient {
  val QCF_NAME = FFMQConstants.JNDI_CONNECTION_FACTORY_NAME
  val QUEUE_NAME = "jmstestq"
  val URL = "tcp://localhost:10002"
  val USER = "x"
  val PASSWORD =  "x"

  // create InitialContext
  val properties = new JHashtable[String, String]
  properties.put(Context.INITIAL_CONTEXT_FACTORY, FFMQConstants.JNDI_CONTEXT_FACTORY)
  properties.put(Context.PROVIDER_URL, URL)
  properties.put(Context.SECURITY_PRINCIPAL, USER)
  properties.put(Context.SECURITY_CREDENTIALS, PASSWORD)

  val ctx = new InitialContext(properties)
  println("Got InitialContext " + ctx.toString())

  // create QueueConnectionFactory
  val qcf = (ctx.lookup(QCF_NAME)).asInstanceOf[ConnectionFactory]
  println("Got ConnectionFactory " + qcf.toString())

  // create QueueConnection
  val conn = qcf.createConnection()
  conn.start()
  val session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE)
  println("Got Connection " + conn.toString())

  val replyQ = session.createTemporaryQueue()

  val destination = session.createQueue(QUEUE_NAME)

  val producer = session.createProducer(destination)
  producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT)

  val consumer = session.createConsumer(replyQ)

  def sendMessage(messageText : String) {
    try {

      val message = session.createTextMessage(messageText)
      message.setJMSReplyTo(replyQ)
      producer.send(message)
      
      val response = consumer.receive()
      response match {
        case tm : TextMessage => println("response: " + tm.getText)
        case null => /* do nothing */
        case _ => println("received something else ??")
      }
      
    } catch {
      case ne : NamingException =>
        ne.printStackTrace(System.err)
        System.exit(0)
      case jmse : JMSException =>
        jmse.printStackTrace(System.err)
        System.exit(0)
      case e : Exception =>
        println("Got other/unexpected exception")
        e.printStackTrace(System.err)
        System.exit(0)
    }
  }
  def main(args: Array[String]) = {
    for(i <- 1 to 100) {
      sendMessage("hello from Scala sendMessage() " + i)
      
    } 
    session.close
    conn.close
  }

}

