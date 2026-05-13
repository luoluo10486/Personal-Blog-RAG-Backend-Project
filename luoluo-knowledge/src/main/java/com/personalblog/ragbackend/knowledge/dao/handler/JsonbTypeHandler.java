package com.personalblog.ragbackend.knowledge.dao.handler;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

public class JsonbTypeHandler extends JacksonTypeHandler {
    public JsonbTypeHandler() {
        super(Object.class);
    }
}
