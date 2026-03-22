package com.personalblog.ragbackend.common.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalblog.ragbackend.common.auth.domain.VerifyCodeRecord;

/**
 * 验证码记录映射层，负责 `sys_verify_code_record` 表的基础持久化。
 */
public interface VerifyCodeRecordMapper extends BaseMapper<VerifyCodeRecord> {
}
