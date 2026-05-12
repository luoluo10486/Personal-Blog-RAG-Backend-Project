package com.personalblog.ragbackend.member.dto.user;

public class CurrentUserVO {
    private String userId;
    private String username;
    private String role;
    private String avatar;

    public CurrentUserVO() {
    }

    public CurrentUserVO(String userId, String username, String role, String avatar) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.avatar = avatar;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }
}
