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

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.{ Directives, Route }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Keep, Sink, Source }
import org.scalatest.{ AsyncWordSpec, BeforeAndAfterAll, Inspectors, Matchers }
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.DurationInt

final class AccessusSpec
    extends AsyncWordSpec
    with Matchers
    with Inspectors
    with BeforeAndAfterAll {
  import Accessus._

  private implicit val system = ActorSystem()

  private implicit val mat = ActorMaterializer()

  private val route = {
    import Directives.{ complete => completeDir, _ }
    path(Segment) { s =>
      get {
        completeDir(s"/$s")
      }
    }
  }

  "RouteOps" should {
    "add a withAccessLog extension method" in {
      run(route.withAccessLog(Sink.head))
    }

    "add a withAccessLog extension method which takes an extension function" in {
      runAndAssert(route.withAccessLog(_ -> now())(Sink.head))
    }
  }

  "HandlerOps" should {
    "add a withAccessLog extension method" in {
      run(Route.handlerFlow(route).withAccessLog(Sink.head))
    }

    "add a withAccessLog extension method which takes an extension function" in {
      runAndAssert(Route.handlerFlow(route).withAccessLog(_ -> now())(Sink.head))
    }
  }

  "withAccessLog" should {
    "wrap a handler in a new one which also streams enriched request-response pairs to a sink" in {
      runAndAssert(withAccessLog(_ -> now())(Sink.head, route))
    }
  }

  override protected def afterAll() = {
    Await.ready(system.terminate(), 42.seconds)
    super.afterAll()
  }

  private def run(handler: Handler[Future[(HttpRequest, HttpResponse)]]) =
    Source
      .single(Get("/test"))
      .viaMat(handler)(Keep.right)
      .to(Sink.ignore)
      .run()
      .map {
        case (request, response) =>
          request.uri.path.toString shouldBe "/test"
          response.status shouldBe OK
      }

  private def runAndAssert(handler: Handler[Future[((HttpRequest, Long), HttpResponse)]]) = {
    val t = now()
    Source
      .single(Get("/test"))
      .viaMat(handler)(Keep.right)
      .to(Sink.ignore)
      .run()
      .map {
        case ((request, t0), response) =>
          request.uri.path.toString shouldBe "/test"
          response.status shouldBe OK
          t0 should be > t
      }
  }

  private def now() = System.nanoTime()
}
