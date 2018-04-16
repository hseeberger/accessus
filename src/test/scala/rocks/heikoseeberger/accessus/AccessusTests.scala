/*
 * Copyright 2018 Heiko Seeberger
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

package rocks.heikoseeberger.accessus

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.{ Directives, Route }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Keep, Sink, Source }
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.DurationInt
import utest._

object AccessusTests extends TestSuite {
  import Accessus._

  private implicit val system: ActorSystem = ActorSystem()

  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val route = {
    import Directives._
    path(Segment) { s =>
      get {
        complete(s"/$s")
      }
    }
  }

  override def tests: Tests =
    Tests {
      'routeOps - {
        'withAccessLog - {
          runAndAssert(route.withAccessLog(Sink.head))
        }

        'withAccessLog2 - {
          runAndAssert(route.withAccessLog(() => now())(Sink.head))
        }
      }

      'handlerOps - {
        'withAccessLog - {
          runAndAssert(Route.handlerFlow(route).withAccessLog(Sink.head))
        }

        'withAccessLog2 - {
          runAndAssert(Route.handlerFlow(route).withAccessLog(() => now())(Sink.head))
        }
      }

      'withAccessLog - {
        runAndAssert(withAccessLog(() => now())(Sink.head, route))
      }
    }

  override def utestAfterAll(): Unit = {
    Await.ready(system.terminate(), 42.seconds)
    super.utestAfterAll()
  }

  private def runAndAssert(handler: Handler[Future[((HttpRequest, Long), HttpResponse)]]) = {
    import system.dispatcher
    val t = now()
    Source
      .single(Get("/test"))
      .viaMat(handler)(Keep.right)
      .to(Sink.ignore)
      .run()
      .map {
        case ((request, t0), response) =>
          assert(request.uri.path.toString == "/test")
          assert(response.status == OK)
          assert(t0 > t)
      }
  }

  private def now() = System.nanoTime()
}
