package com.gengzi.service;

import com.gengzi.request.TtsReq;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

public interface TtsService {


    Flux<DataBuffer> getChatTTSByChatId(TtsReq request);

}
