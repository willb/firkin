/*
 * Copyright (c) 2015 William C. Benton and Red Hat, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.c
 */

package com.freevariable.firkin

import akka.actor._

import colossus._
import colossus.IOSystem
import colossus.service._
import colossus.core.ServerRef
import colossus.protocols.http._
import HttpMethod._
import UrlParsing._

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._

import scala.util.{Try, Success, Failure}

object Firkin {
  lazy val DEBUG = sys.env.getOrElse("FIRKIN_DEBUG", "false").toLowerCase == "true"

  // use me for ultra-simple console 
  def basicStart = {
    implicit val io = IOSystem()
    start()
    io.actorSystem.actorOf(Props[KV])
  }

  def start(port: Int = 4091)(implicit io: IOSystem): ServerRef = {
    import io.actorSystem.dispatcher

    val cache = io.actorSystem.actorOf(Props[KV])

    Service.serve[Http]("firkin", port){ context =>
      context.handle{ connection =>
        import connection.callbackExecutor
        
        connection.become {
	  case req @ Options on _ => {
	    Callback.successful(req.respond(HttpCodes.OK, 
			"", 
			List(("Access-Control-Allow-Origin", "*"),
			     ("Access-Control-Allow-Methods", "GET, POST, OPTIONS"),
			     ("Access-Control-Allow-Headers", "Content-Type"),
			     ("Access-Control-Max-Age", "86400"))))
	  }

          case req @ Get on Root => Callback.successful(req.ok(""))
          
          case req @ Get on Root / "cache" => {
            val cmd = KV.LIST()
            cache ! cmd
            Callback.fromFuture(cmd.promise.future).map {
              case ls: List[String] => {
                val json = ("cachedKeys" -> ls)
                req.ok(compact(render(json)))
              }
            }
          }
          
          case req @ Post on Root / "cache" => {
            if (DEBUG) {
              Console.println(req.toString)
              Console.println(req.entity)
            }
            
            val host = req.head.singleHeader("host").getOrElse("localhost:4091")
            req.entity match {
              case Some(bytes) => {
                Try(parse(bytes.decodeString("UTF-8"))) match {
                  case Success(jv) => {
                    val cmd = KV.PUT(compact(render(jv)))
                    cache ! cmd
                    Callback.fromFuture(cmd.promise.future).map { 
                      case s: String => 
                        req.respond(HttpCodes.FOUND, "", List(("Location", s"http://$host/cache/$s"), ("X-Firkin-Hash", s)))
                    }
                  }
                  
                  case Failure(f) => {
                    Callback.successful(req.respond(HttpCodes.UNPROCESSABLE_ENTITY, s"cowardly refusing to store malformed JSON:  error was $f"))
                  }
                }
              }
              case None =>
                Callback.successful(req.respond(HttpCodes.BAD_REQUEST, "cowardly refusing to store empty data"))
            }
           }
          
          case req @ Get on Root / "cache" / hash => {
            val cmd = KV.GET(hash)
            cache ! cmd
            Callback.fromFuture(cmd.promise.future).map{
              case Some(v) => req.respond(HttpCodes.OK, v.toString, List(("Access-Control-Allow-Origin", "*")) )
              case None => req.notFound("")
            }
          }
          
          case req @ Get on Root / "tag" / tag => {
            val cmd = KV.RESOLVE_TAG(tag)
            val host = req.head.singleHeader("host").getOrElse("localhost:4091")
            
            cache ! cmd
            Callback.fromFuture(cmd.promise.future).map {
              case Some((hash, doc)) => req.respond(HttpCodes.OK, doc.toString, List(("X-Firkin-Hash", s"http://$host/cache/$hash"), ("Access-Control-Allow-Origin", "*")))
              case None => req.notFound("")
            }
          }
          
          case req @ Get on Root / "tag-value" / tag => {
            val cmd = KV.GET_TAG(tag)
            val host = req.head.singleHeader("host").getOrElse("localhost:4091")
            
            cache ! cmd
            Callback.fromFuture(cmd.promise.future).map {
              case Some(hash) => req.ok(hash)
              case None => req.notFound("")
            }
          }
          
          case req @ Post on Root / "tag" => {
            val host = req.head.singleHeader("host").getOrElse("localhost:4091")
            
            req.entity match {
              case Some(bytes) => {
                Try(parse(bytes.decodeString("UTF-8"))) match {
                  case Success(jv) => {
                    val org.json4s.JString(tag) = render(jv \ "tag")
                    val org.json4s.JString(hash) = render(jv \ "hash")
                    val cmd = KV.PUT_TAG(tag, hash)
                    
                    cache ! cmd
                    Callback.fromFuture(cmd.promise.future).map {
                      case Some(hash) => req.respond(HttpCodes.FOUND, "", List(("Location", s"http://$host/tag/$hash")))
                      case None => req.notFound("")
                    }
                  }
                  
                  case Failure(f) => {
                    Callback.successful(req.respond(HttpCodes.UNPROCESSABLE_ENTITY, s"can't store tag from malformed JSON:  error was $f"))
                  }
                }
              }
              case None =>
                Callback.successful(req.respond(HttpCodes.BAD_REQUEST, "cowardly refusing to store empty data"))
            }
          }
          
          case req => {
            Console.println(s"unrecognized:  $req")
            Callback.successful(req.notFound("whoops"))
          }
        }
      }
    }
  }
  
  def main(args: Array[String]) {
    implicit val io = IOSystem()
    io.actorSystem.actorOf(Props[KV])
    
    val port = args match {
      case Array("--port", port) => Try(port.toInt).toOption.getOrElse(4091)
      case Array() => 4091
      case _ => { Console.println("usage:  Firkin [--port PORT]") ; -1 }
    }
    
    if (port < 0) System.exit(1)
    
    start(port)
  }
}
