package com.personalblog.ragbackend.member.service.auth.strategy;

import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.mapper.MemberUserMapper;
import com.personalblog.ragbackend.member.service.MemberVerifyCodeService;
import com.personalblog.ragbackend.member.service.auth.MemberLoginStrategy;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * SmsLoginStrategy 登录策略实现类，负责处理对应方式的认证逻辑。
 */
@Service
public class SmsLoginStrategy implements MemberLoginStrategy {
    private final MemberUserMapper memberUserMapper;
    private final MemberVerifyCodeService verifyCodeService;

    public SmsLoginStrategy(MemberUserMapper memberUserMapper, MemberVerifyCodeService verifyCodeService) {
        this.memberUserMapper = memberUserMapper;
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

        MemberUser user = memberUserMapper.selectActiveByPhone(phone.trim());
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "未找到手机号对应用户");
        }
        return user;
    }
}

