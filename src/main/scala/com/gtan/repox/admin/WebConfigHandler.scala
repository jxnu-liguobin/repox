package com.gtan.repox.admin

import java.nio.ByteBuffer

import com.google.common.base.Charsets
import com.gtan.repox.{Immediate404Rule, Repo}
import com.gtan.repox.config.Config
import io.undertow.Handlers
import io.undertow.server.handlers.resource.ClassPathResourceManager
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, Methods, StatusCodes}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

class WebConfigHandler extends HttpHandler {

  import com.gtan.repox.admin.WebConfigHandler._

  /**
   * intentional avoid reading request body, all data enclosed in querystring
   * @param httpServerExchange core data/state carrier
   */
  override def handleRequest(httpServerExchange: HttpServerExchange) = {
    implicit val exchange = httpServerExchange
    val (method, uriUnprefixed) = (
      httpServerExchange.getRequestMethod,
      httpServerExchange.getRequestURI.drop("/admin/".length))
    restHandlers.map(_.route).reduce(_ orElse _).apply(method -> uriUnprefixed)
  }
}


object WebConfigHandler {
  val restHandlers: Seq[RestHandler] = List(
    StaticAssetHandler,
    UpstreamsHandler,
    ProxiesHandler,
    Immediate404RulesHandler,
    ExpireRulesHandler,
    ParametersHandler
  )

  def setConfigAndRespond(exchange: HttpServerExchange, newConfig: Config): Unit = {
    Config.set(newConfig).onComplete {
      case Success(_) =>
        respondEmptyOK(exchange)
      case Failure(t) =>
        respondError(exchange, t)
    }
  }

  def isStaticRequest(target: String) = Set(".html", ".css", ".js", ".ico", ".ttf", ".map", "woff").exists(target.endsWith)

  def respondJson(exchange: HttpServerExchange, data: java.util.Map[String, _ <: Any]): Unit = {
    exchange.setResponseCode(StatusCodes.OK)
    val respondHeaders = exchange.getResponseHeaders
    respondHeaders.put(Headers.CONTENT_TYPE, "application/json")
    val json = Jsonable.gson.toJson(data)
    exchange.getResponseChannel.writeFinal(ByteBuffer.wrap(json.getBytes(Charsets.UTF_8)))
    exchange.endExchange()
  }

  def respondError(exchange: HttpServerExchange, t: Throwable): Unit = {
    exchange.setResponseCode(StatusCodes.INTERNAL_SERVER_ERROR)
    exchange.getResponseChannel
    exchange.endExchange()
  }

  def respondEmptyOK(exchange: HttpServerExchange): Unit = {
    exchange.setResponseCode(StatusCodes.OK)
    exchange.getResponseChannel
    exchange.endExchange()
  }

}