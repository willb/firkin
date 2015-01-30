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

class Client(endpointHost: String, port: Int) {
  import dispatch._
  import scala.concurrent.ExecutionContext.Implicits.global
  
  lazy val server = host(endpointHost, port)
  
  def put(data: String): String = {
    val endpoint = (server / "cache").POST
    val request = endpoint.setContentType("application/json", "UTF-8") << data
    val response = Http(request > (x => x))
    response().getHeader("Location")
  }
  
  def get(hash: String): Option[String] = {
    val endpoint = (server / "cache" / hash).GET
    val response = Http(endpoint OK as.String).option
    response()
  }
  
  def getOrElse(hash: String, default: String): String = {
    get(hash).getOrElse(default)
  }
}