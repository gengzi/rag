package com.gengzi.test;


import reactor.core.publisher.Flux;

import java.time.Duration;

public class FluxTest {


    public static void main(String[] args) throws InterruptedException {


        Flux.range(1,100).delayElements(Duration.ofMillis(100)).map(item -> item * 2).take(10).subscribe(
                System.out::println
        );


        Thread.sleep(10000);


    }




}
