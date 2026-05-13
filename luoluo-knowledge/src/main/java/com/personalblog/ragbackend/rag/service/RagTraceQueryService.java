package com.personalblog.ragbackend.rag.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.rag.controller.request.RagTraceRunPageRequest;
import com.personalblog.ragbackend.rag.controller.vo.RagTraceDetailVO;
import com.personalblog.ragbackend.rag.controller.vo.RagTraceNodeVO;
import com.personalblog.ragbackend.rag.controller.vo.RagTraceRunVO;

import java.util.List;

public interface RagTraceQueryService {
    IPage<RagTraceRunVO> pageRuns(RagTraceRunPageRequest request);

    RagTraceDetailVO detail(String traceId);

    List<RagTraceNodeVO> listNodes(String traceId);
}
