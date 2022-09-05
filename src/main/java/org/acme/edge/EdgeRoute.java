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
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanOperation;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.google.gson.JsonObject;

@ApplicationScoped
public class EdgeRoute extends RouteBuilder {

    JacksonDataFormat jsonDataFormat = new JacksonDataFormat(Msg.class);
    private long old_time=System.currentTimeMillis();
    private int old_counter=0;
    private String time_key="time";
    private String counter_key="counter";
    private String total_key= "hour";
    private String time_value="";
    private String counter_value="";
    private int total_value=0;

    @ConfigProperty(name = "time.range") 
    long timerange;

        // when temperature out of range and humidity as well
        // store JMSTimestamp, temp and humidity values in infinispan under predefined keys 
        // if new timestamp is greater than current timestamp by a period reset timestamp and reset to one the counter
        // otherwise increment one the key
        // when counter reaches limit send an alert to email address (if connection fails retry)
        // when the counter reached double the amount perform a geo query on redis and trigger the alert on telegram to deliver the good
    @Override
    public void configure() throws Exception {
        // get last saved time value
        from("timer://runOnce?repeatCount=1")
            .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.GETORDEFAULT)
            .setHeader(InfinispanConstants.KEY).constant(time_key)
            .setHeader(InfinispanConstants.DEFAULT_VALUE).constant(old_time)
            .to("infinispan://mycache?hosts=infinispan&username=admin&password=password")
            .process(exchange -> {
                time_value =  (String) exchange.getMessage().getBody();
            });

        // get the long running timer
        from("timer://runOnce?repeatCount=1")
            .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.GETORDEFAULT)
            .setHeader(InfinispanConstants.KEY).constant("old_time")
            .setHeader(InfinispanConstants.DEFAULT_VALUE).constant(old_time)
            .to("infinispan://mycache?hosts=infinispan&username=admin&password=password")
            .process(exchange -> {
                old_time =  (Long) exchange.getMessage().getBody();
            });    

        // get the long running counter
        from("timer://runOnce?repeatCount=1")
            .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.GETORDEFAULT)
            .setHeader(InfinispanConstants.KEY).constant("old_counter")
            .setHeader(InfinispanConstants.DEFAULT_VALUE).constant(old_counter)
            .to("infinispan://mycache?hosts=infinispan&username=admin&password=password")
            .process(exchange -> {
                old_counter =  (Integer) exchange.getMessage().getBody();
            });

        // get last saved counter value
        from("timer://runOnce?repeatCount=1")
            .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.GETORDEFAULT)
            .setHeader(InfinispanConstants.KEY).constant(counter_key)
            .setHeader(InfinispanConstants.DEFAULT_VALUE).constant(old_counter)
            .to("infinispan://mycache?hosts=infinispan&username=admin&password=password")
            .process(exchange -> {
                counter_value =  (String) exchange.getMessage().getBody();
            });

        // check for temperature or humidity limits
        from("jms:{{jms.destinationType}}:{{jms.destinationName}}")
            .routeId("amq2filter")
            .log("message: ${body}")
            .unmarshal(jsonDataFormat)
            .choice()
                .when(simple("${body.temperature} > {{temp.limit}} || ${body.humidity} > {{humid.limit}}"))
                    .to("direct:alert");

        // process the event to update time and counter at the same time
        //TODO remove multi, save payload in exchange property
        from("direct:alert")
            .routeId("filter2multicast")
            .multicast()
                .to("direct:time","direct:counter");

        // processing time updates
        from("direct:time")
            .routeId("infinispan-time")
            .process(exchange -> {
                long t = (long) exchange.getMessage().getHeader("JMSTimestamp");
            
                if(t - Long.parseLong(time_value) >  timerange){
                    old_counter=0;
                    old_time=t;
                }
                exchange.getMessage().setBody(t);
            })
            .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.PUT)
            .setHeader(InfinispanConstants.KEY).constant(time_key)
            .setHeader(InfinispanConstants.VALUE).body()
            .to("infinispan://mycache?hosts=infinispan&username=admin&password=password");

        // processing counter updates (long and short running)
        from("direct:counter")
        .routeId("infinispan-counter")
        .process(exchange -> {
            
            old_counter += 1;
            counter_value += 1;
            exchange.getMessage().setBody(old_counter);
        })
        .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.PUT)
        .setHeader(InfinispanConstants.KEY).constant(time_key)
        .setHeader(InfinispanConstants.VALUE).body()
        .to("infinispan://mycache?hosts=infinispan&username=admin&password=password")
        .to("direct:check");

        // get counter value from infinispan
        from("direct:check")
        .routeId("checkLimit")
        .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.GETORDEFAULT)
            .setHeader(InfinispanConstants.KEY).constant("old_counter")
            .setHeader(InfinispanConstants.DEFAULT_VALUE).constant(old_counter)
            .to("infinispan://mycache?hosts=infinispan&username=admin&password=password")
        
        //.marshal().json()
        //.process(exchange -> {
            //JsonObject json = (JsonObject) JsonParser.parseString(exchange.getMessage().getBody().toString());
            //exchange.getMessage().setBody(json);
            //exchange.getMessage().setHeader("counter", json.get("counter"));
            //String s = exchange.getMessage().getBody().toString();
            //s = s.replaceAll("\'", "\"");
            //exchange.getMessage().setBody(s);
        //})

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