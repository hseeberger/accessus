/*
 * Copyright 2017 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.heikoseeberger.accessus

import akka.Done
import akka.actor.ActorSystem
import akka.event.{ Logging, LoggingAdapter }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.HttpRequest
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
        route.withAccessLog(_ -> now())(accessLog(Logging(system, "ACCESS_LOG"))),
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
  def accessLog(log: LoggingAdapter): AccessLog[(HttpRequest, Long), Future[Done]] =
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
