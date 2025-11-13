import reactor.core.publisher.Mono;

public class FluxTest {


    public static void main(String[] args) {


        Mono.fromCallable(() -> {
            System.out.println(11);
            return 11;
        }).subscribe();


        Mono.defer(() -> true ? Mono.just(1) : Mono.just(2)).subscribe();


    }


}
