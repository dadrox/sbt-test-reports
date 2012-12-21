package com.dadrox.sbt.test.reports

import java.io.File
import java.net.InetAddress
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer
import scala.runtime.RichException
import scala.xml.{ Text, Unparsed }
import org.scalatools.testing.{ Event, Result }
import sbt._
import scala.xml.NodeSeq

class Xml(
    target: String)
        extends TestsListener {
    val hostname = InetAddress.getLocalHost.getHostName
    val targetDir = target + "/test-results/"

    var current = new ThreadLocal[Option[Suite]](None)

    override def doInit() { IO.createDirectory(file(targetDir).getAbsoluteFile()) }

    override def startGroup(name: String) { current.set(Some(new Suite(name, hostname))) }

    override def testEvent(event: TestEvent) { current().map(_.addEvents(event.detail)) }

    override def endGroup(name: String, t: Throwable) {
        // TODO suite failed?
        scala.Console.err.println("Unexpected Throwable during tests:")
        t.printStackTrace(scala.Console.err)
    }

    override def endGroup(name: String, result: TestResult.Value) {
        def filename(testName: String) = targetDir + "TEST-" + testName + ".xml"

        current().map(_.xml) match {
            case Some(xml) =>
                val formattedXml = {
                    val sb = new StringBuilder
                    new scala.xml.PrettyPrinter(100, 4).format(scala.xml.Utility.trim(xml), sb)
                    sb.toString
                }
                IO.write(file(filename(name)).getAbsoluteFile(), formattedXml, IO.utf8, false)
            case None =>
        }
    }

    override def doComplete(finalResult: TestResult.Value) {}
}

object Id {
    val current = new AtomicInteger(1)
}

case class Suite(name: String, hostname: String) {
    val start = System.currentTimeMillis()
    val events = new ListBuffer[Event]

    def addEvents(_events: Seq[Event]) {
        events ++= _events
    }

    private def cdata(s: String) = Option(s) match {
        case Some("") | None => Text("")
        case Some(content)   => Unparsed("<![CDATA[%s]]>".format(content))
    }

    private def escape(s: String) = Option(s) match {
        case Some("") | None => ""
        case Some(content)   => scala.xml.Utility.escape(content)
    }

    def testcase(event: Event) = {
        val stacktrace = Option(event.error()) map { e =>
            cdata("stacktrace:\n" + e.getMessage() + "\n" + new RichException(e).getStackTraceString)
        }
        val escapedExceptionMessage = {
            val maybe = Option(event.error()) map (e => escape(e.getMessage()))
            maybe.getOrElse("")
        }
        val description = Option(event.description())
        val cdataDescription = cdata(description.getOrElse(""))

        <testcase package={ name } name={ event.testName().replaceAll(name + ".", "") }>
            {
                (event.result(), stacktrace) match {
                    case (Result.Success, _)        => { cdataDescription }
                    case (Result.Skipped, _)        => <skipped>{ cdataDescription }</skipped>
                    case (Result.Error, Some(stacktrace))=> <error type="error" message={ escapedExceptionMessage }>{ stacktrace }</error>
                    case (Result.Error, _)          => <error type="error" message={ escapedExceptionMessage }/>
                    case (Result.Failure, Some(stacktrace))=> <failure type="failure" message={ escapedExceptionMessage }>{ stacktrace }</failure>
                    case (Result.Failure, _)        => <failure type="failure" message={ escapedExceptionMessage }/>
                }
            }
        </testcase>
    }

    def xml() = {
        val id = Some(Text(Id.current.getAndIncrement().toString))
        val duration = Some(Text(((System.currentTimeMillis() - start) / 1000.0).toString))
        val tests = Some(Text(events.size.toString))
        val successes = events.filter(_.result() == Result.Success)

        def count2Xml(count: Int) = count match {
            case 0    => None
            case some => Some(Text(some.toString))
        }

        val skipped = count2Xml(events.filter(_.result() == Result.Skipped).size)
        val errors = count2Xml(events.filter(_.result() == Result.Error).size)
        val failures = count2Xml(events.filter(_.result() == Result.Failure).size)

        val systemProperties = new scala.sys.SystemProperties().iterator.flatMap {
            case (k, v) if (k.startsWith("java")) => None
            case (k, v) if (k.startsWith("sun"))  => None
            case (k, v)                           => Some(<property name={ k } value={ v }/>)
        }

        <testsuite id={ id } hostname={ hostname } name={ name } time={ duration } timestamp={ new Date().toString } tests={ tests } errors={ errors } failures={ failures } skipped={ skipped }>
            { events.map(testcase) }
            <properties>
                { systemProperties.toList }
            </properties>
        </testsuite>
    }
}

