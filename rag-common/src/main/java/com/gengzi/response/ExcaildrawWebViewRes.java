package com.gengzi.response;


import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ExcaildrawWebViewRes {

    private String content;

    private String nodeName;

}
