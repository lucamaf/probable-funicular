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
import javax.inject.Inject;

import org.apache.camel.builder.RouteBuilder;
//import org.apache.camel.component.couchdb.CouchDbConstants;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanOperation;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
//import com.google.gson.JsonObject;

@ApplicationScoped
public class EdgeRoute extends RouteBuilder {

    @Inject
    ObjectMapper mapper;    
    
    private long long_time=System.currentTimeMillis();
    private int long_counter=0;
    private String time_key="time";
    private String long_time_key="long_time";
    private String counter_key="counter";
    private String long_counter_key="long_counter";
    private String total_key= "hour";
    private long time_value= 0;
    private int counter_value= 0;
    private int total_value=0;

    @ConfigProperty(name = "time.range") 
    long timerange;

        // when temperature out of range and humidity as well
        // store JMSTimestamp, temp and humidity values in infinispan under predefined keys 
        // if new timestamp is greater than current timestamp by a period reset timestamp and reset to one the counter
        // otherwise increment by one the key
        // when counter reaches limit send an alert to email address (if connection fails retry)
        // when the counter reached double the amount perform a geo query on redis and trigger the alert on telegram to deliver the good
    @Override
    public void configure() throws Exception {
        JacksonDataFormat jsonDataFormat = new JacksonDataFormat(Msg.class);
        jsonDataFormat.setObjectMapper(mapper);
        
        // get last saved time value
        from("timer://runOnce?repeatCount=1")
            .routeId("initTimer")
            .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.GETORDEFAULT)
            .setHeader(InfinispanConstants.KEY).constant(time_key)
            .setHeader(InfinispanConstants.DEFAULT_VALUE).constant(long_time)
            .to("infinispan:mycache")
            .process(exchange -> {
                time_value =  (long) exchange.getMessage().getBody();
            });

        // get the long running timer
        from("timer://runOnce?repeatCount=1")
            .routeId("initLongTimer")
            .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.GETORDEFAULT)
            .setHeader(InfinispanConstants.KEY).constant(long_time_key)
            .setHeader(InfinispanConstants.DEFAULT_VALUE).constant(long_time)
            .to("infinispan:mycache")
            .process(exchange -> {
                long_time =  (long) exchange.getMessage().getBody();
            });    

        // get the long running counter
        from("timer://runOnce?repeatCount=1")
            .routeId("initLongCounter")
            .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.GETORDEFAULT)
            .setHeader(InfinispanConstants.KEY).constant(long_counter_key)
            .setHeader(InfinispanConstants.DEFAULT_VALUE).constant(long_counter)
            .to("infinispan:mycache")
            .process(exchange -> {
                long_counter =  (Integer) exchange.getMessage().getBody();
            });

        // get last saved counter value
        from("timer://runOnce?repeatCount=1")
            .routeId("initCounter")
            .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.GETORDEFAULT)
            .setHeader(InfinispanConstants.KEY).constant(counter_key)
            .setHeader(InfinispanConstants.DEFAULT_VALUE).constant(long_counter)
            .to("infinispan:mycache")
            .process(exchange -> {
                counter_value =  (int) exchange.getMessage().getBody();
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
            
                if(t - time_value >  timerange){
                    long_counter=0;
                    long_time=t;
                }
                exchange.getMessage().setBody(t);
            })
            .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.PUT)
            .setHeader(InfinispanConstants.KEY).constant(time_key)
            .setHeader(InfinispanConstants.VALUE).body()
            .to("infinispan:mycache");

        // processing counter updates (long and short running)
        from("direct:counter")
        .routeId("infinispan-counter")
        .process(exchange -> {
            
            long_counter += 1;
            counter_value += 1;
            exchange.getMessage().setBody(long_counter);
        })
        .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.PUT)
        .setHeader(InfinispanConstants.KEY).constant(counter_key)
        .setHeader(InfinispanConstants.VALUE).body()
        .to("infinispan:mycache")
        .to("direct:check");

        // get counter value from infinispan
        from("direct:check")
        .routeId("checkLimit")
        .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.GETORDEFAULT)
            .setHeader(InfinispanConstants.KEY).constant(counter_key)
            .setHeader(InfinispanConstants.DEFAULT_VALUE).constant(counter_value)
            .to("infinispan:mycache")
        
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
            //.when().jsonpath("$[?(@.counter>{{counter.limit}})]")
            .when(simple("${body} > {{counter.maxlimit}}"))
                .log(" >>> Watchout: tier 2 !!! ")
                .log(" >>> Sending telegram notification")
                .setBody(simple("Alert!!! {{counter.maxlimit}} times exceeded range temperature or humidity"))
                .to("telegram:bots?authorizationToken=5423964667:AAGH2TmPnljRxOOw8joPVuLvewypFtmOIA4&chatId=-866936365")
            .when(simple("${body} > {{counter.limit}}"))
                .log(" >> Watchout: tier 1 !")
                .log(" >> Sending email notification")
                .setBody(simple("Alert! {{counter.limit}} times exceeded range temperature or humidity"))
                .to("smtp://{{smtp.host}}?from={{smtp.from}}&to={{smtp.to}}&subject={{smtp.subject}}&contentType=text/enriched");
                //.to("https://{{alert.endpoint}}");

    }
}