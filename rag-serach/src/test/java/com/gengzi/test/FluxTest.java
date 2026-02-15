package com.gengzi.test;


import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.PriorityQueue;

public class FluxTest {


    public static void main(String[] args) throws InterruptedException {


        Flux.range(1, 100).delayElements(Duration.ofMillis(100)).map(item -> item * 2).take(10).subscribe(
                System.out::println
        );


        Thread.sleep(10000);


        PriorityQueue<List<Integer>> queue = new PriorityQueue<List<Integer>>((List<Integer> o1, List<Integer> o2) -> o1.stream().mapToInt(item -> item).sum() - o2.stream().mapToInt(item -> item).sum());

        List<Integer> poll = queue.poll();
        List<Integer> peek = queue.peek();
  
    }


}
