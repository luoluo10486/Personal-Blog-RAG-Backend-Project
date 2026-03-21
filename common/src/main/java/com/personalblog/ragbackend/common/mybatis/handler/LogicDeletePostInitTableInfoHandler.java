package com.personalblog.ragbackend.common.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.PostInitTableInfoHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import org.apache.ibatis.session.Configuration;

import java.lang.reflect.Field;

/**
 * LogicDeletePostInitTableInfoHandler 处理器类，用于统一处理框架扩展逻辑。
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
            throw new IllegalStateException("运行时关闭 mybatis-plus 逻辑删除失败", e);
        }
    }
}

