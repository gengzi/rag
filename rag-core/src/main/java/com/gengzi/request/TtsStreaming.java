package com.gengzi.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TtsStreaming {

    /**
     * 文本内容
     */
    private String text;
    /**
     * 音色ID，可选参数，默认为0  流示返回下无法调整
     */
    private Integer spk_id;

}
