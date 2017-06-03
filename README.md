# Accessus #

[![Build Status](https://travis-ci.org/hseeberger/accessus.svg?branch=master)](https://travis-ci.org/hseeberger/accessus)
[![Maven Central](https://img.shields.io/maven-central/v/de.heikoseeberger/accessus_2.12.svg)](https://maven-badges.herokuapp.com/maven-central/de.heikoseeberger/accessus_2.12)

Accessus is a micro library (micro all the things!) providing an access log for Akka HTTP based
servers.

Accessus is published to Bintray and Maven Central.

``` scala
// All releases including intermediate ones are published here,
// final ones are also published to Maven Central.
resolvers += Resolver.bintrayRepo("hseeberger", "maven")

libraryDependencies ++= Seq(
  "de.heikoseeberger" %% "accessus" % "0.1.0",
  ...
)
```

Example:

``` scala
import akka.Done
import akka.actor.ActorSystem
import akka.event.{ Logging, LoggingAdapter }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.{ Directives, Route }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{ Failure, Success }

object Demo {
  import Accessus._

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val mat    = ActorMaterializer()
    import system.dispatcher

    Http()
      .bindAndHandle(
        route.withAccessLog(accessLog(Logging(system, "ACCESS_LOG"))),
        "0.0.0.0",
        8000
      )
      .onComplete {
        case Success(ServerBinding(address)) => println(s"Listening on $address")
        case Failure(cause)                  => println(s"Can't bind to 0.0.0.0:8000: $cause")
      }

    StdIn.readLine(f"Hit ENTER to quit!%n")
    system.terminate()
  }

  /** Log HTTP method, path, status and response time in micros to the given log at info level. */
  def accessLog(log: LoggingAdapter): AccessLog[Long, Future[Done]] =
    Sink.foreach {
      case ((req, t0), res) =>
        val m = req.method.value
        val p = req.uri.path.toString
        val s = res.status.intValue()
        val t = (now() - t0) / 1000
        log.info(s"$m $p $s $t")
    }

  /** Simply echo the path for all GET requests. */
  def route: Route = {
    import Directives._
    get {
      extractUnmatchedPath { path =>
        complete(path.toString)
      }
    }
  }

  private def now() = System.nanoTime()
}
```


## Contribution policy ##

Contributions via GitHub pull requests are gladly accepted from their original author. Along with
any pull requests, please state that the contribution is your original work and that you license
the work to the project under the project's open source license. Whether or not you state this
explicitly, by submitting any copyrighted material via pull request, email, or other means you
agree to license the material under the project's open source license and warrant that you have the
legal authority to do so.

## License ##

This code is open source software licensed under the
[Apache-2.0](http://www.apache.org/licenses/LICENSE-2.0) license.
