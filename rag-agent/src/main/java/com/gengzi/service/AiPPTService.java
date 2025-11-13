package com.gengzi.service;

import com.gengzi.request.AiPPTGenerateReq;

public interface AiPPTService {


    void generatePPT(AiPPTGenerateReq req) throws Exception;

    void pptMotherboardParse(String motherboardName) throws Exception;

}
