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
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.server.{ Directives, RejectionHandler, Route }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.stream.scaladsl.{ Keep, Sink, Source }
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, Future }
import utest._

object AccessusTests extends TestSuite {
  import Accessus._

  private implicit val system: ActorSystem = ActorSystem()

  private implicit val mat: Materializer = ActorMaterializer()

  private implicit val rejectionHandler: RejectionHandler =
    RejectionHandler.newBuilder().handleNotFound(Directives.complete(OK)).result()

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
        'withTimestampedAccessLog - {
          runAndAssert(route.withTimestampedAccessLog(Sink.seq))
        }

        'withAccessLog - {
          runAndAssert(route.withAccessLog(() => now())(Sink.seq))
        }
      }

      'handlerOps - {
        'withTimestampedAccessLog - {
          runAndAssert(Route.handlerFlow(route).withTimestampedAccessLog(Sink.seq))
        }

        'withAccessLog - {
          runAndAssert(Route.handlerFlow(route).withAccessLog(() => now())(Sink.seq))
        }
      }

      'withAccessLog - {
        runAndAssert(withAccessLog(() => now())(Sink.seq, route))
      }
    }

  override def utestAfterAll(): Unit = {
    Await.ready(system.terminate(), 42.seconds)
    super.utestAfterAll()
  }

  private def runAndAssert(handler: Handler[Future[Seq[((HttpRequest, Long), HttpResponse)]]]) = {
    import system.dispatcher
    val t = now()
    Source(List("/test", "/"))
      .map(Get.apply)
      .viaMat(handler)(Keep.right)
      .to(Sink.ignore)
      .run()
      .map {
        case Seq(((req1, t1), res1), ((req2, t2), res2)) =>
          assert(req1.uri.path.toString == "/test")
          assert(res1.status == OK)
          assert(t1 > t)

          assert(req2.uri.path.toString == "/")
          assert(res2.status == OK)
          assert(t2 > t)
      }
  }

  private def now() = System.nanoTime()
}
