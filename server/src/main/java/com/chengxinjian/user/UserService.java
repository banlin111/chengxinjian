package com.chengxinjian.user;

import com.chengxinjian.common.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.chengxinjian.user.UserModels.*;

@Service
public class UserService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public UserService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public void requireActive(String id, boolean lock) {
        var rows=jdbc.queryForList("SELECT status FROM app_user WHERE id=?"+(lock ? " FOR UPDATE" : ""),String.class,id);
        if (rows.isEmpty()) { throw BusinessException.unauthenticated(); }
        if ("banned".equals(rows.getFirst())) { throw new BusinessException(HttpStatus.FORBIDDEN,"ACCOUNT_BANNED","账号已被封禁"); }
        if (!"active".equals(rows.getFirst())) { throw new BusinessException(HttpStatus.FORBIDDEN,"ACCOUNT_UNAVAILABLE","账号当前不可用"); }
    }
    public User profile(String id) {
        requireActive(id,false);
        return jdbc.queryForObject("SELECT nickname,status FROM app_user WHERE id=?",(rs,n)->new User(id,rs.getString("nickname"),null,null,false,
            roles(id),rs.getString("status"),new Membership(false,null,null,null,null,false),settings(id).theme()),id);
    }
    private List<String> roles(String id) {
        long linked=jdbc.queryForObject("SELECT COUNT(*) FROM content_teacher WHERE user_id=? AND status<>'archived'",Long.class,id);
        return linked>0 ? List.of("user","teacher") : List.of("user");
    }
    public Settings settings(String id) {
        return jdbc.queryForObject("SELECT settings_json,version FROM user_settings WHERE user_id=?",(rs,n)->decode(rs.getString(1)).withVersion(rs.getInt(2)),id);
    }
    @Transactional
    public Settings updateSettings(String id, Settings input) {
        requireActive(id,true);
        if (!List.of(0.5,0.75,1.0,1.25,1.5,2.0).contains(input.defaultSpeed())) { throw BusinessException.invalid("不支持此播放倍速"); }
        if (input.practiceReminderEnabled() && input.practiceReminderTime()==null) { throw BusinessException.invalid("请设置练习提醒时间"); }
        // End-bell choices require the media/configuration pipeline; do not accept unapproved asset references.
        if (input.endBellAssetId()!=null) { throw BusinessException.invalid("当前没有可选的结束提示音资产"); }
        var current=settings(id);
        if (input.version()!=null && !input.version().equals(current.version())) {
            throw new BusinessException(HttpStatus.CONFLICT,"VERSION_CONFLICT","设置已更新，请重新读取");
        }
        var result=input.withVersion(current.version()+1);
        jdbc.update("UPDATE user_settings SET settings_json=?,version=? WHERE user_id=? AND version=?",encode(result),result.version(),id,current.version());
        return result;
    }
    String encode(Settings value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Settings serialization failed"); }
    }
    private Settings decode(String value) {
        try { return json.readValue(value,Settings.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Settings decoding failed"); }
    }
}
