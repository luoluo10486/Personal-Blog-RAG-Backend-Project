package com.personalblog.ragbackend.member.service.code;

import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 验证码发送路由服务，按 grantType 分发到具体发送策略。
 */
@Service
public class MemberSendCodeService {
    private final Map<String, MemberSendCodeStrategy> strategyMap;

    public MemberSendCodeService(List<MemberSendCodeStrategy> strategies) {
        this.strategyMap = strategies.stream().collect(Collectors.toMap(
                strategy -> strategy.grantType().toLowerCase(Locale.ROOT),
                Function.identity()
        ));
    }

    public MemberSendVerifyCodeResponse send(MemberSendVerifyCodeRequest request) {
        String grantType = normalizeGrantType(request.getGrantType());
        MemberSendCodeStrategy strategy = strategyMap.get(grantType);
        if (strategy == null) {
            throw new ResponseStatusException(BAD_REQUEST, "不支持的 grantType：" + grantType);
        }
        return strategy.send(request);
    }

    private String normalizeGrantType(String grantType) {
        if (grantType == null || grantType.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "grantType 不能为空");
        }
        return grantType.trim().toLowerCase(Locale.ROOT);
    }
}
