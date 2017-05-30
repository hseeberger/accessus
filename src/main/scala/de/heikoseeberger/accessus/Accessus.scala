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
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.server.Route
import akka.stream.{ ActorMaterializer, FlowShape }
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Sink, Zip }

/**
  * Provides ways to wrap a route or request-response handler in a new handler which also streams
  * request-response pairs to an access log sink:
  *
  *{{{
  * +-----------------------------------------------------------------+
  * |                                                                 |
  * |      +----------+        +-----------+        +----------+      |
  * |  +-->○ bcastReq ○------->○  handler  ○------->○ bcastRes ○--+   |
  * |  |   +-----○----+        +-----------+        +-----○----+  |   |
  * |  |         |                                        |       |   |
  * |  |         |                                        v       |   |
  * ○--+         |             +-----------+        +-----○----+  +-->○
  * |            |             | accessLog ○<-------○   zip    |      |
  * |            |             +-----------+        +-----○----+      |
  * |            |                                        ^           |
  * |            |                                        |           |
  * |            +----------------------------------------+           |
  * |                                                                 |
  * +-----------------------------------------------------------------+
  *}}}
  *
  *Example:
  *
  *{{{
  *import Accessus._
  *Http().bindAndHandle(
  *  route.withAccessLog(Sink.foreach { case (req, res) => ??? }),
  *  "0.0.0.0",
  *  8000
  *)
  *}}}
  *
  */
object Accessus {

  /**
    * A sink for request-response pairs.
    */
  type AccessLog[M] = Sink[(HttpRequest, HttpResponse), M]

  /**
    * A request-response handler as required by `Http().bindAndHandle`.
    */
  type Handler[M] = Flow[HttpRequest, HttpResponse, M]

  implicit class RouteOps(val route: Route) extends AnyVal {

    /**
      * Wraps the route in a new request-response handler which also streams request-response pairs
      * to the given access log sink.
      * @param accessLog sink for request-response pairs
      * @return handler to be used in `Http().bindAndHandle` wrapping the given route
      */
    def withAccessLog[M](accessLog: AccessLog[M])(implicit sytem: ActorSystem,
                                                  mat: ActorMaterializer): Handler[M] =
      Accessus.withAccessLog(accessLog)(Route.handlerFlow(route))
  }

  implicit class HandlerOps(val handler: Handler[Any]) extends AnyVal {

    /**
      * Wraps the request-response handler in a new one which also streams request-response pairs
      * to the given access log sink.
      * @param accessLog sink for request-response pairs
      * @return handler to be used in `Http().bindAndHandle` wrapping the given handler
      */
    def withAccessLog[M](accessLog: AccessLog[M]): Handler[M] =
      Accessus.withAccessLog(accessLog)(handler)
  }

  /**
    * Wraps the given request-response handler in a new one which also streams request-response
    * pairs to the access log given sink.
    * @param accessLog sink for request-response pairs
    * @param handler handler to be wrapped
    * @return handler to be used in `Http().bindAndHandle` wrapping the given handler
    */
  def withAccessLog[M](accessLog: AccessLog[M])(handler: Handler[Any]): Handler[M] =
    Flow.fromGraph(GraphDSL.create(accessLog) { implicit builder => accessLog =>
      import GraphDSL.Implicits._
      val bcastReq = builder.add(Broadcast[HttpRequest](2))
      val bcastRes = builder.add(Broadcast[HttpResponse](2))
      val zip      = builder.add(Zip[HttpRequest, HttpResponse])
      // format: OFF
      bcastReq ~> handler ~> bcastRes
                             bcastRes.out(1) ~> zip.in1
                                                zip.out ~> accessLog
      bcastReq            ~>                    zip.in0
      // format: ON
      FlowShape(bcastReq.in, bcastRes.out(0))
    })
}
