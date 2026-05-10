package com.personalblog.ragbackend.common.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 登录用户上下文。
 */
public final class UserContext {
    private static final TransmittableThreadLocal<LoginUser> CONTEXT = new TransmittableThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static LoginUser requireUser() {
        LoginUser user = CONTEXT.get();
        if (user == null) {
            throw new IllegalArgumentException("未获取到当前登录用户");
        }
        return user;
    }

    public static String getUserId() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUserId();
    }

    public static String getUsername() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUsername();
    }

    public static String getDisplayName() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getDisplayName();
    }

    public static String getRole() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getRole();
    }

    public static String getAvatar() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getAvatar();
    }

    public static boolean hasUser() {
        return CONTEXT.get() != null;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
