package com.dadrox.sbt.test.reports

import java.io.File
import java.net.InetAddress
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer
import scala.runtime.RichException
import scala.xml.{ Text, Unparsed }
import sbt._
import scala.xml.NodeSeq
import sbt.testing.Event
import sbt.testing.Status

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
        val stacktrace = {
            val t = event.throwable()
            if(t.isDefined()) {
                val e = t.get()
                Some(cdata("stacktrace:\n" + e.getMessage() + "\n" + e.getStackTraceString))
            }
            else None
        }
        val escapedExceptionMessage = {
            val t = event.throwable()
            if(t.isDefined()) {
                val e = t.get()
                escape(e.getMessage())
            }
            else ""
        }
        val description = Option(event.fullyQualifiedName())
        val cdataDescription = cdata(description.getOrElse(""))

        <testcase package={ name } name={ event.fullyQualifiedName().replaceAll(name + ".", "") }>
            {
                (event.status(), stacktrace) match {
                    case (Status.Success, _)        => { cdataDescription }
                    case (Status.Skipped, _)        => <skipped>{ cdataDescription }</skipped>
                    case (Status.Ignored, _)        => <skipped>{ cdataDescription }</skipped>
                    case (Status.Error, Some(stacktrace))=> <error type="error" message={ escapedExceptionMessage }>{ stacktrace }</error>
                    case (Status.Error, _)          => <error type="error" message={ escapedExceptionMessage }/>
                    case (Status.Failure, Some(stacktrace))=> <failure type="failure" message={ escapedExceptionMessage }>{ stacktrace }</failure>
                    case (Status.Failure, _)        => <failure type="failure" message={ escapedExceptionMessage }/>
                    case (Status.Canceled, _)        => <failure type="canceled" message={ escapedExceptionMessage }/>
                    // TODO I'm not sure what pending is...
                    case (Status.Pending, _)        => { cdataDescription }
                }
            }
        </testcase>
    }

    def xml() = {
        val id = Some(Text(Id.current.getAndIncrement().toString))
        val duration = Some(Text(((System.currentTimeMillis() - start) / 1000.0).toString))
        val tests = Some(Text(events.size.toString))
        val successes = events.filter(_.status() == Status.Success)

        def count2Xml(count: Int) = count match {
            case 0    => None
            case some => Some(Text(some.toString))
        }

        val skipped = count2Xml(events.filter(_.status() == Status.Skipped).size)
        val errors = count2Xml(events.filter(_.status() == Status.Error).size)
        val failures = count2Xml(events.filter(_.status() == Status.Failure).size)

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

