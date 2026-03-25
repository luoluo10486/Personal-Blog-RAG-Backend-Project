package com.personalblog.ragbackend.member.service.auth.strategy;

import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.service.MemberUserService;
import com.personalblog.ragbackend.member.service.MemberVerifyCodeService;
import com.personalblog.ragbackend.member.service.auth.MemberLoginStrategy;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * 短信登录策略，处理手机号和短信验证码的认证流程。
 */
@Service
public class SmsLoginStrategy implements MemberLoginStrategy {
    private final MemberUserService memberUserService;
    private final MemberVerifyCodeService verifyCodeService;

    public SmsLoginStrategy(MemberUserService memberUserService, MemberVerifyCodeService verifyCodeService) {
        this.memberUserService = memberUserService;
        this.verifyCodeService = verifyCodeService;
    }

    @Override
    public String grantType() {
        return "sms";
    }

    @Override
    public MemberUser authenticate(MemberLoginRequest request) {
        String phone = request.getPhone();
        String smsCode = request.getSmsCode();
        if (phone == null || phone.isBlank() || smsCode == null || smsCode.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "phone 和 smsCode 不能为空");
        }

        boolean verified = verifyCodeService.verifyAndConsume("sms", phone.trim(), smsCode.trim());
        if (!verified) {
            throw new ResponseStatusException(UNAUTHORIZED, "短信验证码无效");
        }

        MemberUser user = memberUserService.findActiveByPhone(phone.trim());
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "未找到手机号对应用户");
        }
        return user;
    }
}
