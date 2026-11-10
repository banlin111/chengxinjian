package com.chengxinjian.user;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalAgreementBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public LocalAgreementBootstrap(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public void run(ApplicationArguments args) {
        for (String type:new String[]{"privacy","user"}) {
            try {
                jdbc.update("INSERT INTO agreement_version(agreement_type,version,title,content,effective_at,published,test_only) VALUES (?,?,?,?,?,TRUE,TRUE)",
                    type,"local-test-2026-11-09","【仅本地测试，未经甲方批准】"+(type.equals("privacy") ? "隐私政策" : "用户协议"),
                    "仅供开发联调，不是对外协议，不得用于正式注册或发布。",Timestamp.from(Instant.parse("2026-11-09T00:00:00Z")));
            } catch (DuplicateKeyException ignored) { /* Never replace approved content or existing versions. */ }
        }
    }
}
