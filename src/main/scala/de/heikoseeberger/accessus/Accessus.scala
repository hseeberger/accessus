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
import akka.event.Logging.LogLevel
import akka.event.{ Logging, LoggingAdapter }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.FlowShape
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Sink, Zip }
import scala.concurrent.Future

object Accessus {

  type AccessLog[M] = Sink[(HttpRequest, HttpResponse), M]

  type Handler[M] = Flow[HttpRequest, HttpResponse, M]

  def withLoggingAccessLog(
      toMessage: (HttpRequest, HttpResponse) => String,
      log: LoggingAdapter,
      level: LogLevel = Logging.InfoLevel
  )(handler: Handler[Any]): Handler[Future[Done]] =
    withAccessLog(Sink.foreach { case (req, res) => log.log(level, toMessage(req, res)) })(handler)

  def withAccessLog[M](accessLog: AccessLog[M])(handler: Handler[Any]): Handler[M] =
    Flow.fromGraph(GraphDSL.create(accessLog) { implicit builder => al =>
      import GraphDSL.Implicits._
      val bcastReq = builder.add(Broadcast[HttpRequest](2))
      val bcastRes = builder.add(Broadcast[HttpResponse](2))
      val zip      = builder.add(Zip[HttpRequest, HttpResponse])
      // format: OFF
      bcastReq            ~>                    zip.in0
      bcastReq ~> handler ~> bcastRes
                             bcastRes.out(1) ~> zip.in1
                                                zip.out ~> al
      // format: ON
      FlowShape(bcastReq.in, bcastRes.out(0))
    })
}
