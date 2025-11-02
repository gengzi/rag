package com.gengzi.request;


import lombok.Data;

@Data
public class RelevancyJson {

    private String chunkId;
    private String score;
    private String reason;
}
