package com.chengxinjian.auth;

import com.chengxinjian.common.AuditService;
import com.chengxinjian.common.BusinessException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Invoked only from the offline command, inside a database transaction. No HTTP route. */
public final class AdminInitialization {
    private AdminInitialization() {}
    public static void password(String value) {
        if (value==null || value.length()<12 || value.length()>64 || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw BusinessException.invalid("密码须为 12–64 字符且 UTF-8 不超过 72 字节");
    }
    public static String initialize(JdbcTemplate jdbc,PasswordEncoder passwords,Clock clock,String username,String displayName,String password,String reason) {
        password(password);
        if (username==null || !username.matches("[A-Za-z0-9][A-Za-z0-9_.@-]{2,63}") || displayName==null || displayName.isBlank() || displayName.length()>64
                || reason==null || reason.isBlank() || reason.length()>500) throw BusinessException.invalid("初始化字段无效");
        boolean done=jdbc.queryForObject("SELECT bootstrap_completed FROM admin_security_control WHERE id=1 FOR UPDATE",Boolean.class);
        if (done || jdbc.queryForObject("SELECT COUNT(*) FROM admin_account",Long.class)>0)
            throw new BusinessException(HttpStatus.CONFLICT,"ADMIN_ALREADY_INITIALIZED","已有管理员或初始化已完成，禁止重新初始化");
        String id=UUID.randomUUID().toString(),role=UUID.randomUUID().toString();var now=Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO admin_role(id,name,data_scope,version,created_at,updated_at) VALUES (?,'系统管理员','all',1,?,?)",role,now,now);
        jdbc.update("INSERT INTO admin_role_permission(role_id,permission_code) SELECT ?,code FROM permission",role);
        jdbc.update("INSERT INTO admin_account(id,username,display_name,password_hash,enabled,data_scope,version,created_at,updated_at) VALUES (?,?,?,?,TRUE,'all',1,?,?)",
            id,username,displayName,passwords.encode(password),now,now);
        jdbc.update("INSERT INTO admin_account_role(account_id,role_id) VALUES (?,?)",id,role);
        jdbc.update("UPDATE admin_security_control SET bootstrap_completed=TRUE WHERE id=1");
        new AuditService(jdbc,clock).record(id,"admin.bootstrap.secure",id,reason);
        return id;
    }
}
