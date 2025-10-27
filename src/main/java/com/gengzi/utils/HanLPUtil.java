package com.gengzi.utils;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.NShort.NShortSegment;
import com.hankcs.hanlp.seg.Segment;
import com.hankcs.hanlp.seg.common.Term;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class HanLPUtil {

    /**
     * 中文分词（默认模式）
     */
    public static List<String> segment(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        // 调用HanLP的分词方法
        List<Term> terms = HanLP.segment(text);
        // 提取分词结果中的词语
        return terms.stream()
                .map(term -> term.word)
                .collect(Collectors.toList());
    }

    /**
     * 中文分词 全量分词
     */
    public static List<String>  nShortSegment(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        // 调用HanLP的分词方法
        Segment nShortSegment = new NShortSegment().enableCustomDictionary(false);
        List<Term> terms = nShortSegment.seg(text);
        // 提取分词结果中的词语
        return terms.stream()
                .map(term -> term.word)
                .collect(Collectors.toList());
    }


    /**
     * 中文分词+词性标注
     */
    public static List<String> segmentWithNature(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<Term> terms = HanLP.segment(text);
        // 格式：词语/词性（如"HanLP/nz"）
        return terms.stream()
                .map(term -> term.word + "/" + term.nature)
                .collect(Collectors.toList());
    }

    /**
     * 提取关键词
     */
    public static List<String> extractKeywords(String text, int topN) {
        if (text == null || text.isEmpty() || topN <= 0) {
            return List.of();
        }
        return HanLP.extractKeyword(text, topN);
    }
}
