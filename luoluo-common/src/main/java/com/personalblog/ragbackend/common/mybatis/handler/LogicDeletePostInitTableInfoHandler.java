package com.personalblog.ragbackend.common.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.PostInitTableInfoHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import org.apache.ibatis.session.Configuration;

import java.lang.reflect.Field;

/**
 * 逻辑删除后置处理器，用于按配置开关控制 MP 的逻辑删除能力。
 */
public class LogicDeletePostInitTableInfoHandler implements PostInitTableInfoHandler {
    private static final String WITH_LOGIC_DELETE_FIELD = "withLogicDelete";
    private static final Field TABLE_INFO_WITH_LOGIC_DELETE;

    static {
        try {
            TABLE_INFO_WITH_LOGIC_DELETE = TableInfo.class.getDeclaredField(WITH_LOGIC_DELETE_FIELD);
            TABLE_INFO_WITH_LOGIC_DELETE.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final boolean enableLogicDelete;

    public LogicDeletePostInitTableInfoHandler(boolean enableLogicDelete) {
        this.enableLogicDelete = enableLogicDelete;
    }

    @Override
    public void postTableInfo(TableInfo tableInfo, Configuration configuration) {
        if (enableLogicDelete) {
            return;
        }
        try {
            TABLE_INFO_WITH_LOGIC_DELETE.set(tableInfo, false);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("运行时关闭 MyBatis-Plus 逻辑删除失败", e);
        }
    }
}
