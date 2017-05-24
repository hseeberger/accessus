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
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.model.StatusCodes.NoContent
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Keep, Sink, Source }
import org.scalatest.{ AsyncWordSpec, BeforeAndAfterAll, Matchers }
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import org.mockito.Mockito.{ mock, verify }

final class AccessusSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {
  import Accessus._

  private implicit val system = ActorSystem()

  private implicit val mat = ActorMaterializer()

  private val route = Directives.path("test") & Directives.get & Directives.complete(NoContent)

  "withLoggingAccessLog" should {
    "wrap a handler in a logging access log" in {
      val log = mock(classOf[LoggingAdapter])
      def toMessage(request: HttpRequest, response: HttpResponse) = {
        val path   = request.uri.path
        val status = response.status.intValue
        s"$path-$status"
      }
      run(withLoggingAccessLog(toMessage, log, Logging.DebugLevel)(route)).map { x =>
        verify(log).log(Logging.DebugLevel, "/test-204")
        x shouldBe Done
      }
    }
  }

  "withAccessLog" should {
    "wrap a handler with an access log" in {
      run(withAccessLog(Sink.head)(route))
        .map {
          case (request, response) =>
            request.uri.path.toString shouldBe "/test"
            response.status shouldBe NoContent
        }
    }
  }

  override protected def afterAll() = {
    Await.ready(system.terminate(), 42.seconds)
    super.afterAll()
  }

  private def run[A](handler: Handler[A]) =
    Source
      .single(Get("/test"))
      .viaMat(handler)(Keep.right)
      .to(Sink.ignore)
      .run()
}
