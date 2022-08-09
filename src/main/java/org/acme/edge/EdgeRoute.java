/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.acme.edge;

import javax.enterprise.context.ApplicationScoped;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.couchdb.CouchDbConstants;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.google.gson.JsonObject;

@ApplicationScoped
public class EdgeRoute extends RouteBuilder {

    JacksonDataFormat jsonDataFormat = new JacksonDataFormat(Msg.class);
    private long old_time=System.currentTimeMillis();
    private int old_counter=0;
    private String time_key="";
    private String counter_key="";
    private String time_rev="";
    private String counter_rev="";

    @ConfigProperty(name = "time.range") 
    long timerange;

    @Override
    public void configure() throws Exception {
        from("jms:{{jms.destinationType}}:{{jms.destinationName}}")
            .routeId("amq2filter")
            .log("message: ${body}")
            .unmarshal(jsonDataFormat)
        // when temperature out of range
        // store JMSTimestamp in redis under a key 
        // if new timestamp is greater than current timestamp by a time reset timestamp and reset to one a key
        // otherwise increment one the key
        // when counter reaches 5 send an alert to web trap
            .choice()
                .when(simple("${body.temperature} > {{temp.limit}}"))
                    .to("direct:alert");

        from("direct:alert")
            .routeId("filter2multicast")
            .multicast()
                .to("direct:time","direct:counter");

        from("direct:time")
            .routeId("multi2couch-time")
            .process(exchange -> {
            long t = (long) exchange.getMessage().getHeader("JMSTimestamp");
            if(!time_key.isEmpty()){
                exchange.getMessage().setHeader(CouchDbConstants.HEADER_DOC_ID, time_key);
                exchange.getMessage().setHeader(CouchDbConstants.HEADER_DOC_REV, time_rev);
            }
            if(t-old_time >  timerange){
                if(time_key.isEmpty()){
                    exchange.getMessage().setBody("{'time':"+t+"}", JsonObject.class);
                }else{
                    exchange.getMessage().setBody("{'time':"+t+",'_id':'"+time_key+"','_rev':'"+time_rev+"'}", JsonObject.class);
                }
                old_counter=0;
                old_time=t;
            }else{
                if(time_key.isEmpty()){
                    exchange.getMessage().setBody("{'time':"+t+"}", JsonObject.class);
                }else{
                    exchange.getMessage().setBody("{'time':"+t+",'_id':'"+time_key+"','_rev':'"+time_rev+"'}", JsonObject.class);
                }
            }
            
        })
        .to("couchdb:http://{{camel.couchdb}}/database?createDatabase=true&username=admin&password=password")
        .process(exchange -> {
            time_key = (String) exchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_ID);
            time_rev = (String) exchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_REV);
        });

        from("direct:counter")
        .routeId("multi2couch-counter")
        .process(exchange -> {
            if(!counter_key.isEmpty()){
                exchange.getMessage().setHeader(CouchDbConstants.HEADER_DOC_ID, counter_key);
                exchange.getMessage().setHeader(CouchDbConstants.HEADER_DOC_REV, counter_rev);
            }
            if(old_counter==0){
                if(counter_key.isEmpty()){
                    exchange.getMessage().setBody("{'counter':1}", JsonObject.class);
                }else{
                    exchange.getMessage().setBody("{'counter':1,'_id':'"+counter_key+"','_rev':'"+counter_rev+"'}", JsonObject.class);
                }
                old_counter=1;    
            }else{
                old_counter += 1;
                if(counter_key.isEmpty()){
                    exchange.getMessage().setBody("{'counter':"+old_counter+"}", JsonObject.class);
                }else{
                    exchange.getMessage().setBody("{'counter':"+old_counter+",'_id':'"+counter_key+"','_rev':'"+counter_rev+"'}", JsonObject.class);
                }
            }
        })
        .to("couchdb:http://{{camel.couchdb}}/database?createDatabase=true&username=admin&password=password")
        .process(exchange -> {
            counter_key = (String) exchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_ID);
            counter_rev = (String) exchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_REV);
        })
        .to("direct:check");

        from("direct:check")
        .routeId("checkLimit")
        .process(exchange -> {
            exchange.getMessage().setHeader(CouchDbConstants.HEADER_METHOD, constant("get"));
            exchange.getMessage().setHeader(CouchDbConstants.HEADER_DOC_ID, constant(counter_key));

        })
        .to("couchdb:http://{{camel.couchdb}}/database?createDatabase=true&username=admin&password=password")
        //.marshal().json()
        .process(exchange -> {
            //JsonObject json = (JsonObject) JsonParser.parseString(exchange.getMessage().getBody().toString());
            //exchange.getMessage().setBody(json);
            //exchange.getMessage().setHeader("counter", json.get("counter"));
            String s = exchange.getMessage().getBody().toString();
            s = s.replaceAll("\'", "\"");
            exchange.getMessage().setBody(s);
        })

        //.marshal().json()
        .choice()
            //.when(PredicateBuilder.isGreaterThan(
            //    ExpressionBuilder.languageExpression("jsonpath", "$.counter"), 
            //    ExpressionBuilder.constantExpression("0")))
            //    .log("watchout");
            .when().jsonpath("$[?(@.counter>{{counter.limit}})]")
                .log("watchout")
                .setBody(simple("Alert! {{counter.limit}} times exceeded range temperature"))
                .to("https://{{alert.endpoint}}");
        

    }
}