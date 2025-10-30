package com.gengzi.service;

import com.gengzi.request.TtsReq;
import reactor.core.publisher.Flux;

public interface TtsService {


    Flux<byte[]> getChatTTSByChatId(TtsReq request);

}
