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
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.server.Route
import akka.stream.{ FlowShape, Materializer }
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Sink, Unzip, Zip }

/**
  * Provides ways to wrap a route or request-response handler in a new handler which also streams
  * pairs of enriched request and response to an access log sink:
  *
  *{{{
  *+------------------------------------------------------------------------------------+
  *|                                                                                    |
  *|        +-----------+      +-----------+      +-----------+      +-----------+      |
  *|   +--->○ enrichReq ○----->○   unzip   ○----->○  handler  ○----->○  bcastRes ○--+   |
  *|   |    +-----------+      +-----○-----+      +-----------+      +-----------+  |   |
  *|   |                             |                                     |        |   |
  *○---+                             |                                     |        +-->○
  *|                                 v                                     |            |
  *|        +-----------+      +-----○-----+                               |            |
  *|        | accessLog ○<-----○    zip    ○<------------------------------+            |
  *|        +-----------+      +-----------+                                            |
  *|                                                                                    |
  *+------------------------------------------------------------------------------------+
  *}}}
  *
  *Example:
  *
  *{{{
  *import Accessus._
  *Http().bindAndHandle(
  *  route.withAccessLog(Sink.foreach { case ((req, t), res) => ??? }),
  *  "0.0.0.0",
  *  8000
  *)
  *}}}
  *
  */
object Accessus {

  /**
    * A sink for pairs of enriched request and response.
    */
  type AccessLog[A, M] = Sink[((HttpRequest, A), HttpResponse), M]

  /**
    * A request-response handler as required by `Http().bindAndHandle`.
    */
  type Handler[M] = Flow[HttpRequest, HttpResponse, M]

  final implicit class RouteOps(val route: Route) extends AnyVal {

    /**
      * Wraps the route in a new request-response handler which also streams pairs of request
      * enriched with a timestamp and response to the given access log sink.
      * @param accessLog sink for pairs of enriched request and response
      * @return handler to be used in `Http().bindAndHandle` wrapping the given route
      */
    def withAccessLog[M](accessLog: AccessLog[Long, M])(implicit sytem: ActorSystem,
                                                        mat: Materializer): Handler[M] =
      Accessus.withAccessLog(() => System.nanoTime())(accessLog, Route.handlerFlow(route))

    /**
      * Wraps the route in a new request-response handler which also streams sink for pairs of
      * enriched request and response to the given access log sink.
      * @param f enrich the request, e.g. with a timestamp
      * @param accessLog sink for pairs of enriched request and response
      * @return handler to be used in `Http().bindAndHandle` wrapping the given route
      */
    def withAccessLog[A, M](
        f: () => A
    )(accessLog: AccessLog[A, M])(implicit sytem: ActorSystem, mat: Materializer): Handler[M] =
      Accessus.withAccessLog(f)(accessLog, Route.handlerFlow(route))
  }

  final implicit class HandlerOps(val handler: Handler[Any]) extends AnyVal {

    /**
      * Wraps the request-response handler in a new one which also streams sink for pairs of
      * request enriched with a timestamp and response to the given access log sink.
      * @param accessLog sink for pairs of enriched request and response
      * @return handler to be used in `Http().bindAndHandle` wrapping the given handler
      */
    def withAccessLog[M](accessLog: AccessLog[Long, M]): Handler[M] =
      Accessus.withAccessLog(() => System.nanoTime())(accessLog, handler)

    /**
      * Wraps the request-response handler in a new one which also streams sink for pairs of
      * enriched request and response to the given access log sink.
      * @param f enrich the request, e.g. with a timestamp
      * @param accessLog sink for pairs of enriched request and response
      * @return handler to be used in `Http().bindAndHandle` wrapping the given handler
      */
    def withAccessLog[A, M](f: () => A)(accessLog: AccessLog[A, M]): Handler[M] =
      Accessus.withAccessLog(f)(accessLog, handler)
  }

  /**
    * Wraps the given request-response handler in a new one which also streams sink for pairs of
    * enriched request and response pairs to the access log given sink.
    * @param accessLog sink for pairs of enriched request and response
    * @param f enrich the request, e.g. with a timestamp
    * @param handler handler to be wrapped
    * @return handler to be used in `Http().bindAndHandle` wrapping the given handler
    */
  def withAccessLog[A, M](f: () => A)(accessLog: AccessLog[A, M],
                                      handler: Handler[Any]): Handler[M] =
    Flow.fromGraph(GraphDSL.create(accessLog) { implicit builder => accessLog =>
      import GraphDSL.Implicits._
      val enrichReq = builder.add(Flow[HttpRequest].map(req => (req, (req, f()))))
      val unzip     = builder.add(Unzip[HttpRequest, (HttpRequest, A)]())
      val bcastRes  = builder.add(Broadcast[HttpResponse](2))
      val zip       = builder.add(Zip[(HttpRequest, A), HttpResponse])
      // format: OFF
      enrichReq ~> unzip.in
      unzip.out0 ~> handler ~> bcastRes
      bcastRes.out(1) ~> zip.in1
      unzip.out1            ~>                    zip.in0
      zip.out ~> accessLog
      // format: ON
      FlowShape(enrichReq.in, bcastRes.out(0))
    })
}
