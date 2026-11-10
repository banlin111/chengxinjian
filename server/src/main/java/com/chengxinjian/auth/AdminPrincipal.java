package com.chengxinjian.auth;

import java.util.List;

public record AdminPrincipal(String id, String username, String displayName, List<String> roleIds,
    List<String> permissions, String dataScope, String teacherId) {}
